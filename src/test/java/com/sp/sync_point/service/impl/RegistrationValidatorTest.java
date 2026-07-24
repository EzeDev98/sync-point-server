package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.RegistrationRequest;
import com.sp.sync_point.enums.GenderStatus;
import com.sp.sync_point.exception.RegistrationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationValidatorTest {

    private final RegistrationValidator validator = new RegistrationValidator();

    private RegistrationRequest validRequest() {
        RegistrationRequest request = new RegistrationRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setUsername("jane-doe");
        request.setEmail("jane@example.com");
        request.setPhoneNumber("08010000001");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setGender(GenderStatus.female);
        return request;
    }

    @Test
    void validateRegistrationRequest_validRequest_doesNotThrow() {
        assertThatCode(() -> validator.validateRegistrationRequest(validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRegistrationRequest_blankFirstName_throws() {
        RegistrationRequest request = validRequest();
        request.setFirstName(" ");

        assertThatThrownBy(() -> validator.validateRegistrationRequest(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("First name is required");
    }

    @Test
    void validateRegistrationRequest_invalidPhoneFormat_throws() {
        RegistrationRequest request = validRequest();
        request.setPhoneNumber("not-a-phone");

        assertThatThrownBy(() -> validator.validateRegistrationRequest(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("Phone number must be 11 digits and can start with +");
    }

    @Test
    void validateRegistrationRequest_shortPassword_throws() {
        RegistrationRequest request = validRequest();
        request.setPassword("short");
        request.setConfirmPassword("short");

        assertThatThrownBy(() -> validator.validateRegistrationRequest(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void validateRegistrationRequest_passwordMismatch_throws() {
        RegistrationRequest request = validRequest();
        request.setConfirmPassword("different123");

        assertThatThrownBy(() -> validator.validateRegistrationRequest(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void validateRegistrationRequest_missingGender_throws() {
        RegistrationRequest request = validRequest();
        request.setGender(null);

        assertThatThrownBy(() -> validator.validateRegistrationRequest(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("Gender is required");
    }
}