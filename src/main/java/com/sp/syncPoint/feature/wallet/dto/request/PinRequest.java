package com.sp.syncPoint.feature.wallet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PinRequest {
    private String phoneNumber;
    private String oldPin;
    private String pin;
    private String confirmNewPin;
}
