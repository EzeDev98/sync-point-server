package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.common.response.BaseResponse;

public interface WalletSigningKeyService {

    BaseResponse registerSigningKey(String signingPublicKey);
}