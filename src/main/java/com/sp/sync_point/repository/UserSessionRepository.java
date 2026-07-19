package com.sp.sync_point.repository;

import com.sp.sync_point.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByToken(String token);
    Optional<UserSession> findByPhoneNumberAndIsActiveTrue(String phoneNumber);
    Optional<UserSession> findByPhoneNumber(String phoneNumber);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.token = :token")
    void deleteByToken(@Param("token") String token);
}