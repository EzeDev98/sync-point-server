package com.sp.sync_point.service.impl;

import com.sp.sync_point.config.PasswordResetProperties;
import com.sp.sync_point.dto.CompletePasswordResetRequest;
import com.sp.sync_point.dto.PasswordResetRequestDto;
import com.sp.sync_point.dto.VerifyOtpRequest;
import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.exception.MaxAttemptsExceededException;
import com.sp.sync_point.exception.OtpExpiredException;
import com.sp.sync_point.exception.OtpInvalidException;
import com.sp.sync_point.exception.OtpResendCooldownException;
import com.sp.sync_point.exception.ResetTokenInvalidException;
import com.sp.sync_point.exception.UserNotFoundException;
import com.sp.sync_point.model.PasswordResetOtp;
import com.sp.sync_point.model.PasswordResetToken;
import com.sp.sync_point.model.User;
import com.sp.sync_point.repository.PasswordResetOtpRepository;
import com.sp.sync_point.repository.PasswordResetTokenRepository;
import com.sp.sync_point.repository.UserRepository;
import com.sp.sync_point.response.PasswordResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.OtpDeliveryService;
import com.sp.sync_point.service.PasswordResetService;
import com.sp.sync_point.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import static com.sp.sync_point.constants.GeneralConstants.GENERIC_REQUEST_MESSAGE;
import static com.sp.sync_point.constants.GeneralConstants.PASSWORD_RESET_COOLDOWN_KEY_PREFIX;

/**
 * Orchestrates the password-reset flow. Delegates cryptographic work to
 * OtpService, delivery to OtpDeliveryService, identity-provider updates to
 * KeycloakService, and session invalidation to SessionManagementService —
 * this class only holds the business rules that tie those pieces together.
 */
