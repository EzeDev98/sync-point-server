package com.sp.sync_point.model;

import com.sp.sync_point.enums.PasswordResetStatus;
import com.sp.sync_point.service.ResettableOtp;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Mirrors PasswordResetOtp's shape but tracks the OTP lifecycle for a wallet
 * PIN reset instead of an account password reset. Kept as a separate table
 * (rather than reusing password_reset_otps with a "purpose" column) so the
 * already-working password reset flow isn't touched by this addition.
 *
 * Reuses the existing, purpose-agnostic PasswordResetStatus enum
 * (ACTIVE/VERIFIED/EXPIRED/INVALIDATED) since those values aren't
 * password-specific.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pin_reset_otps")
public class PinResetOtp implements ResettableOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PasswordResetStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;
}