package com.sp.syncPoint.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyRequestException extends RuntimeException {

    private final HttpStatus status;

    public EmptyRequestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
