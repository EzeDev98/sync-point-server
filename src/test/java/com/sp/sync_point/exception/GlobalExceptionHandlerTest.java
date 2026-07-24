package com.sp.sync_point.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── registry-driven simple mappings (spot checks across the range) ──

    @Test
    void handleMappedRuntimeException_notFoundStyleException_mapsToNotFound() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new WalletNotFoundException("no wallet"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("no wallet");
    }

    @Test
    void handleMappedRuntimeException_conflictStyleException_mapsToConflict() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new AlreadyExistException("already exists"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("already exists");
    }

    @Test
    void handleMappedRuntimeException_lockedStyleException_mapsToLocked() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new WalletLockedException("locked out"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.LOCKED.value());
        assertThat(problem.getDetail()).isEqualTo("locked out");
    }

    @Test
    void handleMappedRuntimeException_tooManyRequestsStyleException_mapsToTooManyRequests() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new MaxAttemptsExceededException("too many"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void handleMappedRuntimeException_unauthorizedStyleException_mapsToUnauthorized() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new IncorrectPinException("bad pin"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void handleMappedRuntimeException_paymentRequiredStyleException_mapsToPaymentRequired() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new InsufficientFundsException("no funds"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED.value());
    }

    @Test
    void handleMappedRuntimeException_serviceUnavailableStyleException_mapsToServiceUnavailable() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new ServiceUnavailableException("down"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    // ── unmapped RuntimeException falls back to the generic handling ────

    @Test
    void handleMappedRuntimeException_unrecognizedRuntimeException_fallsBackToGenericUnexpectedError() {
        ProblemDetail problem = handler.handleMappedRuntimeException(new IllegalStateException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(problem.getProperties()).containsKey("traceId");
    }

    // ── exceptions with custom behavior keep their own handler ──────────

    @Test
    void handleDuplicateTransactionCheckException_mapsToInternalServerError() {
        ProblemDetail problem = handler.handleDuplicateTransactionCheckException(
                new DuplicateTransactionCheckException("duplicate"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("duplicate");
    }

    @Test
    void handleInsufficientBalance_setsCustomTitle() {
        ProblemDetail problem = handler.handleInsufficientBalance(new InsufficientBalanceException("low balance"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED.value());
        assertThat(problem.getDetail()).isEqualTo("low balance");
        assertThat(problem.getTitle()).isEqualTo("Insufficient Balance");
    }

    @Test
    void handleAccessDeniedException_setsCustomTitle() {
        ProblemDetail problem = handler.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Access Denied");
    }

    @Test
    void handleSystemWalletInsufficientBalance_usesGenericMessageNotExceptionMessage() {
        ProblemDetail problem = handler.handleSystemWalletInsufficientBalance(
                new SystemWalletInsufficientBalanceException("internal treasury details"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        // Deliberately does NOT leak the raw exception message to the client.
        assertThat(problem.getDetail()).isEqualTo("This operation is temporarily unavailable. Please try again shortly.");
        assertThat(problem.getTitle()).isEqualTo("Service Temporarily Unavailable");
    }

    // ── true catch-all for anything else (including checked exceptions) ─

    @Test
    void handleGeneric_producesTraceIdAndGenericMessage() {
        ProblemDetail problem = handler.handleGeneric(new RuntimeException("something broke"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(problem.getProperties()).containsKey("traceId");
    }

    @Test
    void handleGeneric_eachCallGetsAUniqueTraceId() {
        ProblemDetail first = handler.handleGeneric(new RuntimeException("one"));
        ProblemDetail second = handler.handleGeneric(new RuntimeException("two"));

        assertThat(first.getProperties().get("traceId"))
                .isNotEqualTo(second.getProperties().get("traceId"));
    }
}