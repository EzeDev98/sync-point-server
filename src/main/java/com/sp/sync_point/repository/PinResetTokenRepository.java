package com.sp.sync_point.repository;

import com.sp.sync_point.model.PinResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PinResetTokenRepository extends JpaRepository<PinResetToken, Long> {
    Optional<PinResetToken> findByTokenHash(String tokenHash);
}