package com.sp.syncPoint.feature.wallet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code POST /wallet/signing-key}: the device's ECDSA P-256 public
 * key as base64 SPKI, to be stored on the caller's wallet and used to verify
 * the signatures on the offline transfers that wallet sends.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SigningKeyRequest {
    private String signingPublicKey;
}