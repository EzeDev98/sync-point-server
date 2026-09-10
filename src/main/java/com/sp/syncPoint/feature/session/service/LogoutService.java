package com.sp.syncPoint.feature.session.service;


import com.sp.syncPoint.client.dto.response.LogoutResponse;

public interface LogoutService {
    LogoutResponse logout(String sessionId);
}
