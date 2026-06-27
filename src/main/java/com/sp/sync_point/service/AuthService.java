package com.sp.sync_point.service;

import org.keycloak.representations.AccessTokenResponse;

public interface AuthService {
    AccessTokenResponse authenticateUser(String email, String password);
    AccessTokenResponse refreshAccessToken(String refreshToken);
}
