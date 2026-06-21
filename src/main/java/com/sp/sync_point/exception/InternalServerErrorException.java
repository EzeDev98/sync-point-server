package com.sp.sync_point.exception;

public class InternalServerErrorException extends RuntimeException {
    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
