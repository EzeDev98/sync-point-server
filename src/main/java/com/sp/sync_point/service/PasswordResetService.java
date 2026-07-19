package com.sp.sync_point.service;

import com.sp.sync_point.dto.CompletePasswordResetRequest;
import com.sp.sync_point.dto.PasswordResetRequestDto;
import com.sp.sync_point.dto.VerifyOtpRequest;
import com.sp.sync_point.response.PasswordResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;

public interface PasswordResetService {

    /**
     * Requests a password-reset OTP for the given phone number. Always
     * returns a generic success response, regardless of whether the phone
     * number is registered, to prevent user enumeration.
     */
    PasswordResetResponse requestOtp(PasswordResetRequestDto request);

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