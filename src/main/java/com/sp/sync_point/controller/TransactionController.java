package com.sp.sync_point.controller;

import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.dto.WalletTransferRequest;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;
import com.sp.sync_point.response.WalletFundingResponse;
import com.sp.sync_point.service.TransactionService;
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

    private final TransactionService transactionService;

    @Operation(method = "POST", summary = "Sync transaction", description = "This endpoint is in charge of transactions syncing in the offline payment.")
    @PostMapping("/sync/transactions")
    public BaseResponse syncTransactions(@RequestBody List<SyncTransactionRequest> transactions) {
        return transactionService.syncTransactions(transactions);
    }

    @Operation(method = "GET", summary = "Get all transaction", description = "This endpoint allows for getting all transactions from the offline payment database.")
    @GetMapping("/transactions/find/all")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        List<TransactionResponse> transactionResponses = transactionService.getAllTransactions();
        return ResponseEntity.ok(transactionResponses);
    }

    @Operation(method = "GET", summary = "Fetch transactions by user's WM ID", description = "Fetches all transactions where the user is either sender or receiver")
    @GetMapping("/transactions/{wmUniqueId}")
    public List<TransactionResponse> getTransactionsByWmUniqueId(@PathVariable String wmUniqueId) {
        return transactionService.getTransactionsByWmUniqueId(wmUniqueId);
    }

    @Operation(method = "GET", summary = "Fetch transactions between two specific parties", description = "Fetches transactions where first user is sender and second is receiver, or vice versa")
    @GetMapping("/transactions")
    public List<TransactionResponse> fetchSenderAndReceiverTransactionsByWmUniqueId(@RequestParam String senderWmUniqueId, @RequestParam String receiverWmUniqueId) {
        return transactionService.getTransactionsByWmUniqueId(senderWmUniqueId, receiverWmUniqueId);
    }

    @Operation(method = "GET", summary = "Fetch latest transactions by user's WM ID", description = "Fetches all latest transactions where the user is either sender or receiver")
    @GetMapping("/transactions/latest/{wmUniqueId}")
    public List<TransactionResponse> fetchLatestTransactionsByWmUniqueId(@PathVariable String wmUniqueId) {
        return transactionService.getLatestTransactionsByWmUniqueId(wmUniqueId);
    }

    @Operation(method = "DELETE", summary = "Delete all transactions", description = "This endpoint allows for the deletion of all transactions in the offline payment database.")
    @DeleteMapping("/delete/all/transactions")
    public String deleteAllTransactions() {
        return transactionService.deleteTransactions();
    }


    @Operation(method = "POST", summary = "Save mirror balance", description = "This endpoint allows for the Saving of mirror balance in the offline payment database.")
    @PostMapping("/save/mirror-balance")
    public ResponseEntity<BaseResponse> saveMirrorBalance(@RequestParam String walletId, @RequestParam BigDecimal amount) {
        BaseResponse response = transactionService.saveMirrorBalance(walletId, amount);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "POST", summary = "Fund offline wallet", description = "This endpoint allows a user to fund their offline wallets.")
    @PostMapping("/wallet/funding/offline")
    public ResponseEntity<WalletFundingResponse> fundOfflineWallet(@RequestBody WalletTransferRequest request) {
        WalletFundingResponse response = transactionService.fundOfflineWallet(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "POST", summary = "Transfer to online account", description = "This endpoint allows a user to transfer to their online account.")
    @PostMapping("/wallet/transfer/online")
    public ResponseEntity<WalletFundingResponse> transferToOnlineAccount(@RequestBody WalletTransferRequest request) {
        WalletFundingResponse response = transactionService.transferToOnlineAccount(request);
        return ResponseEntity.ok(response);
    }
}