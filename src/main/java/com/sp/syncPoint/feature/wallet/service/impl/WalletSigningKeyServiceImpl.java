package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletSigningKeyService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Registers the caller's offline-transfer signing key on their own wallet.
 * <p>
 * The wallet is resolved from the authenticated JWT (never from a
 * request-supplied identifier), so this endpoint can only ever set the key
 * for the caller. Registering a key for yourself is not a privileged action:
 * the key only ever authorizes transfers <em>you</em> send, so the worst a
 * caller can do is register a bad key and make their own transfers
 * unverifiable.
 * <p>
 * Re-registration overwrites the previous key, which is what makes the
 * "enroll once on next login" migration and re-install on a new device work.
 * The trade-off: a transfer still in flight that was signed with a superseded
 * key will no longer verify — acceptable under the single-active-device model
 * the login flow already enforces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletSigningKeyServiceImpl implements WalletSigningKeyService {

    private final WalletRepository walletRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    @Override
    public BaseResponse registerSigningKey(String signingPublicKey) {
        if (signingPublicKey == null || signingPublicKey.isBlank()) {
            throw new BadRequestException("signingPublicKey is required");
        }

        // Fail fast on garbage rather than storing a key that would make every
        // future transfer silently fail verification.
        assertParseableEcPublicKey(signingPublicKey);

        String phoneNumber = currentUserService.getCurrentPhoneNumber();

        Wallet wallet = walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .orElseThrow(() -> new EntityNotFoundException("No wallet found for the authenticated user"));

        wallet.setSigningPublicKey(signingPublicKey);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        log.info("[SigningKey] Registered signing key for wallet {}", wallet.getWalletId());
        return new BaseResponse(HttpServletResponse.SC_OK, "Signing key registered");
    }

    private void assertParseableEcPublicKey(String base64Spki) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Spki);
            KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            log.warn("[SigningKey] Rejected malformed signing key: {}", e.getMessage());
            throw new BadRequestException("signingPublicKey is not a valid EC public key");
        }
    }
}