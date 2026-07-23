package com.sp.sync_point.service;

import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read access to transaction history, plus mirror-balance persistence
 * (a write, but one that belongs conceptually with the query side — it
 * persists the client's locally-computed offline balance rather than
 * moving funds).
 */
public interface TransactionQueryService {

    List<TransactionResponse> getAllTransactions();

    List<TransactionResponse> getTransactionsByWmUniqueId(String wmUniqueId);

    List<TransactionResponse> getTransactionsByWmUniqueId(String userWmUniqueId, String receiverWmUniqueId);

    List<TransactionResponse> getLatestTransactionsByWmUniqueId(String wmUniqueId);

    String deleteTransactions();

    BaseResponse saveMirrorBalance(String walletId, BigDecimal amount);
}