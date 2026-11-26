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