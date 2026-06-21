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
}
