package com.sp.syncPoint.feature.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for the destructive data wipe.
 *
 * @see com.sp.syncPoint.feature.admin.web.AdminResetController
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WipeRequest {

    /** Must equal the required confirmation phrase, or the request is rejected. */
    private String confirmation;

    /** When true, also clears the system_wallet singleton. Defaults to false (preserved). */
    private Boolean includeSystemWallet;
}