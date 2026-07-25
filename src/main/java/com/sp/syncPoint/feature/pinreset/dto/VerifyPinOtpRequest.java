package com.sp.syncPoint.feature.pinreset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPinOtpRequest {
    private String phoneNumber;
    private String otp;
}