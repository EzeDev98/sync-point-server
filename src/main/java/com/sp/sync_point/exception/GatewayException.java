package com.sp.sync_point.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Robust custom exception for Mifos Gateway
 * Handles all types of errors with detailed context
 */
@Getter
public class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;

    private final HttpStatus httpStatus;

    private final String requestId;

    private final LocalDateTime timestamp;

    private final Map<String, Object> metadata;

    private final boolean retryable;

    // Private constructor - use builder
    private GatewayException(Builder builder) {
        super(builder.message, builder.cause);
        this.errorCode = builder.errorCode;
        this.httpStatus = builder.httpStatus;
        this.requestId = builder.requestId;
        this.timestamp = LocalDateTime.now();
        this.metadata = builder.metadata;
        this.retryable = builder.retryable;
    }

    // ============= Static Factory Methods =============

    /**
     * Mifos API returned 4xx client error
     */
    public static GatewayException bankingClientError(String message, int statusCode, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.MIFOS_CLIENT_ERROR)
                .message(message)
                .httpStatus(HttpStatus.valueOf(statusCode))
                .requestId(requestId)
                .retryable(false)
                .addMetadata("mifosStatusCode", statusCode)
                .build();
    }

    /**
     * Mifos API returned 5xx server error (retryable)
     */
    public static GatewayException bankingServerError(String message, int statusCode, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.MIFOS_SERVER_ERROR)
                .message(message)
                .httpStatus(HttpStatus.BAD_GATEWAY)
                .requestId(requestId)
                .retryable(true)
                .addMetadata("mifosStatusCode", statusCode)
                .build();
    }

    /**
     * Duplicate request detected (idempotency)
     */
    public static GatewayException duplicateRequest(String requestId, Object cachedResponse) {
        return builder()
                .errorCode(GatewayErrorCode.DUPLICATE_REQUEST)
                .message("Request is already being processed or has been completed")
                .httpStatus(HttpStatus.CONFLICT)
                .requestId(requestId)
                .retryable(false)
                .addMetadata("cachedResponse", cachedResponse)
                .build();
    }

    /**
     * Request is currently being processed
     */
    public static GatewayException requestInProgress(String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.REQUEST_IN_PROGRESS)
                .message("Request is currently being processed. Please wait and check status.")
                .httpStatus(HttpStatus.CONFLICT)
                .requestId(requestId)
                .retryable(false)
                .build();
    }

    /**
     * Idempotency system error
     */
    public static GatewayException idempotencyError(String message, String requestId, Throwable cause) {
        return builder()
                .errorCode(GatewayErrorCode.IDEMPOTENCY_ERROR)
                .message(message)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .requestId(requestId)
                .cause(cause)
                .retryable(true)
                .build();
    }

    /**
     * Validation error with per-field detail.
     * <p>Use this overload when collecting individual field-level errors from
     * bean validation (e.g. {@code @Valid}) so callers receive a structured map
     * they can parse programmatically.
     *
     * @param message          top-level human-readable summary
     * @param validationErrors map of fieldName → error message (may be {@code null})
     */
    public static GatewayException validationError(String message, Map<String, String> validationErrors) {
        return builder()
                .errorCode(GatewayErrorCode.VALIDATION_ERROR)
                .message(message)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .retryable(false)
                .addMetadata("validationErrors", validationErrors)
                .build();
    }

    /**
     * Validation error without per-field detail.
     * <p>Convenience overload for simple single-message validation failures
     * where individual field errors do not apply (e.g. business-rule checks).
     *
     * @param message human-readable validation failure message
     */
    public static GatewayException validationError(String message) {
        return validationError(message, null);
    }

    /**
     * Missing required header
     */
    public static GatewayException missingHeader(String headerName) {
        return builder()
                .errorCode(GatewayErrorCode.MISSING_HEADER)
                .message(String.format("Missing required header: %s", headerName))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .retryable(false)
                .addMetadata("missingHeader", headerName)
                .build();
    }

    /**
     * Network/connectivity error (retryable)
     */
    public static GatewayException networkError(String message, String requestId, Throwable cause) {
        return builder()
                .errorCode(GatewayErrorCode.NETWORK_ERROR)
                .message(message)
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .requestId(requestId)
                .cause(cause)
                .retryable(true)
                .build();
    }

    /**
     * Timeout error (retryable)
     */
    public static GatewayException timeoutError(String operation, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.TIMEOUT_ERROR)
                .message(String.format("Operation '%s' timed out", operation))
                .httpStatus(HttpStatus.GATEWAY_TIMEOUT)
                .requestId(requestId)
                .retryable(true)
                .addMetadata("operation", operation)
                .build();
    }

    /**
     * Circuit breaker open
     */
    public static GatewayException circuitBreakerOpen(String serviceName) {
        return builder()
                .errorCode(GatewayErrorCode.CIRCUIT_BREAKER_OPEN)
                .message(String.format("Service '%s' is temporarily unavailable. Circuit breaker is open.", serviceName))
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .retryable(false)
                .addMetadata("service", serviceName)
                .build();
    }

    /**
     * Rate limit exceeded
     */
    public static GatewayException rateLimitExceeded(String sourceApp) {
        return builder()
                .errorCode(GatewayErrorCode.RATE_LIMIT_EXCEEDED)
                .message("Too many requests. Please try again later.")
                .httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                .retryable(true)
                .addMetadata("sourceApp", sourceApp)
                .build();
    }

    /**
     * Resource not found
     */
    public static GatewayException notFound(String resourceType, Long resourceId, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.RESOURCE_NOT_FOUND)
                .message(String.format("%s with ID %d not found", resourceType, resourceId))
                .httpStatus(HttpStatus.NOT_FOUND)
                .requestId(requestId)
                .retryable(false)
                .addMetadata("resourceType", resourceType)
                .addMetadata("resourceId", resourceId)
                .build();
    }

    /**
     * Insufficient balance (business logic error)
     */
    public static GatewayException insufficientBalance(Long accountId, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.INSUFFICIENT_BALANCE)
                .message("Insufficient balance for this transaction")
                .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .requestId(requestId)
                .retryable(false)
                .addMetadata("accountId", accountId)
                .build();
    }

    /**
     * Account inactive/closed
     */
    public static GatewayException accountInactive(Long accountId, String status, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.ACCOUNT_INACTIVE)
                .message(String.format("Account is %s and cannot perform transactions", status))
                .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .requestId(requestId)
                .retryable(false)
                .addMetadata("accountId", accountId)
                .addMetadata("accountStatus", status)
                .build();
    }

    /**
     * Database error
     */
    public static GatewayException databaseError(String message, Throwable cause) {
        return builder()
                .errorCode(GatewayErrorCode.DATABASE_ERROR)
                .message("Database operation failed: " + message)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .cause(cause)
                .retryable(true)
                .build();
    }

    /**
     * Configuration error
     */
    public static GatewayException configurationError(String message) {
        return builder()
                .errorCode(GatewayErrorCode.CONFIGURATION_ERROR)
                .message("Configuration error: " + message)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .retryable(false)
                .build();
    }

    /**
     * Serialization/Deserialization error
     */
    public static GatewayException serializationError(String message, Throwable cause) {
        return builder()
                .errorCode(GatewayErrorCode.SERIALIZATION_ERROR)
                .message("Failed to process data: " + message)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .cause(cause)
                .retryable(false)
                .build();
    }

    /**
     * Authentication error
     */
    public static GatewayException authenticationError(String message) {
        return builder()
                .errorCode(GatewayErrorCode.AUTHENTICATION_ERROR)
                .message("Authentication failed: " + message)
                .httpStatus(HttpStatus.UNAUTHORIZED)
                .retryable(false)
                .build();
    }

    /**
     * Authorization error
     */
    public static GatewayException authorizationError(String message, String sourceApp) {
        return builder()
                .errorCode(GatewayErrorCode.AUTHORIZATION_ERROR)
                .message("Access denied: " + message)
                .httpStatus(HttpStatus.FORBIDDEN)
                .retryable(false)
                .addMetadata("sourceApp", sourceApp)
                .build();
    }

    /**
     * Generic internal error (last resort)
     */
    public static GatewayException internalError(String message, Throwable cause) {
        return builder()
                .errorCode(GatewayErrorCode.INTERNAL_ERROR)
                .message(message)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .cause(cause)
                .retryable(false)
                .build();
    }

    /**
     * Business rule violation
     */
    public static GatewayException businessRuleViolation(String rule, String message, String requestId) {
        return builder()
                .errorCode(GatewayErrorCode.BUSINESS_RULE_VIOLATION)
                .message(message)
                .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .requestId(requestId)
                .retryable(false)
                .addMetadata("violatedRule", rule)
                .build();
    }

    // ============= Builder =============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GatewayErrorCode errorCode;
        private String message;
        private HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        private String requestId;
        private Throwable cause;
        private Map<String, Object> metadata = new HashMap<>();
        private boolean retryable = false;

        public Builder errorCode(GatewayErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder httpStatus(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public GatewayException build() {
            if (errorCode == null) {
                throw new IllegalStateException("ErrorCode is required");
            }
            if (message == null || message.trim().isEmpty()) {
                throw new IllegalStateException("Message is required");
            }
            return new GatewayException(this);
        }
    }

    // ============= Helper Methods =============

    public boolean isClientError() {
        return httpStatus.is4xxClientError();
    }

    public boolean isServerError() {
        return httpStatus.is5xxServerError();
    }

    public String getDetailedMessage() {

        StringBuilder sb = new StringBuilder();
        sb.append(getMessage());

        if (requestId != null) {
            sb.append(" [RequestID: ").append(requestId).append("]");
        }

        if (!metadata.isEmpty()) {
            sb.append(" [Metadata: ").append(metadata).append("]");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "GatewayException{errorCode=%s, httpStatus=%s, message='%s', requestId='%s', retryable=%s, metadata=%s}",
                errorCode, httpStatus, getMessage(), requestId, retryable, metadata
        );
    }
}