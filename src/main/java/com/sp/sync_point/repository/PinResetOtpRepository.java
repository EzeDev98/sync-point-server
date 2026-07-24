package com.sp.sync_point.repository;

import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.model.PinResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PinResetOtpRepository extends JpaRepository<PinResetOtp, Long> {
    Optional<PinResetOtp> findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, PasswordResetStatus status);
}