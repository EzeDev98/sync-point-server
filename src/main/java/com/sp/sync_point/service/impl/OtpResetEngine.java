package com.sp.sync_point.service.impl;

import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.exception.MaxAttemptsExceededException;
import com.sp.sync_point.exception.OtpExpiredException;
import com.sp.sync_point.exception.OtpInvalidException;
import com.sp.sync_point.exception.OtpResendCooldownException;
import com.sp.sync_point.exception.ResetTokenInvalidException;

import com.sp.sync_point.service.ResettableOtp;
import com.sp.sync_point.service.ResettableToken;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The password-reset and PIN-reset flows (PasswordResetServiceImpl,
 * PinResetServiceImpl) are the same OTP-then-token workflow applied to two
 * different targets (User.password vs Wallet.pin). Before this extraction,
 * the cooldown check, OTP-invalidation, OTP-verification, and
 * token-usability logic were copy-pasted between the two classes,
 * differing only in entity type and a couple of messages/key prefixes.
 * <p>
 * This class holds exactly that shared logic, generic over
 * {@link ResettableOtp} / {@link ResettableToken} so it works for both
 * entity pairs without either service needing to know about the other's
 * types. Each caller still owns everything that's genuinely different
 * between the two flows: which repository to fetch/save through (passed
 * as a method reference), which messages to use, and — critically — the
 * different existence-check semantics (password reset silently no-ops for
 * an unregistered phone number to avoid user enumeration; PIN reset
 * requires an existing wallet up front, since it inherently already knows
 * the phone number is registered).
 */
@Component
@RequiredArgsConstructor
public class OtpResetEngine {

    private final OtpService otpService;

    /**
     * Cooldown enforcement is a two-step check-then-arm sequence rather
     * than one method, because both callers need to hold the bucket
     * reference between the pre-check (at the top of requestOtp) and
     * arming it (at the very end, after the OTP — if any — was sent).
     */
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

    /**
     * Invalidates the most recent still-active OTP for a phone number, if
     * one exists, before a new one is issued.
     *
     * @param existing the caller's own
     *                 {@code findTopByPhoneNumberAndStatusOrderByCreatedAtDesc}
     *                 lookup result (repository-specific, so it stays the
     *                 caller's responsibility)
     * @param save     the caller's repository save method reference
     */
    public <T extends ResettableOtp> void invalidateExisting(Optional<T> existing, Consumer<T> save) {
        existing.ifPresent(otp -> {
            otp.setStatus(PasswordResetStatus.INVALIDATED);
            otp.setInvalidatedAt(LocalDateTime.now());
            save.accept(otp);
        });
    }

    /**
     * Fills in the fields common to both OTP entity types on a
     * newly-constructed OTP. The caller is still responsible for setting
     * its own foreign key (userId or walletId) and persisting the entity —
     * this only populates the shared fields.
     */
    public <T extends ResettableOtp> void populateNewOtp(T otp, String phoneNumber, String rawOtp, int expirationMinutes, int maxAttempts) {
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpHash(otpService.hashOtp(rawOtp));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        otp.setAttemptCount(0);
        otp.setMaxAttempts(maxAttempts);
        otp.setStatus(PasswordResetStatus.ACTIVE);
        otp.setLastSentAt(LocalDateTime.now());
    }

    /**
     * Verifies {@code otp} against {@code rawOtp}, applying the shared
     * expiry / attempt-limit / match rules. On any rejection this both
     * mutates and persists (via {@code save}) the entity before throwing —
     * matching exactly what both callers did inline before this
     * extraction. On success, the entity is left VERIFIED and saved; the
     * caller proceeds to issue a reset token.
     */
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

    /**
     * @throws ResetTokenInvalidException with {@code expiredMessage} if the
     *         token was already used or has expired
     */
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