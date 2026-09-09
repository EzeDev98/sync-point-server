package com.sp.syncPoint.feature.wallet.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPinRequest {
    private String walletId;
    private String unverifiedPin;
}