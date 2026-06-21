package com.sp.sync_point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PinVerificationException extends RuntimeException {

    public PinVerificationException(String message) {
        super(message);
    }

    public PinVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}