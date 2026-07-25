package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.EmptyFieldException;
import com.sp.syncPoint.common.exception.InvalidPinException;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.wallet.dto.SetPinRequest;
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

    // ── isNotValidFormat (moved from WalletPinServiceImpl) ──────────

    @Test
    void isNotValidFormat_fourDigits_returnsFalse() {
        assertThat(validator.isNotValidFormat("7358")).isFalse();
        assertThat(validator.isNotValidFormat("0429")).isFalse();
        assertThat(validator.isNotValidFormat("9042")).isFalse();
    }

    @Test
    void isNotValidFormat_malformed_returnsTrue() {
        assertThat(validator.isNotValidFormat("123")).isTrue();    // too short
        assertThat(validator.isNotValidFormat("12345")).isTrue();  // too long
        assertThat(validator.isNotValidFormat("abcd")).isTrue();   // non-digit
        assertThat(validator.isNotValidFormat("12a4")).isTrue();   // mixed
        assertThat(validator.isNotValidFormat("12 4")).isTrue();   // whitespace
    }

    @Test
    void isNotValidFormat_null_returnsTrue() {
        assertThat(validator.isNotValidFormat(null)).isTrue();
    }

    // ── isWeak (moved from WalletPinServiceImpl) ───────────────────

    @Test
    void isWeak_guessablePins_returnTrue() {
        assertThat(validator.isWeak("1234")).isTrue();  // common
        assertThat(validator.isWeak("1111")).isTrue();  // all-same
        assertThat(validator.isWeak("0000")).isTrue();  // all-same / common
        assertThat(validator.isWeak("4321")).isTrue();  // descending sequence
        assertThat(validator.isWeak("1212")).isTrue();  // repeated two-digit pattern
        assertThat(validator.isWeak("1313")).isTrue();  // repeated two-digit pattern
    }

    @Test
    void isWeak_strongPins_returnFalse() {
        assertThat(validator.isWeak("7358")).isFalse();
        assertThat(validator.isWeak("0429")).isFalse();
        assertThat(validator.isWeak("8261")).isFalse();
        assertThat(validator.isWeak("9042")).isFalse();
    }

    @Test
    void isWeak_null_returnsFalse() {
        assertThat(validator.isWeak(null)).isFalse();
    }
}