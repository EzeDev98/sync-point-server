package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetValidatorTest {

    private final ResetValidator validator = new ResetValidator();

    // ── validatePhoneNumberFoResetOperations ──────────────────────

    @Test
    void validatePhoneNumberFoResetOperations_valid_doesNotThrow() {
        assertThatCode(() -> validator.validatePhoneNumberFoResetOperations("08010000001"))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePhoneNumberFoResetOperations_blank_throws() {
        assertThatThrownBy(() -> validator.validatePhoneNumberFoResetOperations(" "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phone number is required");
    }

    @Test
    void validatePhoneNumberFoResetOperations_invalidFormat_throws() {
        assertThatThrownBy(() -> validator.validatePhoneNumberFoResetOperations("12345"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please enter a valid phone number");
    }

    // ── validateOtpFormat ──────────────────────────────────────────

    @Test
    void validateOtpFormat_correctLength_doesNotThrow() {
        assertThatCode(() -> validator.validateOtpFormat("123456", 6))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOtpFormat_blank_throws() {
        assertThatThrownBy(() -> validator.validateOtpFormat("", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Verification code is required");
    }

    @Test
    void validateOtpFormat_wrongLength_throws() {
        assertThatThrownBy(() -> validator.validateOtpFormat("123", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The verification code is invalid");
    }

    @Test
    void validateOtpFormat_nonDigits_throws() {
        assertThatThrownBy(() -> validator.validateOtpFormat("abcdef", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The verification code is invalid");
    }

    // ── validateNewPassword ────────────────────────────────────────

    @Test
    void validateNewPassword_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateNewPassword("password123", "password123"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNewPassword_tooShort_throws() {
        assertThatThrownBy(() -> validator.validateNewPassword("short", "short"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void validateNewPassword_mismatch_throws() {
        assertThatThrownBy(() -> validator.validateNewPassword("password123", "different123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void validateNewPassword_blankConfirmation_throws() {
        assertThatThrownBy(() -> validator.validateNewPassword("password123", " "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Please confirm your new password");
    }
}