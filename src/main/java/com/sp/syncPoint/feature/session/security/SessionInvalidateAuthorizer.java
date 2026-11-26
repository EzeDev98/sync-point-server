package com.sp.syncPoint.feature.session.security;

import com.sp.syncPoint.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class SessionInvalidateAuthorizer {

    private final String configuredSecret;

    public SessionInvalidateAuthorizer(@Value("${app.session-invalidate.secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    public void authorize(String providedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("[SessionInvalidate] Rejected - endpoint disabled (no app.session-invalidate.secret configured)");
            throw new UnauthorizedException("Session-invalidate endpoint is disabled. Set app.session-invalidate.secret to enable it.");
        }
        if (!secretMatches(providedSecret)) {
            log.warn("[SessionInvalidate] Rejected - missing or invalid X-Admin-Secret");
            throw new UnauthorizedException("Invalid or missing admin secret.");
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
