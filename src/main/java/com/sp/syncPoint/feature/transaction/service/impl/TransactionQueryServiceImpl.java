package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;

import com.sp.syncPoint.common.exception.EmptyFieldException;
import com.sp.syncPoint.common.exception.WalletNotFoundException;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.transaction.service.TransactionQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionQueryServiceImpl implements TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final DtoMapper mapper;
    private final CurrentUserService currentUserService;

    @Override
    public String deleteTransactions() {
        try {
            long transactionCounts = transactionRepository.count();
            transactionRepository.deleteAll();

            if (transactionCounts > 0) {
                return "All transactions deleted successfully";
            } else {
                return "No transactions found to delete";
            }
        } catch (Exception e) {
            throw new RuntimeException("Error deleting transactions", e);
        }
    }

    @Override
    public List<TransactionResponse> getAllTransactions() {

        List<Transaction> transactions = transactionRepository.findAll();
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getTransactionsByWmUniqueId(String userWmUniqueId, String receiverWmUniqueId) {

        if (userWmUniqueId == null || receiverWmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        boolean ownsSender = walletRepository.findByWalletId(userWmUniqueId)
                .map(w -> currentUserService.ownsPhoneNumber(w.getPhoneNumber()))
                .orElse(false);
        boolean ownsReceiver = walletRepository.findByWalletId(receiverWmUniqueId)
                .map(w -> currentUserService.ownsPhoneNumber(w.getPhoneNumber()))
                .orElse(false);

        if (!ownsSender && !ownsReceiver) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }

        List<Transaction> transactions = transactionRepository.findBySenderWalletIdOrReceiverWalletId(userWmUniqueId, receiverWmUniqueId);
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {} and receiverWmUniqueId: {}", userWmUniqueId, receiverWmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getTransactionsByWmUniqueId(String wmUniqueId) {

        if (wmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        Wallet wallet = walletRepository.findByWalletId(wmUniqueId)
                .orElseThrow(() -> new WalletNotFoundException("No wallet found for the ID provided"));
        currentUserService.assertOwnsPhoneNumber(wallet.getPhoneNumber());

        List<Transaction> transactions = transactionRepository.findTransactionByWalletId(wmUniqueId);
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {}", wmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getLatestTransactionsByWmUniqueId(String wmUniqueId) {

        if (wmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        Wallet wallet = walletRepository.findByWalletId(wmUniqueId)
                .orElseThrow(() -> new WalletNotFoundException("No wallet found for the ID provided"));
        currentUserService.assertOwnsPhoneNumber(wallet.getPhoneNumber());

        List<Transaction> transactions = transactionRepository.findLatestTransactionByWalletId(wmUniqueId, PageRequest.of(0, 10));
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {}", wmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public BaseResponse saveMirrorBalance(String walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByWalletId(walletId).orElseThrow(() -> new WalletNotFoundException("No wallet found for the ID provided"));
        currentUserService.assertOwnsPhoneNumber(wallet.getPhoneNumber());
        wallet.setMirrorBalance(amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        return new BaseResponse(HttpServletResponse.SC_OK, "Mirror balance persisted successfully");
    }
}