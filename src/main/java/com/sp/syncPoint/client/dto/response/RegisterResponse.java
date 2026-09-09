package com.sp.syncPoint.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response body for POST /api/v1/auth/register (Step 10A). Deliberately
 * carries no token/session - per the Step 10A scoping decision, registration
 * only confirms account creation; logging in is a separate step once
 * Step 11's login endpoint exists. Never carries the password or its hash.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String internalAuthUserId;
    private String username;
    private String message;
}
