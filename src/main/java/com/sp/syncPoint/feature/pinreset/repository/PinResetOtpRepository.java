package com.sp.syncPoint.feature.pinreset.repository;

import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.feature.pinreset.model.PinResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PinResetOtpRepository extends JpaRepository<PinResetOtp, Long> {
    Optional<PinResetOtp> findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, PasswordResetStatus status);
}