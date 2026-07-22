package com.sp.sync_point.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPinRequest {
    private String walletId;
    private String unverifiedPin;
}