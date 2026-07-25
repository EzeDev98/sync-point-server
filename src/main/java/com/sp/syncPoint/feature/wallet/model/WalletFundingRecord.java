package com.sp.syncPoint.feature.wallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "wallet_funding_record",indexes = {
        @Index(name = "idx_wallet_funding_user_id", columnList = "userId"),
        @Index(name = "idx_wallet_funding_wallet_id", columnList = "walletId"),
        @Index(name = "idx_wallet_funding_reclaimed", columnList = "reclaimed")
})
public class WalletFundingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String walletId;

    private BigDecimal amount;

    private boolean reclaimed;

    private LocalDateTime fundedAt;

    private LocalDateTime reclaimedAt;
}