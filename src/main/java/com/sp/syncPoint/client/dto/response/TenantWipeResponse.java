package com.sp.syncPoint.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Response body for POST {internal-auth}/admin/reset/wipe-tenant.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantWipeResponse {
    private String appId;
    private Map<String, Long> deletedCounts;
}
