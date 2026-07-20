package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.enums.TransactionStatus;
import com.sp.sync_point.enums.TransactionType;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.response.TransactionResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

    /**
     * Map a SyncTransactionRequest to a Transaction entity skeleton.
     *
     * entryType is intentionally NOT set here — the service sets it explicitly
     * per entry (DEBIT for sender, CREDIT for receiver) after calling this method.
     *
     * Fix issue #22: senderName and receiverName are now populated from the DTO
     * (they were always on the entity but the DTO fields didn't exist before).
     *
     * Fix issue #9: createdAt is now Instant — timezone-safe.
     */
    public Transaction mapToTransaction(SyncTransactionRequest request) {
        Transaction transaction = new Transaction();

        transaction.setTransactionReference(request.getTransactionRef());
        transaction.setRequestRef(request.getRequestRef());
        transaction.setTransactionType(TransactionType.P2P_TRANSFER);
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());

        transaction.setCreatedAt(request.getCreatedAt() != null ? request.getCreatedAt() : Instant.now());
        transaction.setStatus(TransactionStatus.PENDING);

        transaction.setSenderWalletId(request.getSenderWalletId());
        transaction.setSenderWalletPhoneNumber(request.getSenderPhone());
        transaction.setSenderName(request.getSenderName());

        transaction.setReceiverWalletId(request.getReceiverWalletId());
        transaction.setReceiverWalletPhoneNumber(request.getReceiverPhone());
        transaction.setReceiverName(request.getReceiverName());

        return transaction;
    }

    public List<TransactionResponse> mapTransactionToResponses(List<Transaction> transactions) {
        return transactions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionReference(transaction.getTransactionReference());
        response.setRequestRef(transaction.getRequestRef());
        response.setTransactionType(transaction.getTransactionType().name());

        response.setEntryType(transaction.getEntryType().name());

        response.setCurrency(transaction.getCurrency());
        response.setAmount(transaction.getAmount());
        response.setDescription(transaction.getDescription());
        response.setStatus(transaction.getStatus().name());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());

        response.setSenderWalletId(transaction.getSenderWalletId());
        response.setSenderName(transaction.getSenderName());
        response.setSenderWalletPhoneNumber(transaction.getSenderWalletPhoneNumber());
        response.setSenderBalanceBeforeTransfer(transaction.getSenderBalanceBefore());
        response.setSenderBalanceAfterTransfer(transaction.getSenderBalanceAfter());

        response.setReceiverWalletId(transaction.getReceiverWalletId());
        response.setReceiverName(transaction.getReceiverName());
        response.setReceiverPhoneNumber(transaction.getReceiverWalletPhoneNumber());
        response.setReceiverBalanceBeforeTransfer(transaction.getReceiverBalanceBefore());
        response.setReceiverBalanceAfterTransfer(transaction.getReceiverBalanceAfter());

        return response;
    }
}