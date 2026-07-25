package com.sp.syncPoint.feature.pinreset.service.impl;

import com.sp.syncPoint.common.exception.OtpInvalidException;
import com.sp.syncPoint.common.exception.ResetTokenInvalidException;
import com.sp.syncPoint.common.reset.OtpResetEngine;
import com.sp.syncPoint.common.reset.OtpService;
import com.sp.syncPoint.common.reset.ResetValidator;
import com.sp.syncPoint.common.reset.delivery.OtpDeliveryService;
import com.sp.syncPoint.common.reset.dto.VerifyOtpRequest;
import com.sp.syncPoint.common.reset.dto.VerifyOtpResponse;
import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.feature.pinreset.config.PinResetProperties;
import com.sp.syncPoint.feature.pinreset.dto.CompletePinResetRequest;
import com.sp.syncPoint.feature.pinreset.dto.PinResetRequestDto;
import com.sp.syncPoint.feature.pinreset.dto.PinResetResponse;
import com.sp.syncPoint.feature.pinreset.model.PinResetOtp;
import com.sp.syncPoint.feature.pinreset.model.PinResetToken;
import com.sp.syncPoint.feature.pinreset.repository.PinResetOtpRepository;
import com.sp.syncPoint.feature.pinreset.repository.PinResetTokenRepository;
import com.sp.syncPoint.feature.pinreset.service.PinResetService;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletPinService;
import jakarta.ws.rs.NotFoundException;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sp.syncPoint.common.constants.GeneralConstants.PIN_RESET_COOLDOWN_KEY_PREFIX;


/**
 * Orchestrates the wallet PIN-reset flow. Deliberately reuses OtpService,
 * OtpDeliveryService, and the shared OTP/reset-token machinery
 * ({@link OtpResetEngine}) from the password-reset feature — the
 * cryptographic/delivery/error-handling machinery is identical, only the
 * domain target (Wallet.pin vs User.password/Keycloak) and trust model
 * (authenticated vs public) differ. See PinResetService for a summary of
 * those differences.
 */
@Service
public class PinResetServiceImpl implements PinResetService {

    private static final Logger log = LoggerFactory.getLogger(PinResetServiceImpl.class);

    private final WalletRepository walletRepository;
    private final PinResetOtpRepository pinResetOtpRepository;
    private final PinResetTokenRepository pinResetTokenRepository;
    private final OtpService otpService;
    private final OtpDeliveryService otpDeliveryService;
    private final WalletPinService walletService;
    private final PinResetProperties properties;
    private final ResetValidator resetValidator;
    private final RedissonClient redissonClient;
    private final OtpResetEngine otpResetEngine;

    public PinResetServiceImpl(WalletRepository walletRepository,
                               PinResetOtpRepository pinResetOtpRepository,
                               PinResetTokenRepository pinResetTokenRepository,
                               OtpService otpService,
                               OtpDeliveryService otpDeliveryService,
                               WalletPinService walletService,
                               PinResetProperties properties,
                               ResetValidator resetValidator,
                               RedissonClient redissonClient,
                               OtpResetEngine otpResetEngine) {
        this.walletRepository = walletRepository;
        this.pinResetOtpRepository = pinResetOtpRepository;
        this.pinResetTokenRepository = pinResetTokenRepository;
        this.otpService = otpService;
        this.otpDeliveryService = otpDeliveryService;
        this.walletService = walletService;
        this.properties = properties;
        this.resetValidator = resetValidator;
        this.redissonClient = redissonClient;
        this.otpResetEngine = otpResetEngine;
    }

    @Override
    @Transactional
    public PinResetResponse requestOtp(PinResetRequestDto request) {
        resetValidator.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        String phoneNumber = request.getPhoneNumber();

        Wallet wallet = walletRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + phoneNumber));

        RBucket<String> cooldownBucket = otpResetEngine.getCooldownBucket(redissonClient, PIN_RESET_COOLDOWN_KEY_PREFIX, phoneNumber);
        otpResetEngine.rejectIfCoolingDown(cooldownBucket);

        otpResetEngine.invalidateExisting(
                pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE),
                pinResetOtpRepository::save);

        String rawOtp = otpService.generateOtp(properties.getOtp().getLength());

        PinResetOtp otpEntity = new PinResetOtp();
        otpEntity.setWalletId(wallet.getId());
        otpResetEngine.populateNewOtp(otpEntity, phoneNumber, rawOtp, properties.getOtp().getExpirationMinutes(), properties.getOtp().getMaxAttempts());
        pinResetOtpRepository.save(otpEntity);

        otpDeliveryService.sendPasswordResetOtp(phoneNumber, rawOtp);
        otpResetEngine.startCooldown(cooldownBucket, properties.getOtp().getResendCooldownSeconds());

        log.info("PIN reset OTP requested for wallet belonging to user: {}", phoneNumber);
        return new PinResetResponse("A verification code has been sent to your registered phone number.", 200);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        resetValidator.validatePhoneNumberFoResetOperations(request.getPhoneNumber());
        resetValidator.validateOtpFormat(request.getOtp(), properties.getOtp().getLength());

        String phoneNumber = request.getPhoneNumber();
        PinResetOtp otpEntity = pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(phoneNumber, PasswordResetStatus.ACTIVE)
                .orElseThrow(() -> new OtpInvalidException("The verification code is invalid."));

        otpResetEngine.verify(otpEntity, request.getOtp(), pinResetOtpRepository::save);

        String rawResetToken = otpService.generateResetToken();
        PinResetToken resetToken = new PinResetToken();
        resetToken.setWalletId(otpEntity.getWalletId());
        otpResetEngine.populateNewToken(resetToken, rawResetToken, properties.getToken().getExpirationMinutes());
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

        otpResetEngine.assertUsable(resetToken, "Your PIN reset session has expired. Please start again.");

        Wallet wallet = walletRepository.findById(resetToken.getWalletId())
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        walletService.resetPin(wallet.getPhoneNumber(), request.getPin(), request.getConfirmNewPin());

        otpResetEngine.markUsed(resetToken, pinResetTokenRepository::save);

        log.info("PIN reset completed successfully for wallet id {}", wallet.getId());
        return new PinResetResponse("PIN reset successful.", 200);
    }
}