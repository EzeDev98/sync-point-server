package com.sp.syncPoint.feature.transaction.service;

import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.common.response.BaseResponse;

import java.util.List;

/**
 * Accepts batches of offline-queued P2P transfers from client devices and
 * reconciles them against server state.
 * <p>
 * This is intentionally a separate contract from wallet funding
 * ({@link WalletFundingService}) and transaction reads
 * ({@link TransactionQueryService}) — batch sync has very different
 * concurrency, idempotency, and failure-handling requirements from either
 * of those, and callers of one rarely need the others.
 */
public interface TransactionSyncService {

    /**
     * Process a batch of offline transactions from a client device.
     * Already-processed transaction references are skipped idempotently;
     * new ones are applied as sender/receiver DEBIT+CREDIT entry pairs.
     *
     * @param requests the batch of offline-queued transfer requests
     * @return a summary of completed, failed, and already-processed transfers
     */
    BaseResponse syncTransactions(List<SyncTransactionRequest> requests);
}