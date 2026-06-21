package com.sp.sync_point.response;


import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.Wallet;

import java.util.List;

public class ProcessTransactionResult {
    public final Wallet wallet;
    public final boolean updated;
    public final List<Transaction> skippedTransactions;

    public ProcessTransactionResult(Wallet wallet, boolean updated, List<Transaction> skippedTransactions) {
        this.wallet = wallet;
        this.updated = updated;
        this.skippedTransactions = skippedTransactions;
    }
}
