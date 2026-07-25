package com.sp.syncPoint.feature.passwordreset.service;

import com.sp.syncPoint.feature.passwordreset.dto.CompletePasswordResetRequest;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetRequestDto;
import com.sp.syncPoint.common.reset.dto.VerifyOtpRequest;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetResponse;
import com.sp.syncPoint.common.reset.dto.VerifyOtpResponse;

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