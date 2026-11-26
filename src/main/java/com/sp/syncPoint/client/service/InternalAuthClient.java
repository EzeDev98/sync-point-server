package com.sp.syncPoint.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sp.syncPoint.client.dto.request.*;
import com.sp.syncPoint.client.dto.response.*;
import com.sp.syncPoint.common.exception.InternalAuthCallException;
import com.sp.syncPoint.common.exception.RefreshTokenInvalidException;
import com.sp.syncPoint.feature.wallet.dto.request.ResendPhoneVerificationRequest;
import com.sp.syncPoint.feature.wallet.dto.request.VerifyPhoneRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class InternalAuthClient {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthClient.class);
    private static final String GENERIC_DOWNSTREAM_ERROR = "internal-auth-server request failed";

    private final RestClient restClient;
    private final String basicAuthHeader;
    private final String clientId;
    private final String resetSecret;
    private final ObjectMapper objectMapper;

    private static final String WIPE_CONFIRMATION = "WIPE-EVERYTHING";

    public InternalAuthClient(
            @Value("${internal-auth.base-url}") String baseUrl,
            @Value("${internal-auth.client-id}") String clientId,
            @Value("${internal-auth.client-secret}") String clientSecret,
            @Value("${internal-auth.reset.secret:}") String resetSecret,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        this.clientId = clientId;
        this.resetSecret = resetSecret;
        this.objectMapper = objectMapper;
    }

    private void handleDownstreamError(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatus status = resolveStatus(response);
        throw new InternalAuthCallException(status, extractMessage(response, status));
    }

    private void handleRefreshTokenError(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatus status = resolveStatus(response);
        String message = extractMessage(response, status);
        if (status == HttpStatus.UNAUTHORIZED) {
            throw new RefreshTokenInvalidException(message);
        }
        throw new InternalAuthCallException(status, message);
    }

    private HttpStatus resolveStatus(ClientHttpResponse response) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String extractMessage(ClientHttpResponse response, HttpStatus status) {
        try {
            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                InternalAuthApiResponse<?> parsed = objectMapper.readValue(body, InternalAuthApiResponse.class);
                if (parsed.getMessage() != null && !parsed.getMessage().isBlank()) {
                    return parsed.getMessage();
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse internal-auth-server error body ({}): {}", status, e.getMessage());
        }
        return GENERIC_DOWNSTREAM_ERROR + " (" + status.value() + ")";
    }

    public InternalAuthApiResponse<RegisterResponse> register(RegisterRequest request) {
        return restClient.post()
                .uri("/auth/register")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<RegisterResponse>>() {});
    }

    public InternalAuthApiResponse<InternalAuthLoginResponse> login(LoginRequest request) {
        return restClient.post()
                .uri("/auth/login")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<InternalAuthLoginResponse>>() {});
    }

    public InternalAuthApiResponse<LoginResponse> refreshToken(RefreshTokenRequest request) {
        return restClient.post()
                .uri("/auth/refresh-token")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleRefreshTokenError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<LoginResponse>>() {});
    }

    public InternalAuthApiResponse<ActivationResponse> verifyPhone(VerifyPhoneRequest request) {
        return restClient.post()
                .uri("/auth/verify-phone")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<ActivationResponse>>() {});
    }

    public InternalAuthApiResponse<ActivationResponse> resendPhoneVerification(ResendPhoneVerificationRequest request) {
        return restClient.post()
                .uri("/auth/resend-phone-verification")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<ActivationResponse>>() {});
    }

    public InternalAuthApiResponse<PasswordResetResponse> requestPasswordResetOtp(PasswordResetRequest request) {
        return restClient.post()
                .uri("/password-reset/request")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<PasswordResetResponse>>() {});
    }

    public InternalAuthApiResponse<VerifyOtpResponse> verifyPasswordResetOtp(VerifyOtpRequest request) {
        return restClient.post()
                .uri("/password-reset/verify-otp")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<VerifyOtpResponse>>() {});
    }

    public InternalAuthApiResponse<PasswordResetResponse> completeReset(CompletePasswordResetRequest request) {
        return restClient.post()
                .uri("/password-reset/complete")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<PasswordResetResponse>>() {});
    }

    public InternalAuthApiResponse<LogoutResponse> logout(String accessToken) {
        return restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/session")
                        .build())
                .header(HttpHeaders.AUTHORIZATION,"Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<LogoutResponse>>() {});
    }

    public boolean isSessionLive(String username) {
        InternalAuthApiResponse<com.sp.syncPoint.client.dto.response.SessionStatusResponse> response = restClient.post()
                .uri("/auth/session-status")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(java.util.Map.of("username", username))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<com.sp.syncPoint.client.dto.response.SessionStatusResponse>>() {});
        return response != null && response.getData() != null && response.getData().isActive();
    }

    public void deleteUser(String internalAuthUserId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/user")
                        .queryParam("internalAuthUserId", internalAuthUserId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<Void>>() {});
    }

    public InternalAuthApiResponse<TenantWipeResponse> wipeTenantData() {
        return restClient.post()
                .uri("/admin/reset/wipe-tenant")
                .header("X-Reset-Secret", resetSecret)
                .body(new TenantWipeRequest(clientId, WIPE_CONFIRMATION))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<TenantWipeResponse>>() {});
    }
}