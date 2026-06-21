package com.sp.sync_point.service;

import com.sp.sync_point.model.UserSession;

public interface SessionManagementService {
    void createSession(Long userId, String phoneNumber, String token);
    void invalidateSession(String token);
    boolean isSessionActive(String token);
}
