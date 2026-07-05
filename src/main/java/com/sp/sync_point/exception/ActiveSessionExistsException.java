package com.sp.sync_point.exception;

public class ActiveSessionExistsException extends RuntimeException {
    public ActiveSessionExistsException(String message) {
        super(message);
    }
}