package com.sp.syncPoint.feature.auth.service;

import com.sp.syncPoint.client.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse refreshAccessToken(String refreshToken);
}
