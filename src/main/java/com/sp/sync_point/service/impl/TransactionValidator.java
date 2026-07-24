package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.utils.MoneyUtils;
import org.springframework.stereotype.Component;

import static com.sp.sync_point.constants.GeneralConstants.MAX_TRANSACTION_AMOUNT;

/**
 * Validates offline-queued P2P transfer requests, ahead of the
 * ownership/idempotency/locking checks in
 * {@code TransactionSyncServiceImpl.validateTransactionRequest}.
 * <p>
 * Extracted from the original {@code ValidateService} grab-bag as part of
 * the SOLID refactor — used only by {@code TransactionSyncServiceImpl}.
 * No rule changed during the move.
 */
@Component
public class TransactionValidator {

    public void validateTransaction(SyncTransactionRequest transactionRequest) {

        if (transactionRequest == null) {
            throw new BadRequestException("Transaction cannot be null or empty");
        }

        if (transactionRequest.getAmount() == null) {
            throw new BadRequestException("Transaction amount cannot be null");
        }

        if (!MoneyUtils.isPositive(transactionRequest.getAmount())) {
            throw new BadRequestException("Transaction amount must be positive");
        }

        if (MoneyUtils.isGreaterThan(transactionRequest.getAmount(), MAX_TRANSACTION_AMOUNT)) {
            throw new BadRequestException("Transaction amount exceeds maximum allowed: " + MAX_TRANSACTION_AMOUNT);
        }

        if (transactionRequest.getType() == null || transactionRequest.getType().trim().isEmpty()) {
            throw new BadRequestException("Transaction type cannot be null or empty");
        }

        if (transactionRequest.getSenderPhone() == null || transactionRequest.getSenderPhone().trim().isEmpty()) {
            throw new BadRequestException("User wallet phone number cannot be null or empty");
        }

        if (transactionRequest.getTransactionRef() == null || transactionRequest.getTransactionRef().trim().isEmpty()) {
            throw new BadRequestException("Transaction reference cannot be null or empty");
        }

        if (transactionRequest.getCreatedAt() == null) {
            throw new BadRequestException("Transaction date cannot be null or empty");
        }
    }
}