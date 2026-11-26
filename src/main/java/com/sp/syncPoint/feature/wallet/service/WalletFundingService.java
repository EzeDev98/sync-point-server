package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.feature.wallet.dto.request.WalletTransferRequest;
import com.sp.syncPoint.feature.wallet.dto.response.WalletFundingResponse;

public interface WalletFundingService {

    WalletFundingResponse fundOfflineWallet(WalletTransferRequest request);

    WalletFundingResponse transferToOnlineAccount(WalletTransferRequest request);
}