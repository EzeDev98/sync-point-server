package com.sp.syncPoint.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for POST {internal-auth}/admin/reset/wipe-tenant. appId is
 * always this app's own internal-auth.client-id ("sync-point") - never
 * caller-supplied, so this call can only ever wipe SyncPoint's own tenant
 * data, never another app's. See InternalAuthClient#wipeTenantData.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantWipeRequest {
    private String appId;
    private String confirmation;
}
