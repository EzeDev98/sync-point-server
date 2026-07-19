package com.sp.sync_point.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpResendCooldownException extends RuntimeException {
    private final long remainingSeconds;

    public OtpResendCooldownException(String message, long remainingSeconds) {
        super(message);
        this.remainingSeconds = remainingSeconds;
    }
}