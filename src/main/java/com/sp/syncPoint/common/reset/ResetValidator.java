package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

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