@RequiredArgsConstructor
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpService otpService;
    private final OtpDeliveryService otpDeliveryService;
    private final KeycloakService keycloakService;
    private final SessionManagementService sessionManagementService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final ValidateService validateService;
    private final RedissonClient redissonClient;


    @Override
    @Transactional
    public PasswordResetResponse requestOtp(PasswordResetRequestDto request) {
        validateService.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        String phoneNumber = request.getPhoneNumber();

        RBucket<String> cooldownBucket = redissonClient.getBucket(PASSWORD_RESET_COOLDOWN_KEY_PREFIX + phoneNumber);
        if (cooldownBucket.isExists()) {
            long remainingSeconds = cooldownBucket.remainTimeToLive() / 1000;
            throw new OtpResendCooldownException("Please wait before requesting another code.", Math.max(remainingSeconds, 1));
        }

        Optional<User> userOptional = userRepository.findByPhoneNumber(phoneNumber);

        // User enumeration protection: only generate/send an OTP if the
        // phone number is registered, but always return the same generic
        // response and always start the cooldown, regardless of outcome.
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            invalidateExistingActiveOtps(phoneNumber);

            String rawOtp = otpService.generateOtp(properties.getOtp().getLength());

            PasswordResetOtp otpEntity = new PasswordResetOtp();
            otpEntity.setUserId(user.getId());
            otpEntity.setPhoneNumber(phoneNumber);
            otpEntity.setOtpHash(otpService.hashOtp(rawOtp));
            otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getOtp().getExpirationMinutes()));
            otpEntity.setAttemptCount(0);
            otpEntity.setMaxAttempts(properties.getOtp().getMaxAttempts());
            otpEntity.setStatus(PasswordResetStatus.ACTIVE);
            otpEntity.setLastSentAt(LocalDateTime.now());
            passwordResetOtpRepository.save(otpEntity);

            otpDeliveryService.sendPasswordResetOtp(phoneNumber, rawOtp);
            log.info("Password reset OTP requested for a registered phone number");
        } else {
            log.info("Password reset OTP requested for an unregistered phone number");
        }

        cooldownBucket.set("1", Duration.ofSeconds(properties.getOtp().getResendCooldownSeconds()));

        return new PasswordResetResponse(GENERIC_REQUEST_MESSAGE, 200);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        validateService.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        validateService.validateOtpFormat(request.getOtp(), properties.getOtp().getLength());

        String phoneNumber = request.getPhoneNumber();
        PasswordResetOtp otpEntity = passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .orElseThrow(() -> new OtpInvalidException("The verification code is invalid."));

        if (LocalDateTime.now().isAfter(otpEntity.getExpiresAt())) {
            otpEntity.setStatus(PasswordResetStatus.EXPIRED);
            otpEntity.setInvalidatedAt(LocalDateTime.now());
            passwordResetOtpRepository.save(otpEntity);
            throw new OtpExpiredException("This verification code has expired. Please request a new code.");
        }

        if (otpEntity.getAttemptCount() >= otpEntity.getMaxAttempts()) {
            otpEntity.setStatus(PasswordResetStatus.INVALIDATED);
            otpEntity.setInvalidatedAt(LocalDateTime.now());
            passwordResetOtpRepository.save(otpEntity);
            throw new MaxAttemptsExceededException("Too many failed attempts. Please request a new verification code.");
        }

        boolean matches = otpService.matchesOtp(request.getOtp(), otpEntity.getOtpHash());
        if (!matches) {
            otpEntity.setAttemptCount(otpEntity.getAttemptCount() + 1);
            boolean nowExhausted = otpEntity.getAttemptCount() >= otpEntity.getMaxAttempts();
            if (nowExhausted) {
                otpEntity.setStatus(PasswordResetStatus.INVALIDATED);
                otpEntity.setInvalidatedAt(LocalDateTime.now());
            }
            passwordResetOtpRepository.save(otpEntity);

            if (nowExhausted) {
                throw new MaxAttemptsExceededException("Too many failed attempts. Please request a new verification code.");
            }
            throw new OtpInvalidException("The verification code is invalid.");
        }

        otpEntity.setStatus(PasswordResetStatus.VERIFIED);
        otpEntity.setVerifiedAt(LocalDateTime.now());
        passwordResetOtpRepository.save(otpEntity);

        String rawResetToken = otpService.generateResetToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(otpEntity.getUserId());
        resetToken.setTokenHash(otpService.hashResetToken(rawResetToken));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getToken().getExpirationMinutes()));
        resetToken.setUsed(false);
        passwordResetTokenRepository.save(resetToken);

        return new VerifyOtpResponse(rawResetToken);
    }

    @Override
    @Transactional
    public PasswordResetResponse completeReset(CompletePasswordResetRequest request) {
        validateService.validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        if (request.getResetToken() == null || request.getResetToken().isBlank()) {
            throw new ResetTokenInvalidException("Your password reset session has expired. Please start again.");
        }

        String tokenHash = otpService.hashResetToken(request.getResetToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResetTokenInvalidException("Your password reset session has expired. Please start again."));

        if (resetToken.isUsed() || LocalDateTime.now().isAfter(resetToken.getExpiresAt())) {
            throw new ResetTokenInvalidException("Your password reset session has expired. Please start again.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Update the identity provider first — if this fails, we abort
        // before touching the local mirror or invalidating sessions.
        keycloakService.resetPassword(user.getKeycloakId(), request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        sessionManagementService.invalidateUserSessionByAdmin(user.getPhoneNumber());

        log.info("Password reset completed successfully for user id {}", user.getId());
        return new PasswordResetResponse("Password reset successful. Please log in with your new password.", 200);
    }

    private void invalidateExistingActiveOtps(String phoneNumber) {
        passwordResetOtpRepository
                .findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(PasswordResetStatus.INVALIDATED);
                    existing.setInvalidatedAt(LocalDateTime.now());
                    passwordResetOtpRepository.save(existing);
                });
    }
}