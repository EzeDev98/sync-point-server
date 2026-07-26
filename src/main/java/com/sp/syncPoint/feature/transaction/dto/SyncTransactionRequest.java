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

    /**
     * The sender's signature authorizing this exact transfer — base64,
     * ECDSA P-256/SHA-256 over the canonical transfer payload (see
     * {@code TransferSignatureVerifier}). It is what lets a receiver settle a
     * transfer they received without the sender being online: the signature,
     * not "who is calling", proves the sender authorized the money movement.
     * <p>
     * Left un-annotated (no {@code @NotBlank}) on purpose — a missing or bad
     * signature must fail as a single rejected transfer inside the batch, not
     * as a 400 that takes the whole sync down with it. The service verifies it
     * per entry.
     */
    @JsonProperty("signature")
    private String signature;
}