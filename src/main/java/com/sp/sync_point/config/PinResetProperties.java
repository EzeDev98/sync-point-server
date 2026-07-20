package com.sp.sync_point.config;

import com.sp.sync_point.constants.PinOtp;
import com.sp.sync_point.constants.PinToken;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized, typed configuration for the PIN-reset OTP flow. Mirrors
 * PasswordResetProperties but kept as its own class/prefix ("pin-reset.*")
 * so PIN and password reset timings can be tuned independently.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pin-reset")
public class PinResetProperties {
    private PinOtp otp = new PinOtp();
    private PinToken token = new PinToken();
}