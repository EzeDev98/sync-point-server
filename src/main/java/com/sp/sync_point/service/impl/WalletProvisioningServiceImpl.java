package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.AlreadyExistException;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InvalidInputException;
import com.sp.sync_point.exception.SystemWalletInsufficientBalanceException;
import com.sp.sync_point.exception.WalletNotFoundException;
import com.sp.sync_point.model.SystemWallet;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.User;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.model.WalletFundingRecord;
import com.sp.sync_point.repository.SystemWalletRepository;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletFundingRecordRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.service.WalletProvisioningService;
import com.sp.sync_point.utils.TransactionUtils;
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

import static com.sp.sync_point.constants.GeneralConstants.FUND_AMOUNT;
import static com.sp.sync_point.constants.GeneralConstants.SYSTEM_WALLET_ID;
import static com.sp.sync_point.constants.GeneralConstants.ZERO;
import static com.sp.sync_point.enums.EntryType.CREDIT;
import static com.sp.sync_point.enums.TransactionStatus.COMPLETED;
import static com.sp.sync_point.enums.TransactionType.WALLET_CAPITALIZATION;

/**
 * Provisions and capitalizes a new wallet from the system treasury wallet.
 * <p>
 * Extracted from the original {@code WalletServiceImpl} god class as part
 * of the SOLID refactor — this class owns exactly one responsibility:
 * "create a funded wallet for a newly registered user". Login lives in
 * {@link WalletAuthServiceImpl}, PIN management in
 * {@link WalletPinServiceImpl}; no logic in any of the three changed
 * during the split.
 */
@Service
@RequiredArgsConstructor
public class WalletProvisioningServiceImpl implements WalletProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(WalletProvisioningServiceImpl.class);

    private final WalletRepository walletRepository;
    private final UniqueValueGeneratorService uniqueValueService;
    private final SystemWalletRepository systemWalletRepo;
    private final WalletFundingRecordRepository walletFundingRecordRepo;
    private final TransactionUtils transactionUtils;
    private final TransactionRepository transactionRepo;

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

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private Wallet createNewWallet(BigDecimal amount, User user) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be positive");
        }

        log.debug("Creating wallet for user ID: {} with amount: {}", user.getId(), amount);

        SystemWallet systemWallet = systemWalletRepo.findByIdForUpdate(SYSTEM_WALLET_ID)
                .orElseThrow(() -> new WalletNotFoundException("No system wallet found for the provided Id" + SYSTEM_WALLET_ID));

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

        Wallet wallet = createWallet(user, amount);
        Transaction transaction = createTransaction(systemWallet, wallet, user, balanceBeforeTransfer, balanceAfterTransfer, ZERO, wallet.getMirrorBalance(), amount);
        WalletFundingRecord walletFundingRecord = walletFundingRecord(user.getId(), wallet.getWalletId(), amount);

        walletRepository.save(wallet);
        transactionRepo.save(transaction);
        walletFundingRecordRepo.save(walletFundingRecord);
        systemWalletRepo.save(systemWallet);
        return wallet;
    }

    private Wallet createWallet(User user, BigDecimal amount) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(uniqueValueService.generateWalletId());
        wallet.setPhoneNumber(user.getPhoneNumber());
        wallet.setUser(user);
        wallet.setStatus(true);
        wallet.setMirrorBalance(amount);
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        return wallet;
    }

    private Transaction createTransaction(SystemWallet systemWallet, Wallet wallet, User user, BigDecimal balanceBeforeTransfer, BigDecimal balanceAfterTransfer, BigDecimal receiverBalanceBeforeTransfer, BigDecimal receiverBalanceAfterTransfer, BigDecimal amount) {
        String description = String.format("Wallet capitalization for user: %s (ID: %d)", user.getUsername(), user.getId());
        return transactionUtils.createTransaction(WALLET_CAPITALIZATION, CREDIT, systemWallet.getWalletId(), systemWallet.getName(), systemWallet.getPhoneNumber(), balanceBeforeTransfer, balanceAfterTransfer, wallet.getWalletId(), user.getUsername(), wallet.getPhoneNumber(), receiverBalanceBeforeTransfer, receiverBalanceAfterTransfer, amount, description, COMPLETED, null);
    }

    private WalletFundingRecord walletFundingRecord(Long userId, String walletId, BigDecimal amount) {
        WalletFundingRecord walletFunding = new WalletFundingRecord();

        walletFunding.setUserId(userId);
        walletFunding.setWalletId(walletId);
        walletFunding.setAmount(amount);
        walletFunding.setReclaimed(false);
        walletFunding.setFundedAt(LocalDateTime.now());
        walletFunding.setReclaimedAt(null);

        return walletFunding;
    }
}