package com.sp.syncPoint.feature.session.service;

import com.sp.syncPoint.feature.session.dto.LogoutResponse;

public interface LogoutService {
    LogoutResponse logout(String sessionId);
}
