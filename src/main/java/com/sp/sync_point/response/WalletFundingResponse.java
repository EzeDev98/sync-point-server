package com.sp.sync_point.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WalletFundingResponse {
    private int statusCode;
    private String message;
    private BigDecimal amount;
    private String transactionRef;
}
