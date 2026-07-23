package com.sp.sync_point.service;

import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.response.BaseResponse;

/**
 * Full lifecycle of a wallet's transaction PIN: initial set, authenticated
 * change, OTP-verified reset, and verification. Consumed by
 * {@code WalletController} (set/change/verify/isSet) and
 * {@code PinResetServiceImpl} (resetPin, as the final step of the
 * OTP-verified reset flow).
 */
public interface WalletPinService {

    BaseResponse setPin(SetPinRequest request);

    BaseResponse changePin(PinRequest pinRequest);

    boolean isWalletPinSet(String walletId);

    boolean verifyPin(String walletId, String unverifiedPin);

    /**
     * Overwrites a wallet's PIN as part of the OTP-verified PIN-reset flow
     * (see PinResetServiceImpl). Unlike setPin, this does not require the
     * PIN to be unset first, and unlike changePin, it does not require the
     * old PIN — OTP verification is the proof of ownership instead.
     */
    void resetPin(String phoneNumber, String newPin, String confirmNewPin);
}