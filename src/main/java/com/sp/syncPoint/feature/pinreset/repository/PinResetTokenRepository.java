package com.sp.syncPoint.feature.pinreset.repository;

import com.sp.syncPoint.feature.pinreset.model.PinResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PinResetTokenRepository extends JpaRepository<PinResetToken, Long> {
    Optional<PinResetToken> findByTokenHash(String tokenHash);
}