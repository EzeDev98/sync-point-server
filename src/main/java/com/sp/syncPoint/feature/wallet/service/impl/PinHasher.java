package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.IncorrectPinException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashing and verification of a wallet's transaction PIN.
 * <p>
 * Extracted from {@code WalletPinServiceImpl} during the phase-2
 * separation-of-concerns pass. The service now orchestrates PIN operations
 * and delegates the cryptographic detail (bcrypt encode/verify) here — a
 * thin, single-purpose wrapper over {@link PasswordEncoder}. No rule
 * changed during the move; {@link #assertMatches} mirrors the former
 * {@code validateHashedPin} helper exactly.
 */
@Component
@RequiredArgsConstructor
public class PinHasher {

    private final PasswordEncoder passwordEncoder;

    /** Hashes a raw PIN for storage. */
    public String hash(String rawPin) {
        return passwordEncoder.encode(rawPin);
    }

    /** True when the raw PIN matches the stored hash. */
    public boolean matches(String rawPin, String storedHashedPin) {
        return passwordEncoder.matches(rawPin, storedHashedPin);
    }

    /**
     * Asserts the raw PIN matches the stored hash, throwing
     * {@link IncorrectPinException} when no PIN is set or the PIN is wrong.
     */
    public void assertMatches(String storedHashedPin, String rawPin) {
        if (storedHashedPin == null) {
            throw new IncorrectPinException("No PIN set for this wallet");
        }
        if (!passwordEncoder.matches(rawPin, storedHashedPin)) {
            throw new IncorrectPinException("Incorrect PIN");
        }
    }
}