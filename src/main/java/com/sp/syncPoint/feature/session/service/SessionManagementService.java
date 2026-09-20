package com.sp.syncPoint.feature.session.service;

import com.sp.syncPoint.feature.session.dto.InvalidatedSessionResponse;

public interface SessionManagementService {
    void createSession(Long userId, String phoneNumber, String token);
    void invalidateSession(String token);
    InvalidatedSessionResponse invalidateUserSessionByAdmin(String phoneNumber);
    void updateSessionOnRefresh(String phoneNumber, String newToken);
    boolean hasActiveSession(String phoneNumber);
}
