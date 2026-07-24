package com.sp.sync_point.service.impl;

import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.repository.TransactionRepository;
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