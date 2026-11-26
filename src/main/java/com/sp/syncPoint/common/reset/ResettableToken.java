package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.feature.pinreset.model.PinResetToken;
import java.time.LocalDateTime;

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