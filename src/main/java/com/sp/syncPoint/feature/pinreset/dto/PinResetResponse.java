package com.sp.syncPoint.feature.pinreset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PinResetResponse {
    private String message;
    private int statusCode;
}