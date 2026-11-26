package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.feature.wallet.dto.request.PinRequest;
import com.sp.syncPoint.feature.wallet.dto.request.SetPinRequest;
import com.sp.syncPoint.common.response.BaseResponse;

public interface WalletPinService {

    BaseResponse setPin(SetPinRequest request);

    BaseResponse changePin(PinRequest pinRequest);

    boolean isWalletPinSet(String walletId);

    boolean verifyPin(String walletId, String unverifiedPin);

    void resetPin(String phoneNumber, String newPin, String confirmNewPin);
}