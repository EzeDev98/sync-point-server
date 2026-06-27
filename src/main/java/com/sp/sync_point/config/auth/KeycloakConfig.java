package com.sp.sync_point.config.auth;

import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.exception.KeycloakAuthenticationException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KeycloakConfig {

    private static final Logger log  = LoggerFactory.getLogger(KeycloakConfig.class);

    @Value("${keycloak.client-id}")
    private String adminClientId;

    @Value("${keycloak.client-secret}")
    private String adminClientSecret;

    @Value("${keycloak.server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Bean
    public org.keycloak.admin.client.Keycloak keycloak(){

        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .build();
    }

    public AccessTokenResponse keycloakAuthentication(String username, String password) {
        try {
            org.keycloak.admin.client.Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm(realm)
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(adminClientId)
                    .clientSecret(adminClientSecret)
                    .username(username)
                    .password(password)
                    .build();

            AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
            log.info("Keycloak authentication successful for user: {}", username);
            return tokenResponse;

        } catch (NotAuthorizedException e) {
            log.error("Keycloak authentication failed - Invalid credentials for user: {}", username, e);
            throw new KeycloakAuthenticationException("Invalid username or password", e);

        } catch (BadRequestException e) {
            log.error("Keycloak bad request for user: {}. Error: {}", username, e.getMessage(), e);
            throw new KeycloakAuthenticationException("Bad request to Keycloak: " + e.getMessage(), e);

        } catch (ProcessingException e) {
            log.error("Keycloak connection error for user: {}. Error: {}", username, e.getMessage(), e);
            throw new KeycloakAuthenticationException("Cannot connect to Keycloak server", e);

        } catch (Exception ex) {
            log.error("Unexpected Keycloak error for user: {}. Exception type: {}, Message: {}", username, ex.getClass().getName(), ex.getMessage(), ex);
            throw new KeycloakAuthenticationException("Failed to authenticate with Keycloak: " + ex.getMessage(), ex);
        }
    }

    /**
     * Exchange a refresh token for a new AccessTokenResponse.
     * Uses a direct HTTP call to the Keycloak token endpoint — the Keycloak
     * admin client does not expose a clean refresh-grant API, so RestTemplate
     * is the most straightforward approach here.
     *
     * Throws KeycloakAuthenticationException with a 401-mapped message when
     * the refresh token is expired or invalid so the caller can return 401
     * to the frontend and trigger a re-login.
     */
    public AccessTokenResponse refreshAccessToken(String refreshToken) {
        String tokenEndpoint = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("client_id", adminClientId);
        body.add("client_secret", adminClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {

            ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(tokenEndpoint, request, AccessTokenResponse.class);
            log.info("Keycloak token refresh successful");
            return response.getBody();

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Refresh token expired or invalid");
            throw new KeycloakAuthenticationException("Refresh token expired or invalid", e);
        } catch (ProcessingException e) {
            log.error("Keycloak connection error during token refresh: {}", e.getMessage(), e);
            throw new KeycloakAuthenticationException("Cannot connect to Keycloak server", e);
        } catch (Exception e) {
            log.error("Unexpected error during token refresh: {}", e.getMessage(), e);
            throw new KeycloakAuthenticationException("Token refresh failed: " + e.getMessage(), e);
        }
    }
}
