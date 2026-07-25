package com.sp.syncPoint.feature.session.service.impl;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.feature.session.model.UserSession;
import com.sp.syncPoint.feature.session.repository.UserSessionRepository;
import com.sp.syncPoint.feature.session.dto.LogoutResponse;
import com.sp.syncPoint.feature.session.service.LogoutService;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogoutServiceImpl implements LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutServiceImpl.class);
    private final UserSessionRepository userSessionRepo;
    private final SessionManagementService sessionManagementService;

    public LogoutServiceImpl(UserSessionRepository userSessionRepo, SessionManagementService sessionManagementService) {
        this.userSessionRepo = userSessionRepo;
        this.sessionManagementService = sessionManagementService;
    }

    @Transactional
    @Override
    public LogoutResponse logout(String token) {

        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted logout with null or empty token");
            throw new BadRequestException("Invalid token");
        }

        try {

            UserSession session = userSessionRepo.findByToken(token)
                    .orElseThrow(() -> new BadRequestException("No active session found"));

            sessionManagementService.invalidateSession(token);
            log.info("Session deleted for user: {}", session.getPhoneNumber());

            return new LogoutResponse("Logout successful", HttpServletResponse.SC_OK);
        } catch (BadRequestException ex) {
            log.warn("Logout failed: {}", ex.getMessage());
            return new LogoutResponse(ex.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Error during logout: {}", ex.getMessage());
            return new LogoutResponse("Logout failed. Please try again.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}