package com.sp.syncPoint.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransactionRequestException extends IllegalArgumentException {
    public InvalidTransactionRequestException(String message) {
        super(message);
    }
}
