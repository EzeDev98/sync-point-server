package com.sp.sync_point.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PinChangeLockedException extends RuntimeException {
    private final long remainingMinutes;

    public PinChangeLockedException(String message, long remainingMinutes) {
        super(message);
        this.remainingMinutes = remainingMinutes;
    }
}
