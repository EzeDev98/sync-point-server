package com.sp.syncPoint.feature.pinreset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompletePinResetRequest {
    private String resetToken;
    private String pin;
    private String confirmNewPin;
}