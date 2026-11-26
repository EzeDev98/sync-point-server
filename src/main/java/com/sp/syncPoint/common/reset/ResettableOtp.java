package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.feature.pinreset.model.PinResetOtp;

import java.time.LocalDateTime;

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