package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.*;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.wallet.model.SystemWallet;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.model.WalletFundingRecord;
import com.sp.syncPoint.feature.wallet.repository.SystemWalletRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletFundingRecordRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletProvisioningService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.sp.syncPoint.common.constants.GeneralConstants.*;

@Service
@RequiredArgsConstructor
public class WalletProvisioningServiceImpl implements WalletProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(WalletProvisioningServiceImpl.class);

    private final WalletRepository walletRepository;
    private final SystemWalletRepository systemWalletRepo;
    private final WalletFundingRecordRepository walletFundingRecordRepo;
    private final TransactionRepository transactionRepo;
    private final WalletFactory walletFactory;

    @Timed(value = "wallet.setup", description = "Time taken to setup wallet")
    @Retryable(
            value = {DeadlockLoserDataAccessException.class, OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    @Override
    public void setupWallet(User user) {

        log.info("Starting wallet setup for user ID: {}, phone: {}", user.getId(), user.getPhoneNumber());

        try {

            if (user.getId() == null) {
                throw new EmptyFieldException("User ID cannot be null", HttpStatus.BAD_REQUEST);
            }

            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                throw new EmptyFieldException("Phone number cannot be null or empty", HttpStatus.BAD_REQUEST);
            }

            if (!user.getPhoneNumber().matches("^\\+?[0-9]{10,15}$")) {
                throw new InvalidInputException("Invalid phone number format");
            }

            Optional<Wallet> existingWallet = walletRepository.findByPhoneNumber(user.getPhoneNumber());
            if (existingWallet.isPresent()) {
                throw new AlreadyExistException("Wallet already exists" + existingWallet.get().getWalletId());
            }

            Wallet wallet = createNewWallet(FUND_AMOUNT, user);

            log.info("Successfully created wallet with ID: {} for user ID: {}", wallet.getWalletId(), user.getId());
        } catch (Exception e) {
            log.error("Failed to create wallet for user ID: {}, phone: {}", user.getId(), user.getPhoneNumber(), e);
            throw e;
        }
    }

    private Wallet createNewWallet(BigDecimal amount, User user) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be positive");
        }

        log.debug("Creating wallet for user ID: {} with amount: {}", user.getId(), amount);

        SystemWallet systemWallet = systemWalletRepo.findByNameForUpdate(SYSTEM_WALLET_ADMIN)
                .orElseThrow(() -> new WalletNotFoundException("No system wallet found for the provided name " + SYSTEM_WALLET_ADMIN));

        BigDecimal balanceBeforeTransfer = systemWallet.getAvailableBalance();

        if (balanceBeforeTransfer.compareTo(amount) < 0) {
            throw new SystemWalletInsufficientBalanceException(String.format("System wallet insufficient balance. Available: %s, Required: %s", balanceBeforeTransfer, amount));
        }

        systemWallet.setAvailableBalance(balanceBeforeTransfer.subtract(amount));

        BigDecimal balanceAfterTransfer = systemWallet.getAvailableBalance();

        systemWallet.setCurrentLiability(systemWallet.getCurrentLiability().add(amount));
        systemWallet.setTotalDisbursed(systemWallet.getTotalDisbursed().add(amount));
        systemWallet.setActiveFundedUsers(systemWallet.getActiveFundedUsers() + 1);
        systemWallet.setTotalUsersFunded(systemWallet.getTotalUsersFunded() + 1);
        systemWallet.setUpdatedAt(LocalDateTime.now());

        Wallet wallet = walletFactory.newWallet(user, amount);
        Transaction transaction = walletFactory.newCapitalizationTransaction(systemWallet, wallet, user, balanceBeforeTransfer, balanceAfterTransfer, ZERO, wallet.getMirrorBalance(), amount);
        WalletFundingRecord walletFundingRecord = walletFactory.newFundingRecord(user.getId(), wallet.getWalletId(), amount);

        walletRepository.save(wallet);
        transactionRepo.save(transaction);
        walletFundingRecordRepo.save(walletFundingRecord);
        systemWalletRepo.save(systemWallet);
        return wallet;
    }
}