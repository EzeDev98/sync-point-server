package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.response.BaseResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates PIN-domain input. Extracted from the original
 * {@code ValidateService} grab-bag as part of the SOLID refactor — used
 * only by {@code WalletPinServiceImpl}, which is also the only consumer of
 * the generic {@code validateRequiredFields} helper, so both live here
 * together rather than as two separate single-consumer components.
 * No rule changed during the move.
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
}