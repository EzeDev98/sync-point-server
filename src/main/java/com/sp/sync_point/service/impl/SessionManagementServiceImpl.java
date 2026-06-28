package com.sp.sync_point.service.impl;

import com.sp.sync_point.model.UserSession;
import com.sp.sync_point.repository.UserSessionRepository;
import com.sp.sync_point.service.SessionManagementService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
                .ifPresent(existing -> {
                    userSessionRepository.delete(existing);
                    log.info("Deleted previous session for user: {}", phoneNumber);
                });

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setPhoneNumber(phoneNumber);
        session.setToken(token);
        session.setActive(true);
        session.setCreatedAt(LocalDateTime.now());

        userSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void invalidateSession(String token) {
        userSessionRepository.findByToken(token)
                .ifPresentOrElse(
                        session -> {
                            userSessionRepository.delete(session);
                            log.info("Session deleted successfully for user: {}", session.getPhoneNumber());
                        },
                        () -> log.warn("No session found for token during logout")
                );
    }

    @Override
    @Transactional
    public void updateSessionToken(String oldToken, String newToken) {
        userSessionRepository.findByToken(oldToken)
                .ifPresentOrElse(
                        session -> {
                            session.setToken(newToken);
                            session.setCreatedAt(LocalDateTime.now());
                            userSessionRepository.save(session);
                            log.info("Session token updated for user: {}", session.getPhoneNumber());
                        },
                        () -> log.warn("No session found for old token during refresh — skipping update")
                );
    }

    @Override
    public boolean isSessionActive(String token) {
        return userSessionRepository.findByToken(token).isPresent();
    }
}