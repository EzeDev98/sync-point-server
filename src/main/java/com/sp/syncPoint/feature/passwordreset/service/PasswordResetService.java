package com.sp.syncPoint.feature.passwordreset.service;


import com.sp.syncPoint.client.dto.request.CompletePasswordResetRequest;
import com.sp.syncPoint.client.dto.request.PasswordResetRequest;
import com.sp.syncPoint.client.dto.request.VerifyOtpRequest;
import com.sp.syncPoint.client.dto.response.PasswordResetResponse;
import com.sp.syncPoint.client.dto.response.VerifyOtpResponse;

public interface PasswordResetService {

    /**
     * Requests a password-reset OTP for the given phone number. Always
     * returns a generic success response, regardless of whether the phone
     * number is registered, to prevent user enumeration.
     */
    PasswordResetResponse requestOtp(PasswordResetRequest request);

    /**
     * Verifies a submitted OTP and, on success, issues a short-lived,
     * single-use password-reset authorization token.
     */
    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    /**
     * Completes the password reset using a valid reset token, updating the
     * password in both Keycloak and the local mirrored hash, then
     * invalidating all active sessions for the user.
     */
    PasswordResetResponse completeReset(CompletePasswordResetRequest request);
}