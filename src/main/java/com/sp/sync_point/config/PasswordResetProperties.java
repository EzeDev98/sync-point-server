package com.sp.sync_point.config;

import com.sp.sync_point.constants.PasswordOtp;
import com.sp.sync_point.constants.PasswordToken;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized, typed configuration for the password-reset OTP flow.
 * Backed by "password-reset.*" properties (see application.properties) so
 * every configurable value (OTP length, expiry, attempts, cooldown, token
 * lifetime) lives in exactly one place instead of being scattered as magic
 * numbers across services.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {
    private PasswordOtp otp = new PasswordOtp();
    private PasswordToken token = new PasswordToken();
}