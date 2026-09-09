package com.sp.syncPoint.feature.wallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sp.syncPoint.client.dto.response.ActivationResponse;
import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.exception.MaxAttemptsExceededException;
import com.sp.syncPoint.feature.wallet.dto.request.ResendPhoneVerificationRequest;
import com.sp.syncPoint.feature.wallet.dto.request.VerifyPhoneRequest;
import com.sp.syncPoint.feature.wallet.service.PhoneVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class PhoneVerificationServiceImpl implements PhoneVerificationService {

    private final ObjectMapper objectMapper;
    private final InternalAuthClient internalAuthClient;

    @Override
    public ActivationResponse verifyPhone(VerifyPhoneRequest request) {
        try {
            return internalAuthClient.verifyPhone(request).getData();
        } catch (RestClientResponseException ex) {
            throw translateAuthError(ex);
        }
    }

    @Override
    public ActivationResponse resendPhoneVerification(ResendPhoneVerificationRequest request) {
        try {
            return internalAuthClient.resendPhoneVerification(request).getData();
        } catch (RestClientResponseException ex) {
            throw translateAuthError(ex);
        }
    }

    private RuntimeException translateAuthError(RestClientResponseException ex) {
        String message = ex.getStatusText();
        try {
            InternalAuthApiResponse<?> body = objectMapper.readValue(
                    ex.getResponseBodyAsString(), InternalAuthApiResponse.class);
            if (body.getMessage() != null) {
                message = body.getMessage();
            }
        } catch (Exception parseFailure) {
            // internal-auth's response wasn't parseable as InternalAuthApiResponse
            // (e.g. a raw Spring error body from a proxy/timeout) - fall back to
            // the raw status text above rather than losing the error entirely.
        }

        if (ex.getStatusCode().value() == 429) {
            return new MaxAttemptsExceededException(message);
        }
        return new BadRequestException(message);
    }
}