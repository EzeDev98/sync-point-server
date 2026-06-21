package com.sp.sync_point.service.impl;

import com.sp.sync_point.enums.UserStatus;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.model.User;
import com.sp.sync_point.model.UserSession;
import com.sp.sync_point.repository.UserRepository;
import com.sp.sync_point.repository.UserSessionRepository;
import com.sp.sync_point.response.LogoutResponse;
import com.sp.sync_point.service.LogoutService;
import com.sp.sync_point.service.SessionManagementService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogoutServiceImpl implements LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutServiceImpl.class);
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepo;
    private final SessionManagementService sessionManagementService;

    public LogoutServiceImpl(UserRepository userRepository, UserSessionRepository userSessionRepo, SessionManagementService sessionManagementService) {
        this.userRepository = userRepository;
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

        LogoutResponse response = new LogoutResponse();

        try {

            UserSession session = userSessionRepo.findByToken(token)
                    .orElseThrow(() -> new BadRequestException("No active session found"));

            User user = userRepository.findByPhoneNumber(session.getPhoneNumber())
                    .orElseThrow(() -> new BadRequestException("User not found"));

            user.setStatus(UserStatus.INACTIVE);
            userRepository.save(user);
            log.info("User {} status set to INACTIVE", session.getPhoneNumber());

            sessionManagementService.invalidateSession(token);
            log.info("Session deleted for user: {}", session.getPhoneNumber());

            response.setMessage("Logout successful");
            response.setStatusCode(200);
            return response;

        } catch (BadRequestException ex) {
            log.warn("Logout failed: {}", ex.getMessage());
            response.setMessage(ex.getMessage());
            response.setStatusCode(400);
            return response;

        } catch (Exception ex) {
            log.error("Error during logout: {}", ex.getMessage());
            response.setMessage("Logout failed. Please try again.");
            response.setStatusCode(500);
            return response;
        }
    }
}