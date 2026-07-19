package com.sp.sync_point.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The resetToken returned here is a short-lived, single-use,
 * password-reset-only authorization token — distinct from Keycloak
 * access/refresh tokens and normal session tokens. It grants no
 * application access beyond the /password-reset/complete call.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VerifyOtpResponse {
    private String resetToken;
}