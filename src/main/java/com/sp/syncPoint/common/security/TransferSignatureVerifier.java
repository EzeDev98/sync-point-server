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

/**
 * Verifies the sender's cryptographic authorization of an offline P2P
 * transfer.
 * <p>
 * This is the server-side counterpart of the client's
 * {@code core/crypto/transferSignature.js}, and the security replacement for
 * the old {@code assertOwnsPhoneNumber} check on the sync endpoint. Once a
 * receiver is allowed to settle a transfer they received — i.e. to submit a
 * batch whose sender is someone else — "the caller owns the sender phone" can
 * no longer be the guard, because it is no longer true. What guards the
 * credit instead is this: the transfer carries a signature that only the
 * genuine sender's private key could have produced, over a canonical
 * description of that exact transfer, and we verify it here against the
 * sender wallet's registered public key. Without the sender's key nobody can
 * forge a credit into any wallet.
 * <p>
 * The canonical string, algorithm, signature encoding and key encoding must
 * stay byte-for-byte identical to the client:
 * <pre>
 *   algorithm   ECDSA, curve P-256, digest SHA-256
 *   signature   raw IEEE-P1363 (r||s), base64 (WebCrypto's native output);
 *               verified with "SHA256withECDSAinP1363Format"
 *   public key  SPKI/X.509 DER, base64
 *   payload     SYNCPOINT-TRANSFER|v1|{transactionRef}|{requestRef}
 *               |{senderWalletId}|{receiverWalletId}|{amountInKobo}
 *               |{currency}|{createdAtEpochMillis}
 * </pre>
 * Amount is serialised as integer kobo and time as epoch-millis so that no
 * decimal-scale or timestamp-format difference between the JVM and the
 * browser can quietly break verification.
 * <p>
 * {@link #verify} never throws: any malformed key, malformed signature, or
 * mismatch is a failed verification and returns {@code false}. The caller
 * decides what a failed verification means (reject that one transfer), which
 * keeps a single bad entry from poisoning an otherwise valid batch.
 */
@Slf4j
@Component
public class TransferSignatureVerifier {

    private static final String CANONICAL_DOMAIN = "SYNCPOINT-TRANSFER";
    private static final String CANONICAL_VERSION = "v1";

    private static final String KEY_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format";

    /**
     * The exact string that was signed on the sending device. Exposed so any
     * caller that needs to log or debug what was verified uses one definition
     * of the format rather than rebuilding it.
     */
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

    /**
     * Verifies a transfer signature against the sender wallet's registered
     * public key.
     *
     * @param senderPublicKeyBase64 the sender wallet's registered SPKI public
     *                              key; a missing key means the sender never
     *                              enrolled and cannot be trusted, so this
     *                              returns {@code false}
     * @param signatureBase64       the base64 P1363 signature carried with the
     *                              transfer
     * @return {@code true} only when the signature is a valid P-256/SHA-256
     *         signature by that key over this transfer's canonical payload
     */
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
            // Malformed key, malformed signature, unsupported algorithm — all
            // are simply "not verified". Never let it escape as a 5xx.
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