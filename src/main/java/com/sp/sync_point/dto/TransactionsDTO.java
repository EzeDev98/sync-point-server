package com.sp.sync_point.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionsDTO {

    @JsonProperty("username")
    private String username;

    @JsonProperty("user_wm_id")
    private String userWmId;

    @JsonProperty("user_wallet_phone_number")
    private String userWalletPhoneNumber;

    @JsonProperty("balance_before_transfer")
    private double balanceBeforeTransfer;

    @JsonProperty("amount")
    private double amount;

    @JsonProperty("balance_after_transfer")
    private double balanceAfterTransfer;

    @JsonProperty("classification")
    private String classification;

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("receiver_name")
    private String receiverName;

    @JsonProperty("receiver_wm_id")
    private String receiverWmId;

    @JsonProperty("receiver_phone_number")
    private String receiverPhoneNumber;

    @JsonProperty("description")
    private String description;

    @JsonProperty("type")
    private String type;

    @JsonProperty("date")
    private String date;
}
