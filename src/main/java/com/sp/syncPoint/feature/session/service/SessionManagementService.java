package com.sp.syncPoint.feature.session.service;

import com.sp.syncPoint.feature.session.dto.InvalidatedSessionResponse;

public interface SessionManagementService {
    void createSession(Long userId, String phoneNumber, String token);
    void invalidateSession(String token);
    InvalidatedSessionResponse invalidateUserSessionByAdmin(String phoneNumber);

    /**
     * Admin action: closes the local session row AND revokes the user's refresh
     * tokens in internal-auth. Fails (and rolls back the local close) if the
     * revoke call fails, so the admin never sees a false success.
     */
    InvalidatedSessionResponse terminateUserSessionByAdmin(String phoneNumber);
    void updateSessionOnRefresh(String phoneNumber, String newToken);
    boolean hasActiveSession(String phoneNumber);
}
