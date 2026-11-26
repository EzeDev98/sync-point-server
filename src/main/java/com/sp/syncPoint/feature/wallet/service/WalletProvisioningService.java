package com.sp.syncPoint.feature.wallet.service;
import com.sp.syncPoint.feature.user.model.User;

public interface WalletProvisioningService {
    void setupWallet(User user);
}