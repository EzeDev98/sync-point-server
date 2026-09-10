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

    public InternalAuthClient(
            @Value("${internal-auth.base-url}") String baseUrl,
            @Value("${internal-auth.client-id}") String clientId,
            @Value("${internal-auth.client-secret}") String clientSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
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
}