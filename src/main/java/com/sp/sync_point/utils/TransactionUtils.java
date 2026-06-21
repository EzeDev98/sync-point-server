package com.sp.sync_point.utils;

import com.sp.sync_point.enums.EntryType;
import com.sp.sync_point.enums.TransactionStatus;
import com.sp.sync_point.enums.TransactionType;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.service.impl.UniqueValueGeneratorService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.CURRENCY;

@Component
public class TransactionUtils {

    private final UniqueValueGeneratorService valueGeneratorService;

    public TransactionUtils(UniqueValueGeneratorService valueGeneratorService) {
        this.valueGeneratorService = valueGeneratorService;
    }

    public Transaction createTransaction(TransactionType transactionType, EntryType entryType, String senderWalletId, String senderName, String senderWalletPhoneNumber, BigDecimal senderBalanceBefore, BigDecimal senderBalanceAfter, String receiverWalletId, String receiverName, String receiverWalletPhoneNumber, BigDecimal receiverBalanceBefore, BigDecimal receiverBalanceAfter, BigDecimal amount, String description, TransactionStatus status, String failureReason) {

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(valueGeneratorService.generateTransactionReference());
        transaction.setRequestRef(valueGeneratorService.generateRequestReference());
        transaction.setTransactionType(transactionType);
        transaction.setEntryType(entryType);
        transaction.setCurrency(CURRENCY);
        transaction.setSenderWalletId(senderWalletId);
        transaction.setSenderName(senderName);
        transaction.setSenderWalletPhoneNumber(senderWalletPhoneNumber);
        transaction.setSenderBalanceBefore(senderBalanceBefore);
        transaction.setSenderBalanceAfter(senderBalanceAfter);
        transaction.setReceiverWalletId(receiverWalletId);
        transaction.setReceiverName(receiverName);
        transaction.setReceiverWalletPhoneNumber(receiverWalletPhoneNumber);
        transaction.setReceiverBalanceBefore(receiverBalanceBefore);
        transaction.setReceiverBalanceAfter(receiverBalanceAfter);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setStatus(status);
        transaction.setSyncId(valueGeneratorService.generateSyncId());
        transaction.setSyncedAt(LocalDateTime.now());
        transaction.setCreatedAt(Instant.now());
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setFailureReason(failureReason);

        return transaction;
    }

    public Transaction createTransaction(String requestRef, TransactionType transactionType, EntryType entryType,
                                         String senderWalletId, String senderName, String senderWalletPhoneNumber,
                                         BigDecimal senderBalanceBefore, BigDecimal senderBalanceAfter,
                                         String receiverWalletId, String receiverName, String receiverWalletPhoneNumber,
                                         BigDecimal receiverBalanceBefore, BigDecimal receiverBalanceAfter,
                                         BigDecimal amount, String description, TransactionStatus status, String failureReason) {

        Transaction transaction = new Transaction();

        transaction.setTransactionReference(valueGeneratorService.generateTransactionReference());
        transaction.setRequestRef(requestRef);
        transaction.setTransactionType(transactionType);
        transaction.setEntryType(entryType);
        transaction.setCurrency(CURRENCY);
        transaction.setSenderWalletId(senderWalletId);
        transaction.setSenderName(senderName);
        transaction.setSenderWalletPhoneNumber(senderWalletPhoneNumber);
        transaction.setSenderBalanceBefore(senderBalanceBefore);
        transaction.setSenderBalanceAfter(senderBalanceAfter);
        transaction.setReceiverWalletId(receiverWalletId);
        transaction.setReceiverName(receiverName);
        transaction.setReceiverWalletPhoneNumber(receiverWalletPhoneNumber);
        transaction.setReceiverBalanceBefore(receiverBalanceBefore);
        transaction.setReceiverBalanceAfter(receiverBalanceAfter);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setStatus(status);
        transaction.setSyncId(valueGeneratorService.generateSyncId());
        transaction.setSyncedAt(LocalDateTime.now());
        transaction.setCreatedAt(Instant.now());
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setFailureReason(failureReason);

        return transaction;
    }


}
