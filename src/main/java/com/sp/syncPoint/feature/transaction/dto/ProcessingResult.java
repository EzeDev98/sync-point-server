package com.sp.syncPoint.feature.transaction.dto;

import com.sp.syncPoint.feature.transaction.enums.Outcome;
import com.sp.syncPoint.feature.transaction.model.Transaction;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ProcessingResult {

    private final Outcome outcome;
    private final List<Transaction> transactions;
    private final String skippedRef;

    private final SyncTransactionRequest request;

    private final String failureReason;

    private ProcessingResult(Outcome outcome, List<Transaction> transactions, String skippedRef,
                             SyncTransactionRequest request, String failureReason) {
        this.outcome = outcome;
        this.transactions = transactions != null
                ? Collections.unmodifiableList(transactions)
                : Collections.emptyList();
        this.skippedRef = skippedRef;
        this.request = request;
        this.failureReason = failureReason;
    }

    public static ProcessingResult success(Transaction debit, Transaction credit) {
        return new ProcessingResult(Outcome.SUCCESS, List.of(debit, credit), null, null, null);
    }

    public static ProcessingResult failed(Transaction debit, Transaction credit) {
        return new ProcessingResult(Outcome.FAILED, List.of(debit, credit), null, null, null);
    }

    public static ProcessingResult skipped(String transactionRef) {
        return new ProcessingResult(Outcome.SKIPPED, null, transactionRef, null, null);
    }

    public static ProcessingResult rejected(SyncTransactionRequest request, String reason) {
        return new ProcessingResult(Outcome.REJECTED, null, null, request, reason);
    }

    public boolean isSuccess()  { return outcome == Outcome.SUCCESS;  }
    public boolean isFailed()   { return outcome == Outcome.FAILED;   }
    public boolean isSkipped()  { return outcome == Outcome.SKIPPED;  }
    public boolean isRejected() { return outcome == Outcome.REJECTED; }
}