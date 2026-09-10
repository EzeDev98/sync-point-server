package com.sp.syncPoint.feature.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    @JsonProperty("token")
    private String token;

    @JsonProperty("refreshToken")
    private String refreshToken;

    @JsonProperty("accessTokenExpiresIn")
    private Long accessTokenExpiresIn;

    @JsonProperty("refreshTokenExpiresIn")
    private Long refreshTokenExpiresIn;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("user_ref_id")
    private String userRefId;

    @JsonProperty("wallet_id")
    private String walletId;

    @JsonProperty("mirror_balance")
    private BigDecimal mirrorBalance;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("first_name")
    private String firstname;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("last_name")
    private String lastName;

    private BigDecimal offlineLimit;

    private boolean status;
}