package com.sp.sync_point.service.impl;

import com.sp.sync_point.config.auth.KeycloakConfig;
import com.sp.sync_point.exception.AuthenticationException;
import com.sp.sync_point.exception.RefreshTokenInvalidException;
import com.sp.sync_point.exception.ServiceUnavailableException;
import com.sp.sync_point.service.AuthService;
import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final KeycloakConfig keycloak;

    public AuthServiceImpl(KeycloakConfig keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public AccessTokenResponse authenticateUser(String username, String password) {
        try {

            log.debug("Attempting Keycloak auth with username: {}", username);
            AccessTokenResponse accessTokenResponse = keycloak.keycloakAuthentication(username, password);
            log.info("Keycloak authentication successful for username: {}", username);
            return accessTokenResponse;
        } catch (Exception ex) {
            log.error("Error during keycloak authentication: {}", ex.getMessage());
            throw new AuthenticationException("Authentication failed");
        }
    }

    @Override
    public AccessTokenResponse refreshAccessToken(String refreshToken) {
        try {
            log.debug("Attempting Keycloak token refresh");
            AccessTokenResponse tokenResponse = keycloak.refreshAccessToken(refreshToken);
            log.info("Token refresh successful");
            return tokenResponse;
        } catch (RefreshTokenInvalidException | ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during token refresh: {}", ex.getMessage());
            throw new AuthenticationException("Token refresh failed");
        }
    }
}
