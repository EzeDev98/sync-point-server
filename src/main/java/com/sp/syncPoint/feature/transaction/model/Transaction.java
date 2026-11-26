package com.sp.syncPoint.feature.transaction.model;

import com.sp.syncPoint.common.money.MoneyConfig;
import com.sp.syncPoint.feature.transaction.enums.EntryType;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_request_ref_entry_type",
                        columnNames = {"request_ref", "entry_type"}
                )
        },
        indexes = {
                @Index(name = "idx_transaction_reference", columnList = "transaction_reference"),
                @Index(name = "idx_sender_wallet_phone", columnList = "sender_wallet_phone_number"),
                @Index(name = "idx_receiver_wallet_phone", columnList = "receiver_wallet_phone_number"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_request_ref", columnList = "request_ref")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, updatable = false)
    private String transactionReference;

    @Column(name = "request_ref", nullable = false, updatable = false)
    private String requestRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "sender_wallet_id")
    private String senderWalletId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_wallet_phone_number")
    private String senderWalletPhoneNumber;

    @Column(name = "sender_balance_before",
            precision = MoneyConfig.MONETARY_PRECISION,
            scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal senderBalanceBefore;

    @Column(name = "sender_balance_after",
            precision = MoneyConfig.MONETARY_PRECISION,
            scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal senderBalanceAfter;

    @Column(name = "receiver_wallet_id")
    private String receiverWalletId;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_wallet_phone_number")
    private String receiverWalletPhoneNumber;

    @Column(name = "receiver_balance_before",
            precision = MoneyConfig.MONETARY_PRECISION,
            scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal receiverBalanceBefore;

    @Column(name = "receiver_balance_after",
            precision = MoneyConfig.MONETARY_PRECISION,
            scale = MoneyConfig.MONETARY_SCALE)
    private BigDecimal receiverBalanceAfter;

    @Column(name = "amount",
            precision = MoneyConfig.MONETARY_PRECISION,
            scale = MoneyConfig.MONETARY_SCALE,
            nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "sync_id")
    private String syncId;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private Long version;
}