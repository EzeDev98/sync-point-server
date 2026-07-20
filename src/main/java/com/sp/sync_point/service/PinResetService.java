package com.sp.sync_point.service;

import com.sp.sync_point.dto.CompletePinResetRequest;
import com.sp.sync_point.dto.PinResetRequestDto;
import com.sp.sync_point.dto.VerifyPinOtpRequest;
import com.sp.sync_point.response.PinResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;

/**
 * Mirrors PasswordResetService's shape. Key differences, since the caller
 * already holds a valid session here (they're only locked out of their
 * PIN, not their account):
 *  - endpoints are authenticated (see WebSecurityConfig — no permitAll
 *    entries were added for these, unlike password reset)
 *  - no user-enumeration protection is needed, since the phone number is
 *    already known from the caller's own session
 *  - completeReset updates Wallet.pin via WalletService.resetPin, not
 *    Keycloak, and does NOT invalidate sessions (the account/session is
 *    still valid — only the PIN and its derived local encryption key
 *    needed replacing)
 */
public interface PinResetService {

    PinResetResponse requestOtp(PinResetRequestDto request);

    VerifyOtpResponse verifyOtp(VerifyPinOtpRequest request);

    PinResetResponse completeReset(CompletePinResetRequest request);
}