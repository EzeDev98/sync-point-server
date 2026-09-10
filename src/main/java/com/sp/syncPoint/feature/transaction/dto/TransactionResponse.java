package com.sp.syncPoint.feature.transaction.dto;

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
public class TransactionResponse {

    private Long id;

    private String transactionReference;

    private String transactionType;

    private String requestRef;

    private String currency;

    private String entryType;

    private String type;

    private String senderWalletId;

    private String senderName;

    private String senderWalletPhoneNumber;

    private String receiverWalletId;

    private String receiverName;

    private String receiverPhoneNumber;

    private String description;

    private String status;

    private String syncId;

    private BigDecimal amount;

    private BigDecimal senderBalanceBeforeTransfer;

    private BigDecimal receiverBalanceBeforeTransfer;

    private BigDecimal senderBalanceAfterTransfer;

    private BigDecimal receiverBalanceAfterTransfer;

    private String classification;

    private String failureReason;

    private String syncTime;

    private Instant createdAt;

    private LocalDateTime completedAt;
}
