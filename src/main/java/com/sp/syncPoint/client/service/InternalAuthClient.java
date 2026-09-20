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

    /**
     * Shared error translation for every downstream call in this class.
     * RestClient's default behavior on a 4xx/5xx response is to throw
     * HttpClientErrorException/HttpServerErrorException, which isn't one of
     * GlobalExceptionHandler's modeled exceptions - it used to fall straight
     * through to the generic "unexpected error" 500 handler, silently
     * discarding the real status and message internal-auth-server sent
     * back. This reads that body (the same InternalAuthApiResponse shape
     * this app's own controllers return) and rethrows it as an
     * InternalAuthCallException carrying the real status, so callers see an
     * honest response instead of a masked 500.
     */
    private void handleDownstreamError(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatus status = resolveStatus(response);
        throw new InternalAuthCallException(status, extractMessage(response, status));
    }

    /**
     * Same body-reading as {@link #handleDownstreamError}, but specific to
     * /auth/refresh-token: a rejected refresh token comes back as a 401
     * with a client-safe message, and that case rethrows the existing
     * RefreshTokenInvalidException (already mapped to 401 in
     * GlobalExceptionHandler) instead of the generic
     * InternalAuthCallException, so a stale/expired/already-redeemed
     * refresh token surfaces as the specific, meaningful error it is,
     * rather than an opaque downstream failure.
     */
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

    /**
     * Asks internal-auth to revoke every refresh token / authorization the user
     * holds for this client, so a device that is offline right now cannot
     * silently refresh its way back in after an admin invalidates the session.
     */
    public void revokeUserSessions(String username) {
        restClient.post()
                .uri("/auth/revoke-sessions")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .body(java.util.Map.of("username", username))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .toBodilessEntity();
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
                .onStatus(HttpStatusCode::isError, this::handleDownstreamError)
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<TenantWipeResponse>>() {});
    }
}