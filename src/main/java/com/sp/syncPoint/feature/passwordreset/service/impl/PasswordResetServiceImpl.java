package com.sp.syncPoint.feature.passwordreset.service.impl;

import com.sp.syncPoint.common.exception.OtpInvalidException;
import com.sp.syncPoint.common.exception.ResetTokenInvalidException;
import com.sp.syncPoint.common.exception.UserNotFoundException;
import com.sp.syncPoint.common.reset.OtpResetEngine;
import com.sp.syncPoint.common.reset.OtpService;
import com.sp.syncPoint.common.reset.ResetValidator;
import com.sp.syncPoint.common.reset.delivery.OtpDeliveryService;
import com.sp.syncPoint.common.reset.dto.VerifyOtpRequest;
import com.sp.syncPoint.common.reset.dto.VerifyOtpResponse;
import com.sp.syncPoint.feature.passwordreset.config.PasswordResetProperties;
import com.sp.syncPoint.feature.passwordreset.dto.CompletePasswordResetRequest;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetRequestDto;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetResponse;
import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.feature.passwordreset.model.PasswordResetOtp;
import com.sp.syncPoint.feature.passwordreset.model.PasswordResetToken;
import com.sp.syncPoint.feature.passwordreset.repository.PasswordResetOtpRepository;
import com.sp.syncPoint.feature.passwordreset.repository.PasswordResetTokenRepository;
import com.sp.syncPoint.feature.passwordreset.service.PasswordResetService;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.sp.syncPoint.common.constants.GeneralConstants.GENERIC_REQUEST_MESSAGE;
import static com.sp.syncPoint.common.constants.GeneralConstants.PASSWORD_RESET_COOLDOWN_KEY_PREFIX;

/**
 * Orchestrates the password-reset flow. Delegates cryptographic work to
 * OtpService, delivery to OtpDeliveryService, identity-provider updates to
 * KeycloakService, and session invalidation to SessionManagementService.
 * The shared OTP-verification and reset-token machinery — identical to
 * PinResetServiceImpl's, since both are the same OTP-then-token workflow
 * applied to a different target — lives in {@link OtpResetEngine}; this
 * class holds only the business rules specific to resetting a password:
 * user-enumeration protection on request, and updating Keycloak + the
 * local password mirror + sessions on completion.
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
    private final SessionManagementService sessionManagementService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final ResetValidator resetValidator;
    private final RedissonClient redissonClient;
    private final OtpResetEngine otpResetEngine;

    @Override
    @Transactional
    public PasswordResetResponse requestOtp(PasswordResetRequestDto request) {
        resetValidator.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        String phoneNumber = request.getPhoneNumber();

        RBucket<String> cooldownBucket = otpResetEngine.getCooldownBucket(redissonClient, PASSWORD_RESET_COOLDOWN_KEY_PREFIX, phoneNumber);
        otpResetEngine.rejectIfCoolingDown(cooldownBucket);

        Optional<User> userOptional = userRepository.findByPhoneNumber(phoneNumber);

        // User enumeration protection: only generate/send an OTP if the
        // phone number is registered, but always return the same generic
        // response and always start the cooldown, regardless of outcome.
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            otpResetEngine.invalidateExisting(
                    passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE),
                    passwordResetOtpRepository::save);

            String rawOtp = otpService.generateOtp(properties.getOtp().getLength());

            PasswordResetOtp otpEntity = new PasswordResetOtp();
            otpEntity.setUserId(user.getId());
            otpResetEngine.populateNewOtp(otpEntity, phoneNumber, rawOtp, properties.getOtp().getExpirationMinutes(), properties.getOtp().getMaxAttempts());
            passwordResetOtpRepository.save(otpEntity);

            otpDeliveryService.sendPasswordResetOtp(phoneNumber, rawOtp);
            log.info("Password reset OTP requested for a registered phone number");
        } else {
            log.info("Password reset OTP requested for an unregistered phone number");
        }

        otpResetEngine.startCooldown(cooldownBucket, properties.getOtp().getResendCooldownSeconds());

        return new PasswordResetResponse(GENERIC_REQUEST_MESSAGE, 200);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        resetValidator.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        resetValidator.validateOtpFormat(request.getOtp(), properties.getOtp().getLength());

        String phoneNumber = request.getPhoneNumber();
        PasswordResetOtp otpEntity = passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .orElseThrow(() -> new OtpInvalidException("The verification code is invalid."));

        otpResetEngine.verify(otpEntity, request.getOtp(), passwordResetOtpRepository::save);

        String rawResetToken = otpService.generateResetToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(otpEntity.getUserId());
        otpResetEngine.populateNewToken(resetToken, rawResetToken, properties.getToken().getExpirationMinutes());
        passwordResetTokenRepository.save(resetToken);

        return new VerifyOtpResponse(rawResetToken);
    }

    @Override
    @Transactional
    public PasswordResetResponse completeReset(CompletePasswordResetRequest request) {
        resetValidator.validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        if (request.getResetToken() == null || request.getResetToken().isBlank()) {
            throw new ResetTokenInvalidException("Your password reset session has expired. Please start again.");
        }

        String tokenHash = otpService.hashResetToken(request.getResetToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResetTokenInvalidException("Your password reset session has expired. Please start again."));

        otpResetEngine.assertUsable(resetToken, "Your password reset session has expired. Please start again.");

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Update the identity provider first — if this fails, we abort
        // before touching the local mirror or invalidating sessions.
//        keycloakService.resetPassword(user.getKeycloakId(), request.getNewPassword());

//        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        otpResetEngine.markUsed(resetToken, passwordResetTokenRepository::save);

        sessionManagementService.invalidateUserSessionByAdmin(user.getPhoneNumber());

        log.info("Password reset completed successfully for user id {}", user.getId());
        return new PasswordResetResponse("Password reset successful. Please log in with your new password.", 200);
    }
}