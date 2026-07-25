package com.sp.syncPoint.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransactionLimitException extends RuntimeException {
    public InvalidTransactionLimitException(String message) {
        super(message);
    }
}
