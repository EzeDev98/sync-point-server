package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.BadRequestException;
import org.springframework.stereotype.Component;

/**
 * Validates input for the OTP-based reset flows.
 * <p>
 * Extracted from the original {@code ValidateService} grab-bag as part of
 * the SOLID refactor. Shared by {@code PasswordResetServiceImpl} and
 * {@code PinResetServiceImpl} — the same two classes that already share
 * {@link OtpResetEngine} for the reset-mechanics side of this feature; this
 * class is the input-validation counterpart for that same domain. No rule
 * changed during the move.
 */
@Component
public class ResetValidator {

    public void validatePhoneNumberFoResetOperations(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("Phone number is required");
        }
        if (!phoneNumber.matches("^\\+?[0-9]{11}$")) {
            throw new BadRequestException("Please enter a valid phone number");
        }
    }

    public void validateOtpFormat(String otp, int expectedLength) {
        if (otp == null || otp.isBlank()) {
            throw new BadRequestException("Verification code is required");
        }
        if (!otp.matches("\\d{" + expectedLength + "}")) {
            throw new BadRequestException("The verification code is invalid");
        }
    }

    /**
     * Reuses the same minimum-length rule enforced during registration
     * (see RegistrationValidator) so password strength requirements aren't
     * duplicated inconsistently between flows.
     */
    public void validateNewPassword(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("Password is required");
        }
        if (newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new BadRequestException("Please confirm your new password");
        }
        if (!confirmPassword.equals(newPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
    }
}