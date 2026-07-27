package com.sp.syncPoint.feature.admin.security;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.sp.syncPoint.common.constants.GeneralConstants.REQUIRED_CONFIRMATION;

/**
 * Guards the destructive data wipe so the controller holds none of the security
 * logic itself. Answers one question — "is this a legitimate, confirmed,
 * authorized reset request?" — and throws if the answer is no, leaving
 * {@code GlobalExceptionHandler} to shape the error response like everywhere
 * else.
 * <p>
 * Two independent preconditions, both required:
 * <ul>
 *   <li><b>Secret.</b> The caller must present {@code X-Reset-Secret} matching
 *       {@code app.reset.secret}. A blank/unset property disables the endpoint
 *       entirely, so the secret doubles as the on/off switch. The comparison is
 *       constant-time to avoid leaking the secret through response timing.</li>
 *   <li><b>Confirmation phrase.</b> The body must carry the exact phrase, so a
 *       call holding only the secret cannot fire by accident or replay.</li>
 * </ul>
 */
@Slf4j
@Component
public class ResetAuthorizer {

    private final String configuredSecret;

    public ResetAuthorizer(@Value("${app.reset.secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    /**
     * @param providedSecret value of the {@code X-Reset-Secret} header
     * @param confirmation   confirmation phrase from the request body
     * @throws UnauthorizedException if the endpoint is disabled or the secret is missing/invalid
     * @throws BadRequestException   if the confirmation phrase is missing or incorrect
     */
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