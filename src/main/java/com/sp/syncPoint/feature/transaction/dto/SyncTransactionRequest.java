package com.sp.syncPoint.feature.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncTransactionRequest {

    @NotBlank(message = "transactionRef is required")
    @JsonProperty("transactionRef")
    private String transactionRef;

    @NotBlank(message = "requestRef is required")
    @JsonProperty("requestRef")
    private String requestRef;

    @NotBlank(message = "type is required")
    @JsonProperty("type")
    private String type;

    @NotBlank(message = "entryType is required")
    @Pattern(regexp = "DEBIT|CREDIT", message = "entryType must be DEBIT or CREDIT")
    @JsonProperty("entryType")
    private String entryType;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "NGN", message = "Only NGN is supported")
    @JsonProperty("currency")
    private String currency;

    @NotBlank(message = "senderWalletId is required")
    @JsonProperty("senderWalletId")
    private String senderWalletId;

    @NotBlank(message = "senderPhone is required")
    @JsonProperty("senderPhone")
    private String senderPhone;

    @JsonProperty("senderName")
    private String senderName;

    @NotBlank(message = "receiverWalletId is required")
    @JsonProperty("receiverWalletId")
    private String receiverWalletId;

    @NotBlank(message = "receiverPhone is required")
    @JsonProperty("receiverPhone")
    private String receiverPhone;

    @JsonProperty("receiverName")
    private String receiverName;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "100.00", message = "Minimum transfer amount is ₦100")
    @DecimalMax(value = "1000000.00", message = "Maximum transfer amount is ₦1,000,000")
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("description")
    private String description;

    @NotNull(message = "createdAt is required")
    @JsonProperty("createdAt")
    private Instant createdAt;
}