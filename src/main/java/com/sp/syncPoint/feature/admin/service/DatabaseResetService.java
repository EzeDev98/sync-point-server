package com.sp.syncPoint.feature.admin.service;

import com.sp.syncPoint.feature.auth.service.impl.KeycloakService;
import com.sp.syncPoint.feature.passwordreset.repository.PasswordResetOtpRepository;
import com.sp.syncPoint.feature.passwordreset.repository.PasswordResetTokenRepository;
import com.sp.syncPoint.feature.pinreset.repository.PinResetOtpRepository;
import com.sp.syncPoint.feature.pinreset.repository.PinResetTokenRepository;
import com.sp.syncPoint.feature.session.repository.UserSessionRepository;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import com.sp.syncPoint.feature.wallet.repository.SystemWalletRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletFundingRecordRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Destructive maintenance service: wipes the entire application dataset so a
 * test environment can be started from a clean slate without touching Render's
 * dashboard or dropping tables by hand.
 * <p>
 * This exists for non-production / test use. It deletes every user, wallet,
 * transaction and related record, and removes the matching Keycloak accounts so
 * the same phone numbers and emails can register again. It is guarded at the
 * controller by a required secret and an explicit confirmation phrase — see
 * {@code AdminResetController}. Nothing here checks those guards; do not wire a
 * second caller to this service without reproducing them.
 *
 * <h2>Delete order</h2>
 * Rows are removed children-first so foreign keys are never violated on any of
 * the three configured databases (Postgres in prod, MySQL in dev, H2 in test):
 * transactions and funding records and sessions and reset tokens first, then
 * wallets, then users. {@code deleteAllInBatch} issues one DELETE per table and
 * bypasses entity cascades, which is why the order is explicit rather than
 * relying on {@code User -> Wallet} cascade.
 *
 * <h2>System wallet</h2>
 * The {@code system_wallet} singleton is preserved by default: it is
 * provisioning/config data, not user data, and clearing it can leave the app
 * unable to fund or provision new users until it is re-seeded. Pass
 * {@code includeSystemWallet = true} to wipe it too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    private final TransactionRepository transactionRepository;
    private final WalletFundingRecordRepository walletFundingRecordRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PinResetOtpRepository pinResetOtpRepository;
    private final PinResetTokenRepository pinResetTokenRepository;
    private final WalletRepository walletRepository;
    private final SystemWalletRepository systemWalletRepository;
    private final UserRepository userRepository;

    private final KeycloakService keycloakService;

    /** Result of the database phase: pre-wipe row counts plus the Keycloak ids to remove next. */
    public record ResetResult(Map<String, Long> deletedCounts, List<String> keycloakIds) {
    }

    /**
     * Delete every application row in FK-safe order, inside a single
     * transaction. Collects the Keycloak ids of all users first (before they
     * are deleted) so the caller can remove the identity-provider accounts
     * after this transaction commits.
     *
     * @param includeSystemWallet also clear the {@code system_wallet} singleton
     * @return pre-wipe counts per table and the collected Keycloak ids
     */
    @Transactional
    public ResetResult wipeDatabase(boolean includeSystemWallet) {
        List<String> keycloakIds = userRepository.findAll().stream()
                .map(User::getKeycloakId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("transactions", transactionRepository.count());
        counts.put("walletFundingRecords", walletFundingRecordRepository.count());
        counts.put("userSessions", userSessionRepository.count());
        counts.put("passwordResetOtps", passwordResetOtpRepository.count());
        counts.put("passwordResetTokens", passwordResetTokenRepository.count());
        counts.put("pinResetOtps", pinResetOtpRepository.count());
        counts.put("pinResetTokens", pinResetTokenRepository.count());
        counts.put("wallets", walletRepository.count());
        if (includeSystemWallet) {
            counts.put("systemWallets", systemWalletRepository.count());
        }
        counts.put("users", userRepository.count());

        log.warn("[Reset] Wiping database. includeSystemWallet={}. Pre-wipe counts={}", includeSystemWallet, counts);

        // Children first, then wallets, then users.
        transactionRepository.deleteAllInBatch();
        walletFundingRecordRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        passwordResetOtpRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        pinResetOtpRepository.deleteAllInBatch();
        pinResetTokenRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        if (includeSystemWallet) {
            systemWalletRepository.deleteAllInBatch();
        }
        userRepository.deleteAllInBatch();

        log.warn("[Reset] Database rows cleared. {} Keycloak account(s) queued for removal.", keycloakIds.size());

        return new ResetResult(counts, keycloakIds);
    }

    /**
     * Remove the given accounts from Keycloak. Best-effort and per-account: one
     * failure is logged and counted, never aborts the rest, so a stray realm
     * error cannot leave the wipe half-done. Runs after {@link #wipeDatabase}
     * has committed, so it is intentionally not part of that transaction — a
     * Keycloak call cannot be rolled back anyway.
     *
     * @return {@code deleted} and {@code failed} counts
     */
    public Map<String, Integer> deleteKeycloakAccounts(List<String> keycloakIds) {
        int deleted = 0;
        int failed = 0;

        for (String keycloakId : keycloakIds) {
            try {
                keycloakService.deleteUser(keycloakId);
                deleted++;
            } catch (Exception ex) {
                failed++;
                log.warn("[Reset] Could not remove Keycloak account {}: {}", keycloakId, ex.getMessage());
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("failed", failed);
        log.warn("[Reset] Keycloak cleanup done. deleted={}, failed={}", deleted, failed);
        return result;
    }
}