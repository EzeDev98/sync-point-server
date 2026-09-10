package com.sp.syncPoint.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response body for POST /api/v1/auth/login and
 * POST /api/v1/auth/login/verify-otp (Steps 11A/11E).
 * mfaRequired=true (Step 11E) means the password was correct but a login
 * OTP was sent and must be verified via verify-otp before login is
 * actually complete; userId, accessToken, refreshToken, expiresIn and
 * refreshExpiresIn are all omitted in that case, since login hasn't
 * finished yet.
 * <p>Step 12F: accessToken carries a real self-issued bearer token, minted
 * directly by LoginServiceImpl once login actually completes (see that
 * class's javadoc for why this happens in-process rather than via a
 * separate /oauth2/token round trip). Null whenever mfaRequired=true.
 * <p>Step 15B: refreshToken/expiresIn/refreshExpiresIn added alongside
 * accessToken. refreshToken (and refreshExpiresIn) is null even on a
 * successful login if the calling ClientApplication's own grantTypes
 * don't include "refresh_token" - see
 * LoginServiceImpl#mintTokens javadoc. expiresIn/refreshExpiresIn are in
 * whole seconds from issuance, matching the conventional OAuth2 token
 * response shape (RFC 6749 §4.2.2) so client apps can use the same
 * "expires_in" handling they'd already have for any other OAuth2 token
 * response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String message;
    private boolean mfaRequired;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long refreshExpiresIn;
}