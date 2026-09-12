#!/bin/bash
set -e

mkdir -p src/main/java/com/sp/syncPoint/client/dto/request
mkdir -p src/main/java/com/sp/syncPoint/client/dto/response

cat > src/main/java/com/sp/syncPoint/client/dto/request/TenantWipeRequest.java << 'EOF'
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
EOF

cat > src/main/java/com/sp/syncPoint/client/dto/response/TenantWipeResponse.java << 'EOF'
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
EOF

echo "Created 2 new DTO files."
echo ""
echo "Rewriting AdminResetController.java and DatabaseResetService.java"
echo "(these currently exist only as dead, commented-out code referencing"
echo "a removed KeycloakService — replacing with working versions wired to"
echo "the new internal-auth-server downstream call)..."

cat > src/main/java/com/sp/syncPoint/feature/admin/web/AdminResetController.java << 'EOF'
package com.sp.syncPoint.feature.admin.web;

import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.admin.dto.WipeRequest;
import com.sp.syncPoint.feature.admin.security.ResetAuthorizer;
import com.sp.syncPoint.feature.admin.service.DatabaseResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danger-zone endpoint for wiping the whole dataset in a test environment -
 * both SyncPoint's own database and, via {@link DatabaseResetService}'s
 * downstream call, every internal-auth-server record scoped to this app.
 * <p>
 * Deliberately thin: it authorizes via {@link ResetAuthorizer}, delegates
 * the work to {@link DatabaseResetService}, and returns the combined
 * report. A rejected request throws from the guard and is shaped by
 * {@code GlobalExceptionHandler}. The path is opened in
 * {@code WebSecurityConfig} (no login required) so it can be triggered
 * with a single request — the secret is the guard.
 */
@Slf4j
@RestController
@RequestMapping("${app.title}")
@RequiredArgsConstructor
@Tag(name = "Admin Reset API", description = "Destructive maintenance — wipes all data in test environments")
public class AdminResetController {

    private final ResetAuthorizer resetAuthorizer;
    private final DatabaseResetService databaseResetService;

    @PostMapping("/admin/reset/wipe")
    public ResponseEntity<BaseResponse> wipeEverything(
            @RequestHeader(value = "X-Reset-Secret", required = false) String providedSecret,
            @RequestBody(required = false) WipeRequest request) {

        String confirmation = request != null ? request.getConfirmation() : null;
        boolean includeSystemWallet = request != null && Boolean.TRUE.equals(request.getIncludeSystemWallet());

        resetAuthorizer.authorize(providedSecret, confirmation);

        log.warn("[Reset] AUTHORIZED wipe starting. includeSystemWallet={}", includeSystemWallet);

        DatabaseResetService.ResetResult result = databaseResetService.wipeDatabase(includeSystemWallet);
        Map<String, Long> internalAuthCounts = databaseResetService.wipeInternalAuthTenantData();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deletedCounts", result.deletedCounts());
        data.put("internalAuthDeletedCounts", internalAuthCounts);
        data.put("systemWalletWiped", includeSystemWallet);

        return ResponseEntity.ok(new BaseResponse(HttpStatus.OK.value(), "All data wiped. Environment is clean.", data));
    }
}
EOF

cat > src/main/java/com/sp/syncPoint/feature/admin/service/DatabaseResetService.java << 'EOF'
package com.sp.syncPoint.feature.admin.service;

