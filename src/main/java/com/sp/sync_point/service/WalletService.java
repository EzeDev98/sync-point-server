package com.sp.sync_point.service;

import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.UserRequest;
import com.sp.sync_point.model.User;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.LoginResponse;

public interface WalletService {
    void setupWallet(User user);
    LoginResponse walletLogin(UserRequest request );
    BaseResponse changePin(PinRequest pinRequest);
    BaseResponse setPin(SetPinRequest request);
    boolean isWalletPinSet(String walletId);
    boolean verifyPin(String walletId, String unverifiedPin);
    boolean doesAccountExist(String phoneNumber);
}
