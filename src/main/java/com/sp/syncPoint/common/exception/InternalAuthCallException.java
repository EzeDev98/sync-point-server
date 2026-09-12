package com.sp.syncPoint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when internal-auth-server returns an HTTP error status that isn't
 * one of this app's own specifically-modeled exceptions (e.g.
 * RefreshTokenInvalidException for a rejected refresh token). Carries the
 * real downstream status and message through instead of letting
 * RestClient's default HttpClientErrorException/HttpServerErrorException
 * fall through GlobalExceptionHandler's unmapped-RuntimeException path,
 * where it used to be silently turned into an opaque generic 500.
 */
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
