package com.sp.syncPoint.common.reset.delivery;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "logging", matchIfMissing = true)
public class LoggingOtpDeliveryService implements OtpDeliveryService {

    @Value("${app.otp.log-to-console:false}")
    private boolean logToConsole;

    @Override
    public void sendResetOtp(String phoneNumber, String otp) {
        if (logToConsole) {
            log.info("[DEV ONLY] Password reset OTP for {}: {}", phoneNumber, otp);
        } else {
            log.info("Password reset OTP generated for {} (delivery provider not configured)", phoneNumber);
        }
    }
}