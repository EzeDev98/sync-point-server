package com.sp.sync_point.service;

import com.sp.sync_point.model.PasswordResetToken;
import com.sp.sync_point.model.PinResetToken;

import java.time.LocalDateTime;

/**
 * Common shape shared by {@link PasswordResetToken} and
 * {@link PinResetToken}. See {@link ResettableOtp} for why this is a plain
 * interface with no persistence implications.
 */
public interface ResettableToken {

    String getTokenHash();

    void setTokenHash(String tokenHash);

    LocalDateTime getExpiresAt();

    void setExpiresAt(LocalDateTime expiresAt);

    boolean isUsed();

    void setUsed(boolean used);

    LocalDateTime getUsedAt();

    void setUsedAt(LocalDateTime usedAt);
}