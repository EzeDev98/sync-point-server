package com.sp.syncPoint.feature.admin.security;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.sp.syncPoint.common.constants.GeneralConstants.REQUIRED_CONFIRMATION;

@Slf4j
@Component
public class ResetAuthorizer {

    private final String configuredSecret;

    public ResetAuthorizer(@Value("${app.reset.secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    public void authorize(String providedSecret, String confirmation) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("[Reset] Rejected — endpoint disabled (no app.reset.secret configured)");
            throw new UnauthorizedException("Reset endpoint is disabled. Set app.reset.secret to enable it.");
        }

        if (!secretMatches(providedSecret)) {
            log.warn("[Reset] Rejected — missing or invalid X-Reset-Secret");
            throw new UnauthorizedException("Invalid or missing reset secret.");
        }

        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            log.warn("[Reset] Rejected — confirmation phrase missing or incorrect");
            throw new BadRequestException("Confirmation required: send {\"confirmation\": \"" + REQUIRED_CONFIRMATION + "\"} in the body.");
        }
    }

    private boolean secretMatches(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        byte[] expected = configuredSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}