package com.sp.sync_point.service;

public interface SessionManagementService {
    void createSession(Long userId, String phoneNumber, String token);
    void invalidateSession(String token);
    void updateSessionOnRefresh(String phoneNumber, String newToken);
    boolean hasActiveSession(String phoneNumber);
}
