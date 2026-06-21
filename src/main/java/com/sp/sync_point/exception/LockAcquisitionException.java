package com.sp.sync_point.exception;

/**
 * Thrown when a distributed lock cannot be acquired within the timeout.
 */
public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String message) {
        super(message);
    }
    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}