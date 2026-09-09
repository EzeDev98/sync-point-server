package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.feature.wallet.dto.request.WalletTransferRequest;
import com.sp.syncPoint.feature.wallet.dto.response.WalletFundingResponse;

/**
 * Moves funds between a user's online mirror balance and their offline
 * spendable wallet. Both operations are PIN-authenticated, idempotent on
 * {@code requestRef}, and scoped to the wallet owned by the currently
 * authenticated user.
 */
public interface WalletFundingService {

    /**
     * Moves funds from the user's online mirror balance into their offline
     * wallet, subject to the wallet's configured offline limit.
     *
     * @param request PIN-authenticated funding request
     * @return the funding outcome, including the transaction reference
     */
    WalletFundingResponse fundOfflineWallet(WalletTransferRequest request);

    /**
     * Moves funds from the user's offline wallet back into their online
     * mirror balance.
     *
     * @param request PIN-authenticated transfer request
     * @return the transfer outcome, including the transaction reference
     */
    WalletFundingResponse transferToOnlineAccount(WalletTransferRequest request);
}