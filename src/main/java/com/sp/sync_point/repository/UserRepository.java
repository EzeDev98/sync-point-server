package com.sp.sync_point.repository;

import com.sp.sync_point.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhoneNumberOrEmailOrUsername(String phoneNumber, String email, String username);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByUserRefId(String refId);
    Optional<User> findByUserRefId(String userRefId);
    Optional<User> findByKeycloakId(String keycloakId);
}
