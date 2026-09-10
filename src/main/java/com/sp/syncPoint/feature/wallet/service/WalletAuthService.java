package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.common.exception.ActiveSessionExistsException;
import com.sp.syncPoint.common.exception.WalletLockedException;
import com.sp.syncPoint.feature.auth.dto.request.UserRequest;
import com.sp.syncPoint.feature.auth.dto.response.LoginResponse;

/**
 * Wallet-scoped authentication: credential verification, login-attempt
 * lockout, single-active-session enforcement, and account-existence checks.
 * <p>
 * Separated from wallet provisioning ({@link WalletProvisioningService})
 * and PIN management ({@link WalletPinService}) — this is the "can this
 * caller establish a session" concern, consumed only by
 * {@code WalletController}.
 */
public interface WalletAuthService {

    /**
     * Authenticates a user by phone number + password, enforcing login
     * lockout after repeated failures and rejecting the login if the
     * account already has an active session elsewhere.
     *
     * @throws WalletLockedException if the
     *         wallet is currently locked out
     * @throws ActiveSessionExistsException if
     *         the account is already active on another device
     */
    LoginResponse walletLogin(UserRequest request);

    /**
     * @return true if an offline-payment account exists for the given
     *         phone number
     */
    boolean doesAccountExist(String phoneNumber);
}