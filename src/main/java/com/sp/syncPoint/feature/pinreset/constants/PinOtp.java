package com.sp.syncPoint.feature.pinreset.constants;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PinOtp {
    private int length = 6;
    private int expirationMinutes = 5;
    private int maxAttempts = 5;
    private int resendCooldownSeconds = 60;
}
