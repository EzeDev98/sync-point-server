package com.sp.sync_point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IncorrectPinException extends IllegalArgumentException{
    public IncorrectPinException(String message) {
        super(message);
    }
}
