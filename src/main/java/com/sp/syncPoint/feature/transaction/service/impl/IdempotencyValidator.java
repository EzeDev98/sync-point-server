package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IdempotencyValidator {

    private final TransactionRepository transactionRepository;

    public IdempotencyValidator(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction> getProcessedTransactions(List<String> transactionRefs) {
        return transactionRepository.findByTransactionReferenceIn(transactionRefs);
    }
}