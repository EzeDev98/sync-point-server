package com.sp.syncPoint.feature.session.service.impl;

import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.feature.session.model.UserSession;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import com.sp.syncPoint.feature.session.repository.UserSessionRepository;
import com.sp.syncPoint.feature.session.dto.InvalidatedSessionResponse;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.sp.syncPoint.common.constants.GeneralConstants.*;

@Slf4j
@Service
public class SessionManagementServiceImpl implements SessionManagementService {

    private final UserSessionRepository userSessionRepository;

    private final UserRepository userRepository;
    private final InternalAuthClient authClient;

    public SessionManagementServiceImpl(UserSessionRepository userSessionRepository,
                                        UserRepository userRepository,
                                        InternalAuthClient authClient) {
        this.userSessionRepository = userSessionRepository;
        this.userRepository = userRepository;
        this.authClient = authClient;
    }

    @Override
    @Transactional
    public void createSession(Long userId, String phoneNumber, String token) {
        userSessionRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber)
                .ifPresent(existing -> closeSession(existing, REPLACED_BY_NEW_LOGIN));

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setPhoneNumber(phoneNumber);
        session.setToken(token);
        session.setActive(true);
        session.setCreatedAt(LocalDateTime.now());

        userSessionRepository.save(session);
        log.info("New session created for user: {}", phoneNumber);
    }

    @Override
    @Transactional
    public void invalidateSession(String token) {
        userSessionRepository.findByToken(token)
                .ifPresentOrElse(
                        session -> {
                            closeSession(session, LOGOUT);
                            log.info("Session invalidated for user: {}", session.getPhoneNumber());
                        },
                        () -> log.warn("No session found for token during logout")
                );
    }

    @Override
    @Transactional
    public InvalidatedSessionResponse invalidateUserSessionByAdmin(String phoneNumber) {
        return userSessionRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber)
                .map(session -> {
                    closeSession(session, ADMIN_ACTION);
                    log.info("Session invalidated by admin for user: {}", session.getPhoneNumber());
                    return new InvalidatedSessionResponse(HttpServletResponse.SC_OK, "Session successfully invalidated for user with phone number " + phoneNumber);
                })
                .orElseGet(() -> {
                    log.warn("No active session found for {} during admin invalidation — nothing to update", phoneNumber);
                    return new InvalidatedSessionResponse(HttpServletResponse.SC_NOT_FOUND, "No active session found for user with phone number " + phoneNumber);
                });
    }

    @Override
    @Transactional
    public void updateSessionOnRefresh(String phoneNumber, String newToken) {
        userSessionRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber)
                .ifPresentOrElse(
                        session -> {
                            session.setToken(newToken);
                            session.setCreatedAt(LocalDateTime.now());
                            userSessionRepository.save(session);
                            log.info("Session refreshed and TTL reset for user: {}", phoneNumber);
                        },
                        () -> log.warn("No active session found for {} during refresh — nothing to update", phoneNumber)
                );
    }

    @Override
    @Transactional
    public boolean hasActiveSession(String phoneNumber) {
        return userSessionRepository.findByPhoneNumberAndIsActiveTrue(phoneNumber)
                .map(session -> {
                    LocalDateTime expiryThreshold = session.getCreatedAt().plusDays(SESSION_TTL_DAYS);
                    if (LocalDateTime.now().isAfter(expiryThreshold)) {
                        log.info("Session for {} exceeded {}-day TTL — expiring", phoneNumber, SESSION_TTL_DAYS);
                        closeSession(session, TTL_EXPIRED);
                        return false;
                    }
                    if (!isStillLiveInInternalAuth(phoneNumber)) {
                        log.info("Session for {} was ended in internal-auth — releasing the local record", phoneNumber);
                        closeSession(session, ADMIN_ACTION);
                        return false;
                    }
                    return true;
                }).orElse(false);
    }

    private boolean isStillLiveInInternalAuth(String phoneNumber) {
        var user = userRepository.findByPhoneNumber(phoneNumber);
        if (user.isEmpty()) {
            return true;
        }
        try {
            return authClient.isSessionLive(user.get().getUsername());
        } catch (RuntimeException ex) {
            log.warn("Could not confirm session status with internal-auth for {}: {}", phoneNumber, ex.getMessage());
            return true;
        }
    }

    private void closeSession(UserSession session, String reason) {
        session.setActive(false);
        session.setEndedAt(LocalDateTime.now());
        session.setEndReason(reason);
        userSessionRepository.save(session);
    }
}