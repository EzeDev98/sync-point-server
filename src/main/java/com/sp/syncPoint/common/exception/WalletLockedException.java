package com.sp.syncPoint.common.exception;

import lombok.Getter;

@Getter
public class WalletLockedException extends RuntimeException {
    private final long remainingMinutes;

    public WalletLockedException(String message, long remainingMinutes) {
        super(message);
        this.remainingMinutes = remainingMinutes;
    }

    public WalletLockedException(String message) {
        super(message);
        this.remainingMinutes = 0;
    }
}
