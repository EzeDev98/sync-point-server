package com.sp.sync_point.repository;

import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(String phoneNumber, PasswordResetStatus status);
}