import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.dto.response.TenantWipeResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.feature.pinreset.repository.PinResetOtpRepository;
import com.sp.syncPoint.feature.pinreset.repository.PinResetTokenRepository;
import com.sp.syncPoint.feature.session.repository.UserSessionRepository;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import com.sp.syncPoint.feature.wallet.repository.SystemWalletRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletFundingRecordRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Destructive maintenance service: wipes the entire SyncPoint dataset, then
 * calls down into internal-auth-server's own tenant-wipe endpoint so both
 * sides come back clean together, and the same phone numbers/emails/usernames
 * can register again on either side without a leftover uniqueness conflict.
 * <p>
 * This exists for non-production / test use. Guarded at the controller by a
 * required secret and confirmation phrase (see {@code ResetAuthorizer}) -
 * nothing here checks those guards; do not wire a second caller to this
 * service without reproducing them.
 * <p>
 * Password reset (OTP/token, and the User's password itself) is no longer
 * local to this service — see PasswordResetServiceImpl's class Javadoc:
 * that domain moved entirely to internal-auth-server, and is wiped there,
 * scoped to this app's clientApplicationId, by the downstream call below.
 *
 * <h2>Delete order</h2>
 * Children first, so foreign keys are never violated on any of the three
 * configured databases (Postgres in prod, MySQL in dev, H2 in test):
 * transactions and funding records and local sessions and pin-reset
 * otps/tokens first, then wallets, then users. {@code deleteAllInBatch}
 * issues one DELETE per table and bypasses entity cascades, which is why
 * the order is explicit rather than relying on a User -&gt; Wallet cascade.
 *
 * <h2>System wallet</h2>
 * The {@code system_wallet} singleton is preserved by default: it is
 * provisioning/config data, not user data, and clearing it can leave the
 * app unable to fund or provision new users until it is re-seeded. Pass
 * {@code includeSystemWallet = true} to wipe it too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    private final TransactionRepository transactionRepository;
    private final WalletFundingRecordRepository walletFundingRecordRepository;
    private final UserSessionRepository userSessionRepository;
    private final PinResetOtpRepository pinResetOtpRepository;
    private final PinResetTokenRepository pinResetTokenRepository;
    private final WalletRepository walletRepository;
    private final SystemWalletRepository systemWalletRepository;
    private final UserRepository userRepository;

    private final InternalAuthClient authClient;

    /** Result of the local-database phase: pre-wipe row counts per table. */
    public record ResetResult(Map<String, Long> deletedCounts) {
    }

    /**
     * Delete every SyncPoint row in FK-safe order, inside a single
     * transaction.
     *
     * @param includeSystemWallet also clear the {@code system_wallet} singleton
     * @return pre-wipe counts per table
     */
    @Transactional
    public ResetResult wipeDatabase(boolean includeSystemWallet) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("transactions", transactionRepository.count());
        counts.put("walletFundingRecords", walletFundingRecordRepository.count());
        counts.put("userSessions", userSessionRepository.count());
        counts.put("pinResetOtps", pinResetOtpRepository.count());
        counts.put("pinResetTokens", pinResetTokenRepository.count());
        counts.put("wallets", walletRepository.count());
        if (includeSystemWallet) {
            counts.put("systemWallets", systemWalletRepository.count());
        }
        counts.put("users", userRepository.count());

        log.warn("[Reset] Wiping database. includeSystemWallet={}. Pre-wipe counts={}", includeSystemWallet, counts);

        transactionRepository.deleteAllInBatch();
        walletFundingRecordRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        pinResetOtpRepository.deleteAllInBatch();
        pinResetTokenRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        if (includeSystemWallet) {
            systemWalletRepository.deleteAllInBatch();
        }
        userRepository.deleteAllInBatch();

        log.warn("[Reset] SyncPoint database rows cleared.");

        return new ResetResult(counts);
    }

    /**
     * Wipes every internal-auth-server record scoped to this app
     * (clientApplicationId resolved from internal-auth.client-id,
     * "sync-point") - the User rows, their sessions, email/phone
     * verification records, and password-reset/login-MFA OTPs and tokens.
     * Deliberately not best-effort (unlike the old per-account Keycloak
     * cleanup this replaces): a failure here leaves the two systems
     * out-of-sync, so it propagates as a real error rather than being
     * swallowed.
     */
    public Map<String, Long> wipeInternalAuthTenantData() {
        InternalAuthApiResponse<TenantWipeResponse> response = authClient.wipeTenantData();
        TenantWipeResponse body = response.getData();
        Map<String, Long> counts = body != null ? body.getDeletedCounts() : Map.of();
        log.warn("[Reset] internal-auth-server tenant data wiped. counts={}", counts);
        return counts;
    }
}
EOF

