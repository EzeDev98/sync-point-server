package com.sp.syncPoint.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for POST /api/v1/auth/register (Step 10A). phoneNumber and
 * email are both optional individually - RegistrationValidator enforces
 * that at least one is present, mirroring User's DB-level
 * ck_users_phone_or_email constraint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String username;
    private String phoneNumber;
    private String email;
    private String password;
    private String confirmPassword;
}
