package com.sp.syncPoint.feature.wallet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPhoneRequest {
    private String phoneNumber;
    private String otp;
}
