package com.sp.sync_point.service.impl;

import com.sp.sync_point.model.SystemWallet;

import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.repository.SystemWalletRepository;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.utils.TransactionUtils;
import jakarta.annotation.PostConstruct;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.*;
import static com.sp.sync_point.enums.EntryType.CREDIT;
import static com.sp.sync_point.enums.TransactionStatus.COMPLETED;
import static com.sp.sync_point.enums.TransactionType.SYSTEM_CAPITALIZATION;

@Component
@RequiredArgsConstructor
public class SystemWalletInitializer {

    private final SystemWalletRepository systemWalletRepo;
    private final TransactionRepository transactionRepo;
    private final UniqueValueGeneratorService valueGeneratorService;

    private final TransactionUtils transactionUtils;

    @Transactional
    @PostConstruct
    public void initialize() {
        if (systemWalletRepo.count() > 0) {
            return;
        }

        String walletId = valueGeneratorService.generateUniqueReferenceId();

        SystemWallet systemWallet = new SystemWallet();

        systemWallet.setInitialFund(SYSTEM_WALLET_AMOUNT);
        systemWallet.setActiveFundedUsers(0L);
        systemWallet.setAvailableBalance(SYSTEM_WALLET_AMOUNT);
        systemWallet.setTotalDisbursed(ZERO);
        systemWallet.setTotalUsersFunded(0L);
        systemWallet.setTotalReclaimed(ZERO);
        systemWallet.setCurrentLiability(ZERO);
        systemWallet.setWalletId(walletId);
        systemWallet.setName(SENDER_NAME);
        systemWallet.setPhoneNumber(SENDER_NUMBER);

        systemWallet.setCreatedAt(LocalDateTime.now());
        systemWallet.setUpdatedAt(LocalDateTime.now());

        Transaction transaction = transactionUtils.createTransaction(SYSTEM_CAPITALIZATION, CREDIT, SENDER_WALLET_ID, SENDER_NAME, SENDER_NUMBER, SYSTEM_WALLET_AMOUNT, ZERO, walletId, SYSTEM_NAME, SYSTEM_PHONE_NUMBER, ZERO, SYSTEM_WALLET_AMOUNT, SYSTEM_WALLET_AMOUNT, INITIAL_TRANSACTION_DESCRIPTION, COMPLETED, null);

        transactionRepo.save(transaction);
        systemWalletRepo.save(systemWallet);
    }
}





