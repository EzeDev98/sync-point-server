package com.sp.sync_point.service;


import com.sp.sync_point.model.Wallet;

public interface EntityUtilityService {

    Wallet fetchWallet(String walletId);
    Wallet findWalletByPhoneNumber(String walletId);
    boolean existByPhoneNumber(String phoneNumber);
}
