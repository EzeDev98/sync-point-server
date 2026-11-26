package com.sp.syncPoint.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Component
public class TransferSignatureVerifier {

    private static final String CANONICAL_DOMAIN = "SYNCPOINT-TRANSFER";
    private static final String CANONICAL_VERSION = "v1";

    private static final String KEY_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format";

    public String canonicalTransferString(
            String transactionRef,
            String requestRef,
            String senderWalletId,
            String receiverWalletId,
            BigDecimal amount,
            String currency,
            Instant createdAt) {

        return String.join("|",
                CANONICAL_DOMAIN,
                CANONICAL_VERSION,
                transactionRef,
                requestRef,
                senderWalletId,
                receiverWalletId,
                amountToKobo(amount),
                currency,
                String.valueOf(createdAt.toEpochMilli()));
    }

    public boolean verify(
            String senderPublicKeyBase64,
            String transactionRef,
            String requestRef,
            String senderWalletId,
            String receiverWalletId,
            BigDecimal amount,
            String currency,
            Instant createdAt,
            String signatureBase64) {

        if (isBlank(senderPublicKeyBase64) || isBlank(signatureBase64)) {
            log.warn("[TransferSignature] Missing key or signature for transfer {} — rejecting.", transactionRef);
            return false;
        }

        try {
            PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(senderPublicKeyBase64)));

            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);

            String canonical = canonicalTransferString(
                    transactionRef, requestRef, senderWalletId, receiverWalletId, amount, currency, createdAt);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));

            boolean valid = verifier.verify(Base64.getDecoder().decode(signatureBase64));
            if (!valid) {
                log.warn("[TransferSignature] Signature did not verify for transfer {}.", transactionRef);
            }
            return valid;

        } catch (Exception e) {
            log.warn("[TransferSignature] Verification error for transfer {}: {}", transactionRef, e.getMessage());
            return false;
        }
    }

    private String amountToKobo(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .toBigIntegerExact()
                .toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}