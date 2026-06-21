package com.sp.sync_point.model;

import com.sp.sync_point.config.MoneyConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.sp.sync_point.constants.GeneralConstants.OFFLINE_LIMIT;
import static com.sp.sync_point.constants.GeneralConstants.ZERO;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id")
    private String walletId;

    @Column(name = "pin")
    private String pin;

    @Column(name = "wallet_phone_number")
    private String phoneNumber;

    @Column(name = "offline_limit", precision = MoneyConfig.MONETARY_PRECISION, scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal offlineLimit = OFFLINE_LIMIT;

    @Column(name = "status")
    private boolean status;

    @Column(name = "mirror_balance", precision = MoneyConfig.MONETARY_PRECISION, scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal mirrorBalance = ZERO;

    @Column(name = "pin_last_changed")
    private LocalDateTime pinLastChanged;

    @Column(name = "pin_attempts")
    private Integer pinAttempts = 0;

    @Column(name = "pin_change_attempts")
    private Integer pinChangeAttempts = 0;

    @Column(name = "last_pin_change_attempt")
    private LocalDateTime lastPinChangeAttempt;

    @Column(name = "is_locked")
    private Boolean locked = false;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "login_attempts")
    private Integer loginAttempts = 0;

    @Column(name = "last_login_attempt")
    private LocalDateTime lastLoginAttempt;

    @Column(name = "login_lock_until")
    private LocalDateTime loginLockUntil;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}