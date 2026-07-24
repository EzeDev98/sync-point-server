package com.sp.sync_point.exception;

public class CustomApiException extends RuntimeException {

    public CustomApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
