package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.IncorrectPinException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PinHasher {

    private final PasswordEncoder passwordEncoder;

    public String hash(String rawPin) {
        return passwordEncoder.encode(rawPin);
    }

    public boolean matches(String rawPin, String storedHashedPin) {
        return passwordEncoder.matches(rawPin, storedHashedPin);
    }

    public void assertMatches(String storedHashedPin, String rawPin) {
        if (storedHashedPin == null) {
            throw new IncorrectPinException("No PIN set for this wallet");
        }
        if (!passwordEncoder.matches(rawPin, storedHashedPin)) {
            throw new IncorrectPinException("Incorrect PIN");
        }
    }
}