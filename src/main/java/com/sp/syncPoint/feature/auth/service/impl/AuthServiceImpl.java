package com.sp.syncPoint.feature.auth.service.impl;

import com.sp.syncPoint.client.dto.request.RefreshTokenRequest;
import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.dto.response.LoginResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.feature.auth.service.AuthService;
import org.springframework.stereotype.Component;

@Component
public class AuthServiceImpl implements AuthService {

    private final InternalAuthClient authClient;

    public AuthServiceImpl(InternalAuthClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public LoginResponse refreshAccessToken(String refreshToken) {
        InternalAuthApiResponse<LoginResponse> loginResponse = authClient.refreshToken(new RefreshTokenRequest(refreshToken));
        return loginResponse.getData();
    }
}
