package com.sp.syncPoint.feature.transaction.mapper;

import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.transaction.enums.EntryType;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.enums.TransactionType;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

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

    public Transaction buildEntry(SyncTransactionRequest request, EntryType entryType, TransactionStatus status, String failureReason) {
        Transaction transaction = mapToTransaction(request);
        transaction.setEntryType(entryType);
        transaction.setTransactionType(TransactionType.P2P_TRANSFER);
        transaction.setStatus(status);
        if (failureReason != null) {
            transaction.setFailureReason(failureReason);
        }
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