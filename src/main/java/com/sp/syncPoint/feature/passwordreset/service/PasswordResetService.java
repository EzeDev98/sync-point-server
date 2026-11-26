package com.sp.syncPoint.feature.passwordreset.service;


import com.sp.syncPoint.client.dto.request.CompletePasswordResetRequest;
import com.sp.syncPoint.client.dto.request.PasswordResetRequest;
import com.sp.syncPoint.client.dto.request.VerifyOtpRequest;
import com.sp.syncPoint.client.dto.response.PasswordResetResponse;
import com.sp.syncPoint.client.dto.response.VerifyOtpResponse;

public interface PasswordResetService {

    PasswordResetResponse requestOtp(PasswordResetRequest request);

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    PasswordResetResponse completeReset(CompletePasswordResetRequest request);
}