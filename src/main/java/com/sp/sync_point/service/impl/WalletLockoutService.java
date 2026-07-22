package com.sp.sync_point.service.impl;

import com.sp.sync_point.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.MAX_LOGIN_ATTEMPTS;
import static com.sp.sync_point.constants.GeneralConstants.WALLET_LOCKED_DURATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletLockoutService {

    private final WalletRepository walletRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedLoginAttempt(String phoneNumber) {
        return walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .map(wallet -> {
                    Integer currentAttempts = wallet.getLoginAttempts();
                    int newAttempts = (currentAttempts == null) ? 1 : currentAttempts + 1;
                    wallet.setLoginAttempts(newAttempts);
                    wallet.setLastLoginAttempt(LocalDateTime.now());

                    if (newAttempts >= MAX_LOGIN_ATTEMPTS) {
                        wallet.setLoginLockUntil(LocalDateTime.now().plusMinutes(WALLET_LOCKED_DURATION));
                        wallet.setLocked(true);
                        wallet.setLockedUntil(LocalDateTime.now().plusMinutes(WALLET_LOCKED_DURATION));
                    }

                    walletRepository.save(wallet);
                    return newAttempts;
                })
                .orElse(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedPinChangeAttempt(String phoneNumber) {
        return walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .map(wallet -> {
                    Integer currentAttempts = wallet.getPinChangeAttempts();
                    int newAttempts = (currentAttempts == null) ? 1 : currentAttempts + 1;
                    wallet.setPinChangeAttempts(newAttempts);
                    wallet.setLastPinChangeAttempt(LocalDateTime.now());

                    walletRepository.save(wallet);
                    return newAttempts;
                })
                .orElse(0);
    }
}