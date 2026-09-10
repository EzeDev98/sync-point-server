package com.sp.syncPoint.feature.auth.service.impl;

import com.sp.syncPoint.feature.auth.dto.request.RegistrationRequest;
import com.sp.syncPoint.common.exception.RegistrationException;
import org.springframework.stereotype.Component;

/**
 * Validates new-user registration requests.
 * <p>
 * Extracted from the original {@code ValidateService} grab-bag as part of
 * the SOLID refactor — that class validated six unrelated domains
 * (registration, PIN setting, password reset, OTP format, transactions,
 * and a generic required-fields check) in one place, despite no consumer
 * ever needing more than one of those domains. This class holds exactly
 * the registration rules, used only by {@code UserServiceImpl}. No rule
 * changed during the move.
 */
@Component
public class RegistrationValidator {

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
}