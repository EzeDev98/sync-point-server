package com.sp.syncPoint.feature.pinreset.config;

import com.sp.syncPoint.feature.pinreset.constants.PinOtp;
import com.sp.syncPoint.feature.pinreset.constants.PinToken;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pin-reset")
public class PinResetProperties {
    private PinOtp otp = new PinOtp();
    private PinToken token = new PinToken();
}