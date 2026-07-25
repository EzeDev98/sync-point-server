package com.sp.syncPoint.common.exception;

import lombok.Getter;

@Getter
public class PinChangeLockedException extends RuntimeException {
    private final long remainingMinutes;

    public PinChangeLockedException(String message, long remainingMinutes) {
        super(message);
        this.remainingMinutes = remainingMinutes;
    }
}
