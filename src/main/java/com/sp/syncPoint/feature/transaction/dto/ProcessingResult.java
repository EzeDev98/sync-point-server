package com.sp.syncPoint.feature.transaction.dto;

import com.sp.syncPoint.feature.transaction.enums.Outcome;
import com.sp.syncPoint.feature.transaction.model.Transaction;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Result of processing a single transfer — used internally to communicate
 * outcome without throwing for non-error cases (skipped, insufficient balance).
 */
@Getter
public class ProcessingResult {

    private final Outcome outcome;
    private final List<Transaction> transactions;
    private final String skippedRef;

    private ProcessingResult(Outcome outcome, List<Transaction> transactions,
                             String skippedRef) {
        this.outcome = outcome;
        this.transactions = transactions != null
                ? Collections.unmodifiableList(transactions)
                : Collections.emptyList();
        this.skippedRef = skippedRef;
    }

    public static ProcessingResult success(Transaction debit, Transaction credit) {
        return new ProcessingResult(Outcome.SUCCESS, List.of(debit, credit), null);
    }

    public static ProcessingResult failed(Transaction debit, Transaction credit) {
        return new ProcessingResult(Outcome.FAILED, List.of(debit, credit), null);
    }

    public static ProcessingResult skipped(String transactionRef) {
        return new ProcessingResult(Outcome.SKIPPED, null, transactionRef);
    }

    public boolean isSuccess() { return outcome == Outcome.SUCCESS; }
    public boolean isFailed()  { return outcome == Outcome.FAILED;  }
    public boolean isSkipped() { return outcome == Outcome.SKIPPED; }
}