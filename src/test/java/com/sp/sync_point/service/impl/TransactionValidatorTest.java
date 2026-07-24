package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionValidatorTest {

    private final TransactionValidator validator = new TransactionValidator();

    private SyncTransactionRequest validRequest() {
        SyncTransactionRequest request = new SyncTransactionRequest();
        request.setTransactionRef("TXN-1");
        request.setRequestRef("REQ-1");
        request.setType("P2P_TRANSFER");
        request.setSenderPhone("08010000001");
        request.setAmount(new BigDecimal("500.00"));
        request.setCreatedAt(Instant.now());
        return request;
    }

    @Test
    void validateTransaction_validRequest_doesNotThrow() {
        assertThatCode(() -> validator.validateTransaction(validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateTransaction_nullRequest_throws() {
        assertThatThrownBy(() -> validator.validateTransaction(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction cannot be null or empty");
    }

    @Test
    void validateTransaction_nullAmount_throws() {
        SyncTransactionRequest request = validRequest();
        request.setAmount(null);

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction amount cannot be null");
    }

    @Test
    void validateTransaction_negativeAmount_throws() {
        SyncTransactionRequest request = validRequest();
        request.setAmount(new BigDecimal("-10.00"));

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction amount must be positive");
    }

    @Test
    void validateTransaction_amountExceedsMaximum_throws() {
        SyncTransactionRequest request = validRequest();
        request.setAmount(new BigDecimal("10000000.01"));

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds maximum allowed");
    }

    @Test
    void validateTransaction_blankType_throws() {
        SyncTransactionRequest request = validRequest();
        request.setType(" ");

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction type cannot be null or empty");
    }

    @Test
    void validateTransaction_blankSenderPhone_throws() {
        SyncTransactionRequest request = validRequest();
        request.setSenderPhone(" ");

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User wallet phone number cannot be null or empty");
    }

    @Test
    void validateTransaction_blankTransactionRef_throws() {
        SyncTransactionRequest request = validRequest();
        request.setTransactionRef(" ");

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction reference cannot be null or empty");
    }

    @Test
    void validateTransaction_nullCreatedAt_throws() {
        SyncTransactionRequest request = validRequest();
        request.setCreatedAt(null);

        assertThatThrownBy(() -> validator.validateTransaction(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction date cannot be null or empty");
    }
}