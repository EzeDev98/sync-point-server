package com.sp.sync_point.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GatewayErrorCode {

    // Client Errors (4xx)
    VALIDATION_ERROR("ERR_VALIDATION", "Request validation failed"),
    MISSING_HEADER("ERR_MISSING_HEADER", "Required header is missing"),
    RESOURCE_NOT_FOUND("ERR_NOT_FOUND", "Requested resource not found"),
    DUPLICATE_REQUEST("ERR_DUPLICATE", "Duplicate request detected"),
    REQUEST_IN_PROGRESS("ERR_IN_PROGRESS", "Request is being processed"),
    AUTHENTICATION_ERROR("ERR_AUTH", "Authentication failed"),
    AUTHORIZATION_ERROR("ERR_AUTHZ", "Access denied"),

    // Business Logic Errors
    INSUFFICIENT_BALANCE("ERR_INSUFFICIENT_BALANCE", "Insufficient account balance"),
    ACCOUNT_INACTIVE("ERR_ACCOUNT_INACTIVE", "Account is not active"),
    BUSINESS_RULE_VIOLATION("ERR_BUSINESS_RULE", "Business rule violation"),

    // Mifos Errors
    MIFOS_CLIENT_ERROR("ERR_MIFOS_CLIENT", "Mifos API client error"),
    MIFOS_SERVER_ERROR("ERR_MIFOS_SERVER", "Mifos API server error"),

    // Infrastructure Errors (5xx)
    NETWORK_ERROR("ERR_NETWORK", "Network connectivity error"),
    TIMEOUT_ERROR("ERR_TIMEOUT", "Operation timed out"),
    CIRCUIT_BREAKER_OPEN("ERR_CIRCUIT_BREAKER", "Service circuit breaker is open"),
    RATE_LIMIT_EXCEEDED("ERR_RATE_LIMIT", "Rate limit exceeded"),
    DATABASE_ERROR("ERR_DATABASE", "Database operation failed"),
    SERIALIZATION_ERROR("ERR_SERIALIZATION", "Data serialization failed"),
    IDEMPOTENCY_ERROR("ERR_IDEMPOTENCY", "Idempotency system error"),
    CONFIGURATION_ERROR("ERR_CONFIG", "Configuration error"),
    INTERNAL_ERROR("ERR_INTERNAL", "Internal server error");

    private final String code;
    private final String description;

    public boolean isRetryable() {
        return this == MIFOS_SERVER_ERROR
                || this == NETWORK_ERROR
                || this == TIMEOUT_ERROR
                || this == DATABASE_ERROR
                || this == RATE_LIMIT_EXCEEDED
                || this == IDEMPOTENCY_ERROR;
    }
}