package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.money.MoneyUtils;
import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import org.springframework.stereotype.Component;

import static com.sp.syncPoint.common.constants.GeneralConstants.CURRENCY;
import static com.sp.syncPoint.common.constants.GeneralConstants.MAX_TRANSACTION_AMOUNT;
import static java.math.BigDecimal.ZERO;

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

    /**
     * Transfer-level rules checked after {@link #validateTransaction} and the
     * caller's ownership assertion: receiver presence, positive amount,
     * distinct sender/receiver, supported currency, and reconciliation ref.
     * <p>
     * Moved verbatim from {@code TransactionSyncServiceImpl.validateTransactionRequest}
     * during the phase-2 separation-of-concerns pass. Only the sender-phone and
     * ownership checks stayed behind — the former is already covered by
     * {@link #validateTransaction}, the latter is an authorization concern the
     * service still performs (in the same order as before). No rule or message
     * changed. {@code senderPhone} is guaranteed non-null here by the prior
     * {@link #validateTransaction} call.
     */
    public void validateTransferRules(SyncTransactionRequest request) {
        if (request.getReceiverPhone() == null || request.getReceiverPhone().isBlank()) {
            throw new BadRequestException("Receiver phone number is required");
        }
        if (request.getAmount() == null || MoneyUtils.isLessThanOrEqual(request.getAmount(), ZERO)) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (request.getSenderPhone().equals(request.getReceiverPhone())) {
            throw new BadRequestException("Sender and receiver cannot be the same");
        }
        if (!CURRENCY.equals(request.getCurrency())) {
            throw new BadRequestException("Only NGN is supported. Received: " + request.getCurrency());
        }
        if (request.getRequestRef() == null || request.getRequestRef().isBlank()) {
            throw new BadRequestException("requestRef is required for reconciliation");
        }
    }
}