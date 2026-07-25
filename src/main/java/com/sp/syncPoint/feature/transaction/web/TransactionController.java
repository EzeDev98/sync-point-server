package com.sp.syncPoint.feature.transaction.web;

import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.wallet.dto.WalletTransferRequest;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.wallet.dto.WalletFundingResponse;
import com.sp.syncPoint.feature.transaction.service.TransactionQueryService;
import com.sp.syncPoint.feature.transaction.service.TransactionSyncService;
import com.sp.syncPoint.feature.wallet.service.WalletFundingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("${app.title}")
@Tag(name = "Transaction API", description = "Resource for managing transactions")
public class TransactionController {

    private final TransactionSyncService transactionSyncService;
    private final WalletFundingService walletFundingService;
    private final TransactionQueryService transactionQueryService;

    @Operation(method = "POST", summary = "Sync transaction", description = "This endpoint is in charge of transactions syncing in the offline payment.")
    @PostMapping("/wallet/sync-transaction")
    public ResponseEntity<BaseResponse> syncTransactions(@RequestBody List<SyncTransactionRequest> transactions) {
        BaseResponse response = transactionSyncService.syncTransactions(transactions);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @Operation(method = "GET", summary = "Get all transaction", description = "This endpoint allows for getting all transactions from the offline payment database.")
    @GetMapping("/transactions/all")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        List<TransactionResponse> transactionResponses = transactionQueryService.getAllTransactions();
        return ResponseEntity.ok(transactionResponses);
    }

    @Operation(method = "GET", summary = "Fetch transactions by user's WM ID", description = "Fetches all transactions where the user is either sender or receiver")
    @GetMapping("/transactions/{wmUniqueId}")
    public List<TransactionResponse> getTransactionsByWmUniqueId(@PathVariable String wmUniqueId) {
        return transactionQueryService.getTransactionsByWmUniqueId(wmUniqueId);
    }

    @Operation(method = "GET", summary = "Fetch transactions between two specific parties", description = "Fetches transactions where first user is sender and second is receiver, or vice versa")
    @GetMapping("/transactions")
    public List<TransactionResponse> fetchSenderAndReceiverTransactionsByWmUniqueId(@RequestParam String senderWmUniqueId, @RequestParam String receiverWmUniqueId) {
        return transactionQueryService.getTransactionsByWmUniqueId(senderWmUniqueId, receiverWmUniqueId);
    }

    @Operation(method = "GET", summary = "Fetch latest transactions by user's WM ID", description = "Fetches all latest transactions where the user is either sender or receiver")
    @GetMapping("/transactions/latest/{wmUniqueId}")
    public List<TransactionResponse> fetchLatestTransactionsByWmUniqueId(@PathVariable String wmUniqueId) {
        return transactionQueryService.getLatestTransactionsByWmUniqueId(wmUniqueId);
    }

    @Operation(method = "DELETE", summary = "Delete all transactions", description = "This endpoint allows for the deletion of all transactions in the offline payment database.")
    @PreAuthorize("@rolesEvaluator.deleteTransactions(authentication)")
    @DeleteMapping("/delete/all/transactions")
    public ResponseEntity<String> deleteAllTransactions() {
        return ResponseEntity.ok(transactionQueryService.deleteTransactions());
    }


    @Operation(method = "POST", summary = "Save mirror balance", description = "This endpoint allows for the Saving of mirror balance in the offline payment database.")
    @PostMapping("/save/mirror-balance")
    public ResponseEntity<BaseResponse> saveMirrorBalance(@RequestParam String walletId, @RequestParam BigDecimal amount) {
        BaseResponse response = transactionQueryService.saveMirrorBalance(walletId, amount);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @Operation(method = "POST", summary = "Fund offline wallet", description = "This endpoint allows a user to fund their offline wallets.")
    @PostMapping("/wallet/funding/offline")
    public ResponseEntity<WalletFundingResponse> fundOfflineWallet(@RequestBody WalletTransferRequest request) {
        WalletFundingResponse response = walletFundingService.fundOfflineWallet(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "POST", summary = "Transfer to online account", description = "This endpoint allows a user to transfer to their online account.")
    @PostMapping("/wallet/transfer/online")
    public ResponseEntity<WalletFundingResponse> transferToOnlineAccount(@RequestBody WalletTransferRequest request) {
        WalletFundingResponse response = walletFundingService.transferToOnlineAccount(request);
        return ResponseEntity.ok(response);
    }
}