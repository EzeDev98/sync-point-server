package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.RegistrationRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.exception.RegistrationException;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.utils.MoneyUtils;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import static com.sp.sync_point.constants.GeneralConstants.MAX_TRANSACTION_AMOUNT;

@Component
public class ValidateService {

    private static final Logger log = LoggerFactory.getLogger(ValidateService.class);

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

    public void validateRegistrationRequest(RegistrationRequest request) {

        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            throw new RegistrationException("First name is required");
        }

        if (request.getLastName() == null || request.getLastName().isBlank()) {
            throw new RegistrationException("Last name is required");
        }

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new RegistrationException("Username is required");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RegistrationException("Email is required");
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new RegistrationException("Phone number is required");
        }

        if (!request.getPhoneNumber().matches("^\\+?[0-9]{11}$")) {
            throw new RegistrationException("Phone number must be 11 digits and can start with +");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RegistrationException("Password is required");
        }

        if (request.getPassword().length() < 8) {
            throw new RegistrationException("Password must be at least 8 characters");
        }

        if (request.getConfirmPassword() == null || request.getConfirmPassword().isBlank()) {
            throw new RegistrationException("Confirm password is required");
        }

        if (!request.getConfirmPassword().equals(request.getPassword())) {
            throw new RegistrationException("Passwords do not match");
        }

        if (request.getGender() == null) {
            throw new RegistrationException("Gender is required");
        }

        if (!request.getGender().toString().matches("^(male|female)$")) {
            throw new RegistrationException("Gender must be Male or Female");
        }
    }

    public void validatePhoneNumberForPasswordReset(String phoneNumber) {
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
     * (see validateRegistrationRequest) so password strength requirements
     * aren't duplicated inconsistently between flows.
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

    public void validateTransaction(SyncTransactionRequest transactionRequest) {

        if (transactionRequest == null) {
            throw new BadRequestException("Transaction cannot be null or empty");
        }

        if (transactionRequest.getAmount() == null) {
            throw new BadRequestException("Transaction amount cannot be null");
        }

        if (!MoneyUtils.isPositive(transactionRequest.getAmount())) {
            throw new BadRequestException("Transaction amount must be positive");
        }

        if (MoneyUtils.isGreaterThan(transactionRequest.getAmount(), MAX_TRANSACTION_AMOUNT)) {
            throw new BadRequestException("Transaction amount exceeds maximum allowed: " + MAX_TRANSACTION_AMOUNT);
        }

        if (transactionRequest.getType() == null || transactionRequest.getType().trim().isEmpty()) {
            throw new BadRequestException("Transaction type cannot be null or empty");
        }

        if (transactionRequest.getSenderPhone() == null || transactionRequest.getSenderPhone().trim().isEmpty()) {
            throw new BadRequestException("User wallet phone number cannot be null or empty");
        }

        if (transactionRequest.getTransactionRef() == null || transactionRequest.getTransactionRef().trim().isEmpty()) {
            throw new BadRequestException("Transaction reference cannot be null or empty");
        }

        if (transactionRequest.getCreatedAt() == null) {
            throw new BadRequestException("Transaction date cannot be null or empty");
        }
    }
}