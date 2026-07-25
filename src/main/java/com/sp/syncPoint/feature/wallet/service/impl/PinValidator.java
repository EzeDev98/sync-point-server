package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.EmptyFieldException;
import com.sp.syncPoint.common.exception.InvalidPinException;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.wallet.dto.SetPinRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates PIN-domain input: presence and shape of a set-PIN request, the
 * generic required-fields guard, PIN format, and PIN strength.
 * <p>
 * Extracted from the original {@code ValidateService} grab-bag as part of
 * the earlier SOLID refactor; the format ({@link #isNotValidFormat}) and
 * strength ({@link #isWeak}) rules were then moved here verbatim from
 * {@code WalletPinServiceImpl} during the phase-2 separation-of-concerns
 * pass, so all PIN-input validation now lives in one place. No rule
 * changed during either move.
 */
@Component
public class PinValidator {

    private static final Logger log = LoggerFactory.getLogger(PinValidator.class);

    public void setPinValidation(SetPinRequest request) {
        if (request.getPhoneNumber() == null) {
            throw new EmptyFieldException("Phone number is required.", HttpStatus.BAD_REQUEST);
        }

        if (request.getPin() == null) {
            throw new EmptyFieldException("Pin is required.", HttpStatus.BAD_REQUEST);
        }

        if (request.getConfirmNewPin() == null) {
            throw new EmptyFieldException("Pin confirmation is required.", HttpStatus.BAD_REQUEST);
        }

        if (!request.getPin().matches("\\d{4}")) {
            throw new InvalidPinException("Pin must be exactly 4 digits");
        }
    }

    public boolean validateRequiredFields(BaseResponse response, Object... fields) {
        for (Object field : fields) {
            if (field == null) {
                log.error("One of more fields are null");
                response.setDescription("All required fields must not be null");
                response.setStatusCode(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }

            if (field instanceof String string && string.isEmpty()) {
                log.error("One or more fields are null");
                response.setDescription("All required fields must not be null");
                response.setStatusCode(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    /** True when the PIN is null or not exactly four digits. */
    public boolean isNotValidFormat(String pin) {
        return pin == null || !pin.matches("\\d{4}");
    }

    /**
     * True when a PIN is trivially guessable — a well-known common PIN,
     * all-identical digits, an ascending/descending run, or a repeated
     * two-digit pattern (e.g. 1212).
     */
    public boolean isWeak(String pin) {
        if (pin == null) return false;

        Set<String> commonPins = Set.of(
                "1234", "1111", "0000", "1212", "1004",
                "2000", "4444", "2222", "6969", "9999",
                "7777", "5555", "6666", "1122", "1313"
        );

        if (commonPins.contains(pin)) {
            return true;
        }

        if (pin.chars().allMatch(c -> c == pin.charAt(0))) {
            return true;
        }

        if (isSequential(pin)) {
            return true;
        }

        if (pin.length() == 4 && pin.charAt(0) == pin.charAt(2) && pin.charAt(1) == pin.charAt(3)) {
            return true;
        }

        return false;
    }

    private boolean isSequential(String pin) {
        boolean ascending = true;
        boolean descending = true;

        for (int i = 0; i < pin.length() - 1; i++) {
            int current = Character.getNumericValue(pin.charAt(i));
            int next = Character.getNumericValue(pin.charAt(i + 1));

            if (next != current + 1) ascending = false;
            if (next != current - 1) descending = false;
        }

        return ascending || descending;
    }
}