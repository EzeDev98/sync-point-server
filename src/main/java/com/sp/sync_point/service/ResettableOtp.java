package com.sp.sync_point.service;

import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.model.PasswordResetOtp;
import com.sp.sync_point.model.PinResetOtp;

import java.time.LocalDateTime;

/**
 * Common shape shared by {@link PasswordResetOtp} and {@link PinResetOtp}.
 * <p>
 * Implementing this adds no persistence behavior whatsoever — it's a plain
 * Java interface, not a JPA mapping construct, so it has zero effect on
 * table structure, Hibernate mapping, or query behavior. It exists purely
 * so {@code OtpResetEngine} (service.impl) can apply the shared
 * expiry/attempt-limit/match rules once instead of duplicating them per
 * entity type. Both implementing classes already expose exactly these
 * getters/setters via Lombok, so implementing this interface required no
 * change to their fields or annotations.
 */
public interface ResettableOtp {

    String getPhoneNumber();

    void setPhoneNumber(String phoneNumber);

    String getOtpHash();

    void setOtpHash(String otpHash);

    LocalDateTime getExpiresAt();

    void setExpiresAt(LocalDateTime expiresAt);

    int getAttemptCount();

    void setAttemptCount(int attemptCount);

    int getMaxAttempts();

    void setMaxAttempts(int maxAttempts);

    PasswordResetStatus getStatus();

    void setStatus(PasswordResetStatus status);

    LocalDateTime getVerifiedAt();

    void setVerifiedAt(LocalDateTime verifiedAt);

    LocalDateTime getInvalidatedAt();

    void setInvalidatedAt(LocalDateTime invalidatedAt);

    LocalDateTime getLastSentAt();

    void setLastSentAt(LocalDateTime lastSentAt);
}