echo "Rewrote 2 files. Now patching InternalAuthClient.java and application.properties..."

python3 << 'PYEOF'
import io

def patch(path, old, new, label):
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    count = content.count(old)
    if count != 1:
        raise SystemExit(f"ABORT [{label}]: expected 1 match in {path}, found {count}. File may have already been patched or has diverged — check manually.")
    content = content.replace(old, new, 1)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched: {path} ({label})")

# 1. InternalAuthClient — store clientId, add resetSecret, add wipeTenantData()
patch(
    "src/main/java/com/sp/syncPoint/client/service/InternalAuthClient.java",
    '''    private final RestClient restClient;
    private final String basicAuthHeader;

    public InternalAuthClient(
            @Value("${internal-auth.base-url}") String baseUrl,
            @Value("${internal-auth.client-id}") String clientId,
            @Value("${internal-auth.client-secret}") String clientSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
    }''',
    '''    private final RestClient restClient;
    private final String basicAuthHeader;
    private final String clientId;
    private final String resetSecret;

    private static final String WIPE_CONFIRMATION = "WIPE-EVERYTHING";

    public InternalAuthClient(
            @Value("${internal-auth.base-url}") String baseUrl,
            @Value("${internal-auth.client-id}") String clientId,
            @Value("${internal-auth.client-secret}") String clientSecret,
            @Value("${internal-auth.reset.secret:}") String resetSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        this.clientId = clientId;
        this.resetSecret = resetSecret;
    }''',
    "InternalAuthClient constructor"
)

patch(
    "src/main/java/com/sp/syncPoint/client/service/InternalAuthClient.java",
    '''    public void deleteUser(String internalAuthUserId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/user")
                        .queryParam("internalAuthUserId", internalAuthUserId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<Void>>() {});
    }
}''',
    '''    public void deleteUser(String internalAuthUserId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/user")
                        .queryParam("internalAuthUserId", internalAuthUserId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<Void>>() {});
    }

    /**
     * Calls down into internal-auth-server's own tenant wipe endpoint so
     * SyncPoint's {@code /admin/reset/wipe} can clear its own database and
     * every internal-auth-server record scoped to this client application
     * together. Uses the dedicated internal-auth.reset.secret
     * (X-Reset-Secret header), NOT the client-id/secret Basic-auth header
     * every other method here uses - that Basic auth authenticates "which
     * app is calling", this secret separately authorizes "this call may
     * wipe data". appId is always this app's own clientId, so this can
     * never be pointed at another tenant's data.
     */
    public InternalAuthApiResponse<TenantWipeResponse> wipeTenantData() {
        return restClient.post()
                .uri("/admin/reset/wipe-tenant")
                .header("X-Reset-Secret", resetSecret)
                .body(new TenantWipeRequest(clientId, WIPE_CONFIRMATION))
                .retrieve()
                .body(new ParameterizedTypeReference<InternalAuthApiResponse<TenantWipeResponse>>() {});
    }
}''',
    "InternalAuthClient wipeTenantData method"
)

# 2. application.properties — add internal-auth.reset.secret
patch(
    "src/main/resources/application.properties",
    "internal-auth.issuer-url=${INTERNAL_AUTH_ISSUER_URL:http://localhost:5151}",
    "internal-auth.issuer-url=${INTERNAL_AUTH_ISSUER_URL:http://localhost:5151}\ninternal-auth.reset.secret=${INTERNAL_AUTH_RESET_SECRET:}",
    "application.properties internal-auth.reset.secret"
)

print("All patches applied successfully.")
PYEOF

echo ""
echo "Done. Set:"
echo "  - APP_RESET_SECRET (already wired, your own caller-facing secret)"
echo "  - INTERNAL_AUTH_RESET_SECRET (must match ADMIN_RESET_SECRET set on internal-auth-server)"
