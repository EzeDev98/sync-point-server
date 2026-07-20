package com.sp.sync_point.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_wallet")
public class SystemWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, unique = true)
    private String walletId;

    @Column(name = "name")
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "initial_fund", nullable = false)
    private BigDecimal initialFund;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "total_disbursed", nullable = false)
    private BigDecimal totalDisbursed = BigDecimal.ZERO;

    @Column(name = "total_reclaimed", nullable = false)
    private BigDecimal totalReclaimed = BigDecimal.ZERO;

    @Column(name = "total_users_funded", nullable = false)
    private Long totalUsersFunded = 0L;

    @Column(name = "active_funded_users", nullable = false)
    private Long activeFundedUsers = 0L;

    @Column(name = "current_liability", nullable = false)
    private BigDecimal currentLiability = BigDecimal.ZERO;

    @Version
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}