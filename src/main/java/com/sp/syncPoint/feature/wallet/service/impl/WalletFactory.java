package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.util.UniqueValueGeneratorService;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.util.TransactionUtils;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.wallet.model.SystemWallet;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.model.WalletFundingRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.sp.syncPoint.feature.transaction.enums.EntryType.CREDIT;
import static com.sp.syncPoint.feature.transaction.enums.TransactionStatus.COMPLETED;
import static com.sp.syncPoint.feature.transaction.enums.TransactionType.WALLET_CAPITALIZATION;

@Component
@RequiredArgsConstructor
public class WalletFactory {

    private final UniqueValueGeneratorService uniqueValueService;
    private final TransactionUtils transactionUtils;

    public Wallet newWallet(User user, BigDecimal amount) {
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

    public Transaction newCapitalizationTransaction(SystemWallet systemWallet, Wallet wallet, User user,
                                                    BigDecimal balanceBeforeTransfer, BigDecimal balanceAfterTransfer,
                                                    BigDecimal receiverBalanceBeforeTransfer, BigDecimal receiverBalanceAfterTransfer,
                                                    BigDecimal amount) {
        String description = String.format("Wallet capitalization for user: %s (ID: %d)", user.getUsername(), user.getId());
        return transactionUtils.createTransaction(WALLET_CAPITALIZATION, CREDIT, systemWallet.getWalletId(), systemWallet.getName(), systemWallet.getPhoneNumber(), balanceBeforeTransfer, balanceAfterTransfer, wallet.getWalletId(), user.getUsername(), wallet.getPhoneNumber(), receiverBalanceBeforeTransfer, receiverBalanceAfterTransfer, amount, description, COMPLETED, null);
    }

    public WalletFundingRecord newFundingRecord(Long userId, String walletId, BigDecimal amount) {
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