package com.sp.syncPoint.feature.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WalletTransferRequest {
    private String phoneNumber;
    private String transactionPin;
    private BigDecimal amount;
    private String description;
    private String requestRef;
}
