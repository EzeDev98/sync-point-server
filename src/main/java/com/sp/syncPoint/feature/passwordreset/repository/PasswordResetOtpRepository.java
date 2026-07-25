package com.sp.syncPoint.feature.passwordreset.repository;

import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.feature.passwordreset.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, PasswordResetStatus status);
}