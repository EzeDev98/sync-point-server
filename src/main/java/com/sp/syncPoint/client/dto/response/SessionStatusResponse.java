package com.sp.syncPoint.client.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/** internal-auth's answer to POST /auth/session-status. */
@Data
@NoArgsConstructor
public class SessionStatusResponse {
    private boolean active;
}
