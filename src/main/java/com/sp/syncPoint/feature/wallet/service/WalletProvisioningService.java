package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.common.exception.AlreadyExistException;
import com.sp.syncPoint.common.exception.SystemWalletInsufficientBalanceException;
import com.sp.syncPoint.feature.user.model.User;

/**
 * Provisions a new wallet for a newly-registered user, funding it from the
 * system treasury wallet (system-wallet debit + user-wallet credit +
 * transaction audit trail + funding record, all in one unit of work).
 * <p>
 * Separated from login ({@link WalletAuthService}) and PIN management
 * ({@link WalletPinService}) — provisioning happens exactly once, at
 * registration time, and only {@code UserServiceImpl} needs it.
 */
public interface WalletProvisioningService {

    /**
     * Creates and capitalizes a wallet for {@code user}, debiting the
     * system wallet by the standard funding amount.
     *
     * @throws AlreadyExistException if a wallet
     *         already exists for the user's phone number
     * @throws SystemWalletInsufficientBalanceException
     *         if the system treasury wallet cannot cover the funding amount
     */
    void setupWallet(User user);
}