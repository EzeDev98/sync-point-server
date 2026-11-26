package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.feature.auth.dto.request.UserRequest;
import com.sp.syncPoint.feature.auth.dto.response.LoginResponse;

public interface WalletAuthService {

    LoginResponse walletLogin(UserRequest request);

    boolean doesAccountExist(String phoneNumber);
}