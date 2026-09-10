package com.sp.syncPoint.common.reset.delivery;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development-friendly OtpDeliveryService implementation.
 *
 * Active by default (app.otp.provider=logging, or unset) so the OTP delivery
 * abstraction has a working implementation without contaminating
 * PasswordResetServiceImpl with provider-specific detail. Logs the OTP
 * instead of sending it over SMS.
 *
 * IMPORTANT: logging the raw OTP is strictly a development convenience and
 * is gated behind app.otp.log-to-console, which must be disabled (false)
 * in production. To use a real provider instead, set app.otp.provider to
 * that provider's name (e.g. "contiguity") — see ContiguityOtpDeliveryService.
 * PasswordResetServiceImpl requires no changes either way.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "logging", matchIfMissing = true)
public class LoggingOtpDeliveryService implements OtpDeliveryService {

    @Value("${app.otp.log-to-console:false}")
    private boolean logToConsole;

    @Override
    public void sendResetOtp(String phoneNumber, String otp) {
        // Never log the raw OTP unless explicitly enabled for local development.
        if (logToConsole) {
            log.info("[DEV ONLY] Password reset OTP for {}: {}", phoneNumber, otp);
        } else {
            log.info("Password reset OTP generated for {} (delivery provider not configured)", phoneNumber);
        }
    }
}