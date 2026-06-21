package com.sp.sync_point.service;

import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.dto.WalletTransferRequest;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;
import com.sp.sync_point.response.WalletFundingResponse;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {

    BaseResponse syncTransactions(List<SyncTransactionRequest> transactions);

    List<TransactionResponse> getAllTransactions();

    List<TransactionResponse> getTransactionsByWmUniqueId(String wmUniqueId);

    String deleteTransactions();

    List<TransactionResponse> getTransactionsByWmUniqueId(String userWmUniqueId, String receiverWmUniqueId);

    List<TransactionResponse> getLatestTransactionsByWmUniqueId(String wmUniqueId);

    BaseResponse saveMirrorBalance(String walletId, BigDecimal amount);

    WalletFundingResponse fundOfflineWallet(WalletTransferRequest request);

    WalletFundingResponse transferToOnlineAccount(WalletTransferRequest request);
}