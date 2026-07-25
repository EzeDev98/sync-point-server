package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.sp.syncPoint.common.constants.GeneralConstants.*;


/**
 * Sole owner of wallet lockout state — both the LOGIN counters
 * ({@code loginAttempts}/{@code loginLockUntil}) and the PIN-CHANGE
 * counters ({@code pinChangeAttempts}/{@code lastPinChangeAttempt}), plus
 * the shared wallet-lock flag ({@code locked}/{@code lockedUntil}).
 * <p>
 * The {@code recordFailed*} mutators run in their own transaction so a
 * failed attempt is persisted even when the surrounding operation rolls
 * back. The remaining methods are pure in-memory state transitions on an
 * already-loaded {@link Wallet}; the caller persists within its own
 * transaction. The lock-state queries and counter resets were consolidated
 * here from {@code WalletPinServiceImpl} and {@code WalletAuthServiceImpl}
 * during the phase-2 separation-of-concerns pass (the two services
 * previously each carried their own copy) — no rule changed in the move.
 */
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

    // ── shared wallet lock flag ────────────────────────────────────

    /**
     * True when the wallet is currently locked. A lock whose window has
     * already elapsed is cleared in-place and reported as unlocked.
     */
    public boolean isWalletLocked(Wallet wallet) {
        if (!Boolean.TRUE.equals(wallet.getLocked())) return false;

        LocalDateTime lockedUntil = wallet.getLockedUntil();
        if (lockedUntil == null) return true;

        if (lockedUntil.isBefore(LocalDateTime.now())) {
            unlockWallet(wallet);
            return false;
        }
        return true;
    }

    public void unlockWallet(Wallet wallet) {
        wallet.setLocked(false);
        wallet.setLockedUntil(null);
    }

    // ── PIN-change lock state ──────────────────────────────────────

    public boolean isLockedForPinChange(Wallet wallet) {
        Integer attempts = wallet.getPinChangeAttempts();
        LocalDateTime lastAttempt = wallet.getLastPinChangeAttempt();

        if (attempts != null && attempts >= MAX_PIN_CHANGE_ATTEMPTS && lastAttempt != null) {
            return lastAttempt.plusMinutes(PIN_CHANGE_LOCK_MINUTES).isAfter(LocalDateTime.now());
        }
        return false;
    }

    public long getRemainingPinChangeLockTime(Wallet wallet) {
        LocalDateTime lastAttempt = wallet.getLastPinChangeAttempt();
        if (lastAttempt == null) return 0;

        LocalDateTime unlockTime = lastAttempt.plusMinutes(PIN_CHANGE_LOCK_MINUTES);
        LocalDateTime now = LocalDateTime.now();

        if (unlockTime.isAfter(now)) {
            return Duration.between(now, unlockTime).toMinutes();
        }
        return 0;
    }

    public void incrementPinChangeAttempt(Wallet wallet) {
        Integer currentAttempts = wallet.getPinChangeAttempts();
        wallet.setPinChangeAttempts((currentAttempts == null) ? 1 : currentAttempts + 1);
        wallet.setLastPinChangeAttempt(LocalDateTime.now());
    }

    public void resetPinChangeAttempts(Wallet wallet) {
        wallet.setPinChangeAttempts(0);
        wallet.setLastPinChangeAttempt(null);
    }

    // ── login lock state ───────────────────────────────────────────

    /**
     * True when a login lock is currently in force. An elapsed lock is
     * cleared in-place (attempts reset) and reported as unlocked.
     */
    public boolean isLoginLocked(Wallet wallet) {
        LocalDateTime loginLockUntil = wallet.getLoginLockUntil();
        if (loginLockUntil == null) return false;

        if (loginLockUntil.isAfter(LocalDateTime.now())) {
            return true;
        }

        resetLoginAttempts(wallet);
        return false;
    }

    public void resetLoginAttempts(Wallet wallet) {
        wallet.setLoginAttempts(0);
        wallet.setLastLoginAttempt(null);
        wallet.setLoginLockUntil(null);
    }

    public long getRemainingLoginLockTime(Wallet wallet) {
        LocalDateTime loginLockUntil = wallet.getLoginLockUntil();
        if (loginLockUntil == null || loginLockUntil.isBefore(LocalDateTime.now())) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), loginLockUntil).toMinutes();
    }
}