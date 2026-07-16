package com.sp.sync_point.service.impl;

import com.sp.sync_point.model.UserSession;
import com.sp.sync_point.repository.UserSessionRepository;
import com.sp.sync_point.service.SessionManagementService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.*;

@Service
public class SessionManagementServiceImpl implements SessionManagementService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagementServiceImpl.class);
    private final UserSessionRepository userSessionRepository;

    public SessionManagementServiceImpl(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
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
                    return true;
                }).orElse(false);
    }

    private void closeSession(UserSession session, String reason) {
        session.setActive(false);
        session.setEndedAt(LocalDateTime.now());
        session.setEndReason(reason);
        userSessionRepository.save(session);
    }
}