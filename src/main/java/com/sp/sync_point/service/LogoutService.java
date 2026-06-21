package com.sp.sync_point.service;

import com.sp.sync_point.response.LogoutResponse;

public interface LogoutService {
    LogoutResponse logout(String sessionId);
}
