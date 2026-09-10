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

/**
 * Password reset is now owned entirely by internal-auth-server: it holds
 * the password, the OTP/reset-token tables, the resend cooldown, the
 * user-enumeration protection on request, and session invalidation on
 * completion. This class is a thin pass-through - no local OTP generation,
 * no local token storage, no local password mirror (SyncPoint's User
 * entity has no password field to mirror into). All three steps forward
 * to InternalAuthClient and unwrap its response.
 *
 * Retired here: local PasswordResetOtp/PasswordResetToken persistence, the
 * Redis resend-cooldown bucket, and the dead Keycloak call + no-op
 * userRepository.save(user) that used to sit in completeReset (see git
 * history) - the equivalent logic now runs once, centrally, in
 * internal-auth-server, instead of separately per client app.
 */
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

        // internal-auth-server only revokes its own OAuth2Authorization
        // records on reset - it has no visibility into SyncPoint's local
        // single-active-session lock, so that has to be cleared here.
        if (data != null && data.getPhoneNumber() != null) {
            sessionManagementService.invalidateUserSessionByAdmin(data.getPhoneNumber());
        }

        log.info("Password reset completed via internal-auth-server");
        return data;
    }
}
