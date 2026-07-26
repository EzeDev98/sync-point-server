package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.response.BaseResponse;

/**
 * Registration of a wallet's offline-transfer signing key.
 * <p>
 * Consumed by {@code WalletController} via {@code POST /wallet/signing-key}.
 * The key is always registered on the <em>caller's own</em> wallet (resolved
 * from the JWT), never on one named in the request, so a user can only ever
 * publish a signing key for themselves.
 */
public interface WalletSigningKeyService {

    /**
     * Stores {@code signingPublicKey} (base64 SPKI, ECDSA P-256) on the
     * authenticated caller's wallet, replacing any previously registered key.
     *
     * @throws BadRequestException if the key
     *         is missing or not a parseable EC public key
     */
    BaseResponse registerSigningKey(String signingPublicKey);
}