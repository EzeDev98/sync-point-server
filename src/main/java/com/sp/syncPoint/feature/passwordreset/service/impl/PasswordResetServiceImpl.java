package com.sp.syncPoint.feature.passwordreset.service.impl;

import com.sp.syncPoint.client.dto.request.CompletePasswordResetRequest;
import com.sp.syncPoint.client.dto.request.PasswordResetRequest;
import com.sp.syncPoint.client.dto.request.VerifyOtpRequest;
import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.dto.response.PasswordResetResponse;
import com.sp.syncPoint.client.dto.response.VerifyOtpResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import com.sp.syncPoint.feature.passwordreset.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final InternalAuthClient authClient;
    private final SessionManagementService sessionManagementService;

    @Override
    public PasswordResetResponse requestOtp(PasswordResetRequest request) {
        InternalAuthApiResponse<PasswordResetResponse> response = authClient.requestPasswordResetOtp(request);
        log.info("Password reset OTP request forwarded to internal-auth-server");
        return response.getData();
    }

    @Override
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        InternalAuthApiResponse<VerifyOtpResponse> response = authClient.verifyPasswordResetOtp(request);
        return response.getData();
    }

    @Override
    public PasswordResetResponse completeReset(CompletePasswordResetRequest request) {
        InternalAuthApiResponse<PasswordResetResponse> response = authClient.completeReset(request);
        PasswordResetResponse data = response.getData();

        if (data != null && data.getPhoneNumber() != null) {
            sessionManagementService.invalidateUserSessionByAdmin(data.getPhoneNumber());
        }

        log.info("Password reset completed via internal-auth-server");
        return data;
    }
}
