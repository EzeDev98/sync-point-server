package com.sp.syncPoint.common.exception;

import org.springframework.http.HttpStatus;

public class InternalAuthCallException extends RuntimeException {

    private final HttpStatus status;

    public InternalAuthCallException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
