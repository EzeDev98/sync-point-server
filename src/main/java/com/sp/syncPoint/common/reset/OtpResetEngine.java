package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.common.exception.*;
import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class OtpResetEngine {

    private final OtpService otpService;

    public RBucket<String> getCooldownBucket(RedissonClient redissonClient, String keyPrefix, String phoneNumber) {
        return redissonClient.getBucket(keyPrefix + phoneNumber);
    }

    public void rejectIfCoolingDown(RBucket<String> cooldownBucket) {
        if (cooldownBucket.isExists()) {
            long remainingSeconds = cooldownBucket.remainTimeToLive() / 1000;
            throw new OtpResendCooldownException("Please wait before requesting another code.", Math.max(remainingSeconds, 1));
        }
    }

    public void startCooldown(RBucket<String> cooldownBucket, int resendCooldownSeconds) {
        cooldownBucket.set("1", Duration.ofSeconds(resendCooldownSeconds));
    }

    public <T extends ResettableOtp> void invalidateExisting(Optional<T> existing, Consumer<T> save) {
        existing.ifPresent(otp -> {
            otp.setStatus(PasswordResetStatus.INVALIDATED);
            otp.setInvalidatedAt(LocalDateTime.now());
            save.accept(otp);
        });
    }

    public <T extends ResettableOtp> void populateNewOtp(T otp, String phoneNumber, String rawOtp, int expirationMinutes, int maxAttempts) {
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpHash(otpService.hashOtp(rawOtp));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        otp.setAttemptCount(0);
        otp.setMaxAttempts(maxAttempts);
        otp.setStatus(PasswordResetStatus.ACTIVE);
        otp.setLastSentAt(LocalDateTime.now());
    }

    public <T extends ResettableOtp> void verify(T otp, String rawOtp, Consumer<T> save) {
        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            otp.setStatus(PasswordResetStatus.EXPIRED);
            otp.setInvalidatedAt(LocalDateTime.now());
            save.accept(otp);
            throw new OtpExpiredException("This verification code has expired. Please request a new code.");
        }

        if (otp.getAttemptCount() >= otp.getMaxAttempts()) {
            otp.setStatus(PasswordResetStatus.INVALIDATED);
            otp.setInvalidatedAt(LocalDateTime.now());
            save.accept(otp);
            throw new MaxAttemptsExceededException("Too many failed attempts. Please request a new verification code.");
        }

        boolean matches = otpService.matchesOtp(rawOtp, otp.getOtpHash());
        if (!matches) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            boolean nowExhausted = otp.getAttemptCount() >= otp.getMaxAttempts();
            if (nowExhausted) {
                otp.setStatus(PasswordResetStatus.INVALIDATED);
                otp.setInvalidatedAt(LocalDateTime.now());
            }
            save.accept(otp);

            if (nowExhausted) {
                throw new MaxAttemptsExceededException("Too many failed attempts. Please request a new verification code.");
            }
            throw new OtpInvalidException("The verification code is invalid.");
        }

        otp.setStatus(PasswordResetStatus.VERIFIED);
        otp.setVerifiedAt(LocalDateTime.now());
        save.accept(otp);
    }

    public <T extends ResettableToken> void populateNewToken(T token, String rawResetToken, int expirationMinutes) {
        token.setTokenHash(otpService.hashResetToken(rawResetToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        token.setUsed(false);
    }

    public <T extends ResettableToken> void assertUsable(T token, String expiredMessage) {
        if (token.isUsed() || LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new ResetTokenInvalidException(expiredMessage);
        }
    }

    public <T extends ResettableToken> void markUsed(T token, Consumer<T> save) {
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        save.accept(token);
    }
}