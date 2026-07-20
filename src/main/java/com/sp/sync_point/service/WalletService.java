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

    /**
     * Overwrites a wallet's PIN as part of the OTP-verified PIN-reset flow
     * (see PinResetServiceImpl). Unlike setPin, this does not require the
     * PIN to be unset first, and unlike changePin, it does not require the
     * old PIN — OTP verification is the proof of ownership instead.
     */
    void resetPin(String phoneNumber, String newPin, String confirmNewPin);
}
