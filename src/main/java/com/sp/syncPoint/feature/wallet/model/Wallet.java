package com.sp.syncPoint.feature.wallet.model;

import com.sp.syncPoint.feature.user.model.User;

import com.sp.syncPoint.common.money.MoneyConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.sp.syncPoint.common.constants.GeneralConstants.OFFLINE_LIMIT;
import static com.sp.syncPoint.common.constants.GeneralConstants.ZERO;

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

    /**
     * The device's ECDSA P-256 public key (SPKI/X.509 DER, base64), used to
     * verify the signatures on offline P2P transfers this wallet sends.
     * <p>
     * Registered via {@code POST /wallet/signing-key} — at first PIN-set for
     * new wallets, and once on next login for wallets that predate signing.
     * Nullable so existing rows migrate cleanly; a null key simply means the
     * wallet hasn't enrolled yet and its transfers cannot be verified (and so
     * cannot be settled) until it does. Length is generous headroom over the
     * ~124-char base64 SPKI of a P-256 key.
     */
    @Column(name = "signing_public_key", length = 512)
    private String signingPublicKey;

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