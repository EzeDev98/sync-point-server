package com.sp.sync_point.service.impl;

import com.sp.sync_point.config.PinResetProperties;
import com.sp.sync_point.dto.CompletePinResetRequest;
import com.sp.sync_point.dto.PinResetRequestDto;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.VerifyPinOtpRequest;
import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.exception.MaxAttemptsExceededException;
import com.sp.sync_point.exception.OtpExpiredException;
import com.sp.sync_point.exception.OtpInvalidException;
import com.sp.sync_point.exception.OtpResendCooldownException;
import com.sp.sync_point.exception.ResetTokenInvalidException;
import com.sp.sync_point.model.PinResetOtp;
import com.sp.sync_point.model.PinResetToken;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.PinResetOtpRepository;
import com.sp.sync_point.repository.PinResetTokenRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.PinResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.OtpDeliveryService;
import com.sp.sync_point.service.PinResetService;
import com.sp.sync_point.service.WalletService;
import jakarta.ws.rs.NotFoundException;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.PIN_RESET_COOLDOWN_KEY_PREFIX;

/**
 * Orchestrates the wallet PIN-reset flow. Deliberately reuses OtpService,
 * OtpDeliveryService, and the shared OTP exceptions from the password-reset
 * feature — the cryptographic/delivery/error-handling machinery is
 * identical, only the domain target (Wallet.pin vs User.password/Keycloak)
 * and trust model (authenticated vs public) differ. See PinResetService
 * for a summary of those differences.
 */
@Service
public class PinResetServiceImpl implements PinResetService {

    private static final Logger log = LoggerFactory.getLogger(PinResetServiceImpl.class);

    private final WalletRepository walletRepository;
    private final PinResetOtpRepository pinResetOtpRepository;
    private final PinResetTokenRepository pinResetTokenRepository;
    private final OtpService otpService;
    private final OtpDeliveryService otpDeliveryService;
    private final WalletService walletService;
    private final PinResetProperties properties;
    private final ValidateService validateService;
    private final RedissonClient redissonClient;

    public PinResetServiceImpl(WalletRepository walletRepository,
                               PinResetOtpRepository pinResetOtpRepository,
                               PinResetTokenRepository pinResetTokenRepository,
                               OtpService otpService,
                               OtpDeliveryService otpDeliveryService,
                               WalletService walletService,
                               PinResetProperties properties,
                               ValidateService validateService,
                               RedissonClient redissonClient) {
        this.walletRepository = walletRepository;
        this.pinResetOtpRepository = pinResetOtpRepository;
        this.pinResetTokenRepository = pinResetTokenRepository;
        this.otpService = otpService;
        this.otpDeliveryService = otpDeliveryService;
        this.walletService = walletService;
        this.properties = properties;
        this.validateService = validateService;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional
    public PinResetResponse requestOtp(PinResetRequestDto request) {
        validateService.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        String phoneNumber = request.getPhoneNumber();

        Wallet wallet = walletRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + phoneNumber));

        RBucket<String> cooldownBucket = redissonClient.getBucket(PIN_RESET_COOLDOWN_KEY_PREFIX + phoneNumber);
        if (cooldownBucket.isExists()) {
            long remainingSeconds = cooldownBucket.remainTimeToLive() / 1000;
            throw new OtpResendCooldownException("Please wait before requesting another code.", Math.max(remainingSeconds, 1));
        }

        invalidateExistingActiveOtps(phoneNumber);

        String rawOtp = otpService.generateOtp(properties.getOtp().getLength());

        PinResetOtp otpEntity = new PinResetOtp();
        otpEntity.setWalletId(wallet.getId());
        otpEntity.setPhoneNumber(phoneNumber);
        otpEntity.setOtpHash(otpService.hashOtp(rawOtp));
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getOtp().getExpirationMinutes()));
        otpEntity.setAttemptCount(0);
        otpEntity.setMaxAttempts(properties.getOtp().getMaxAttempts());
        otpEntity.setStatus(PasswordResetStatus.ACTIVE);
        otpEntity.setLastSentAt(LocalDateTime.now());
        pinResetOtpRepository.save(otpEntity);

        otpDeliveryService.sendPasswordResetOtp(phoneNumber, rawOtp);
        cooldownBucket.set("1", Duration.ofSeconds(properties.getOtp().getResendCooldownSeconds()));

        log.info("PIN reset OTP requested for wallet belonging to user: {}", phoneNumber);
        return new PinResetResponse("A verification code has been sent to your registered phone number.", 200);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyPinOtpRequest request) {
        validateService.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        validateService.validateOtpFormat(request.getOtp(), properties.getOtp().getLength());

        String phoneNumber = request.getPhoneNumber();
        PinResetOtp otpEntity = pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .orElseThrow(() -> new OtpInvalidException("The verification code is invalid."));

        if (LocalDateTime.now().isAfter(otpEntity.getExpiresAt())) {
            otpEntity.setStatus(PasswordResetStatus.EXPIRED);
            otpEntity.setInvalidatedAt(LocalDateTime.now());
            pinResetOtpRepository.save(otpEntity);
            throw new OtpExpiredException("This verification code has expired. Please request a new code.");
        }

        if (otpEntity.getAttemptCount() >= otpEntity.getMaxAttempts()) {
            otpEntity.setStatus(PasswordResetStatus.INVALIDATED);
            otpEntity.setInvalidatedAt(LocalDateTime.now());
            pinResetOtpRepository.save(otpEntity);
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
            pinResetOtpRepository.save(otpEntity);

            if (nowExhausted) {
                throw new MaxAttemptsExceededException("Too many failed attempts. Please request a new verification code.");
            }
            throw new OtpInvalidException("The verification code is invalid.");
        }

        otpEntity.setStatus(PasswordResetStatus.VERIFIED);
        otpEntity.setVerifiedAt(LocalDateTime.now());
        pinResetOtpRepository.save(otpEntity);

        String rawResetToken = otpService.generateResetToken();
        PinResetToken resetToken = new PinResetToken();
        resetToken.setWalletId(otpEntity.getWalletId());
        resetToken.setTokenHash(otpService.hashResetToken(rawResetToken));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getToken().getExpirationMinutes()));
        resetToken.setUsed(false);
        pinResetTokenRepository.save(resetToken);

        return new VerifyOtpResponse(rawResetToken);
    }

    @Override
    @Transactional
    public PinResetResponse completeReset(CompletePinResetRequest request) {
        if (request.getResetToken() == null || request.getResetToken().isBlank()) {
            throw new ResetTokenInvalidException("Your PIN reset session has expired. Please start again.");
        }

        String tokenHash = otpService.hashResetToken(request.getResetToken());
        PinResetToken resetToken = pinResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResetTokenInvalidException("Your PIN reset session has expired. Please start again."));

        if (resetToken.isUsed() || LocalDateTime.now().isAfter(resetToken.getExpiresAt())) {
            throw new ResetTokenInvalidException("Your PIN reset session has expired. Please start again.");
        }

        Wallet wallet = walletRepository.findById(resetToken.getWalletId())
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        walletService.resetPin(wallet.getPhoneNumber(), request.getPin(), request.getConfirmNewPin());

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        pinResetTokenRepository.save(resetToken);

        log.info("PIN reset completed successfully for wallet id {}", wallet.getId());
        return new PinResetResponse("PIN reset successful.", 200);
    }

    private void invalidateExistingActiveOtps(String phoneNumber) {
        pinResetOtpRepository
                .findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(PasswordResetStatus.INVALIDATED);
                    existing.setInvalidatedAt(LocalDateTime.now());
                    pinResetOtpRepository.save(existing);
                });
    }
}