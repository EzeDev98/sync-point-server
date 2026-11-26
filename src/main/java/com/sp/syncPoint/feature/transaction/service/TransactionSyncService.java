package com.sp.syncPoint.feature.transaction.service;

import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.common.response.BaseResponse;

import java.util.List;

public interface TransactionSyncService {
    BaseResponse syncTransactions(List<SyncTransactionRequest> requests);
}