package com.sp.syncPoint.common.support;


import com.sp.syncPoint.feature.wallet.model.Wallet;

public interface EntityUtilityService {

    Wallet fetchWallet(String walletId);
    boolean existByPhoneNumber(String phoneNumber);
}
