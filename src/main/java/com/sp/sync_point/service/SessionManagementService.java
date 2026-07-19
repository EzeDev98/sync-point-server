package com.sp.sync_point.service;

import com.sp.sync_point.response.InvalidatedSessionResponse;

public interface SessionManagementService {
    void createSession(Long userId, String phoneNumber, String token);
    void invalidateSession(String token);
    InvalidatedSessionResponse invalidateUserSessionByAdmin(String phoneNumber);
    void updateSessionOnRefresh(String phoneNumber, String newToken);
    boolean hasActiveSession(String phoneNumber);
}
