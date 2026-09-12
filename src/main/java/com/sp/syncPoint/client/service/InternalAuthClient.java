package com.sp.syncPoint.client.service;

import com.sp.syncPoint.client.dto.request.*;
import com.sp.syncPoint.client.dto.response.*;
import com.sp.syncPoint.feature.wallet.dto.request.ResendPhoneVerificationRequest;
import com.sp.syncPoint.feature.wallet.dto.request.VerifyPhoneRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Base64;

@Component
public class InternalAuthClient {

    private final RestClient restClient;
    private final String basicAuthHeader;
    private final String clientId;
    private final String resetSecret;

    private static final String WIPE_CONFIRMATION = "WIPE-EVERYTHING";

    public InternalAuthClient(
            @Value("${internal-auth.base-url}") String baseUrl,
            @Value("${internal-auth.client-id}") String clientId,
            @Value("${internal-auth.client-secret}") String clientSecret,
            @Value("${internal-auth.reset.secret:}") String resetSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        this.clientId = clientId;
        this.resetSecret = resetSecret;
    }

    public InternalAuthApiResponse<RegisterResponse> register(RegisterRequest request) {
        return restClient.post()
                .uri("/auth/register")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<RegisterResponse>>() {});
    }

    public InternalAuthApiResponse<InternalAuthLoginResponse> login(LoginRequest request) {
        return restClient.post()
                .uri("/auth/login")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<InternalAuthLoginResponse>>() {});
    }

    public InternalAuthApiResponse<LoginResponse> refreshToken(RefreshTokenRequest request) {
        return restClient.post()
                .uri("/auth/refresh-token")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<LoginResponse>>() {});
    }

    public InternalAuthApiResponse<ActivationResponse> verifyPhone(VerifyPhoneRequest request) {
        return restClient.post()
                .uri("/auth/verify-phone")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<ActivationResponse>>() {});
    }

    public InternalAuthApiResponse<ActivationResponse> resendPhoneVerification(ResendPhoneVerificationRequest request) {
        return restClient.post()
                .uri("/auth/resend-phone-verification")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<ActivationResponse>>() {});
    }

    public InternalAuthApiResponse<PasswordResetResponse> requestPasswordResetOtp(PasswordResetRequest request) {
        return restClient.post()
                .uri("/password-reset/request")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<PasswordResetResponse>>() {});
    }

    public InternalAuthApiResponse<VerifyOtpResponse> verifyPasswordResetOtp(VerifyOtpRequest request) {
        return restClient.post()
                .uri("/password-reset/verify-otp")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<VerifyOtpResponse>>() {});
    }

    public InternalAuthApiResponse<PasswordResetResponse> completeReset(CompletePasswordResetRequest request) {
        return restClient.post()
                .uri("/password-reset/complete")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<PasswordResetResponse>>() {});
    }

    public InternalAuthApiResponse<LogoutResponse> logout(String accessToken) {
        return restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/session")
                        .build())
                .header(HttpHeaders.AUTHORIZATION,"Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<LogoutResponse>>() {});
    }

    public void deleteUser(String internalAuthUserId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/user")
                        .queryParam("internalAuthUserId", internalAuthUserId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<Void>>() {});
    }

    /**
     * Calls down into internal-auth-server's own tenant wipe endpoint so
     * SyncPoint's {@code /admin/reset/wipe} can clear its own database and
     * every internal-auth-server record scoped to this client application
     * together. Uses the dedicated internal-auth.reset.secret
     * (X-Reset-Secret header), NOT the client-id/secret Basic-auth header
     * every other method here uses - that Basic auth authenticates "which
     * app is calling", this secret separately authorizes "this call may
     * wipe data". appId is always this app's own clientId, so this can
     * never be pointed at another tenant's data.
     */
    public InternalAuthApiResponse<TenantWipeResponse> wipeTenantData() {
        return restClient.post()
                .uri("/admin/reset/wipe-tenant")
                .header("X-Reset-Secret", resetSecret)
                .body(new TenantWipeRequest(clientId, WIPE_CONFIRMATION))
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<TenantWipeResponse>>() {});
    }
}