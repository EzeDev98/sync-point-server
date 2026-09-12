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
