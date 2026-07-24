package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.response.BaseResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinValidatorTest {

    private final PinValidator validator = new PinValidator();

    // ── setPinValidation ───────────────────────────────────────────

    private SetPinRequest validSetPinRequest() {
        SetPinRequest request = new SetPinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin("7358");
        request.setConfirmNewPin("7358");
        return request;
    }

    @Test
    void setPinValidation_valid_doesNotThrow() {
        assertThatCode(() -> validator.setPinValidation(validSetPinRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void setPinValidation_missingPhoneNumber_throwsEmptyFieldException() {
        SetPinRequest request = validSetPinRequest();
        request.setPhoneNumber(null);

        assertThatThrownBy(() -> validator.setPinValidation(request))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void setPinValidation_missingPin_throwsEmptyFieldException() {
        SetPinRequest request = validSetPinRequest();
        request.setPin(null);

        assertThatThrownBy(() -> validator.setPinValidation(request))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void setPinValidation_missingConfirmation_throwsEmptyFieldException() {
        SetPinRequest request = validSetPinRequest();
        request.setConfirmNewPin(null);

        assertThatThrownBy(() -> validator.setPinValidation(request))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void setPinValidation_nonFourDigitPin_throwsInvalidPinException() {
        SetPinRequest request = validSetPinRequest();
        request.setPin("12345");

        assertThatThrownBy(() -> validator.setPinValidation(request))
                .isInstanceOf(InvalidPinException.class)
                .hasMessage("Pin must be exactly 4 digits");
    }

    // ── validateRequiredFields ─────────────────────────────────────

    @Test
    void validateRequiredFields_allPresent_returnsTrue() {
        BaseResponse response = new BaseResponse();

        assertThat(validator.validateRequiredFields(response, "a", "b", "c")).isTrue();
    }

    @Test
    void validateRequiredFields_containsNull_returnsFalseAndPopulatesResponse() {
        BaseResponse response = new BaseResponse();

        boolean result = validator.validateRequiredFields(response, "a", null, "c");

        assertThat(result).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getDescription()).isEqualTo("All required fields must not be null");
    }

    @Test
    void validateRequiredFields_containsBlankString_returnsFalse() {
        BaseResponse response = new BaseResponse();

        assertThat(validator.validateRequiredFields(response, "a", "", "c")).isFalse();
    }

    @Test
    void validateRequiredFields_noFields_returnsTrue() {
        BaseResponse response = new BaseResponse();

        assertThat(validator.validateRequiredFields(response)).isTrue();
    }
}