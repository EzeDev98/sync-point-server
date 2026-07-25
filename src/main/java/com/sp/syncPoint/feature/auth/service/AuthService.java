package com.sp.syncPoint.feature.auth.service;

import org.keycloak.representations.AccessTokenResponse;

public interface AuthService {
    AccessTokenResponse authenticateUser(String email, String password);
    AccessTokenResponse refreshAccessToken(String refreshToken);
}
