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

    public record ResetResult(Map<String, Long> deletedCounts) {
    }

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

    public Map<String, Long> wipeInternalAuthTenantData() {
        InternalAuthApiResponse<TenantWipeResponse> response = authClient.wipeTenantData();
        TenantWipeResponse body = response.getData();
        Map<String, Long> counts = body != null ? body.getDeletedCounts() : Map.of();
        log.warn("[Reset] internal-auth-server tenant data wiped. counts={}", counts);
        return counts;
    }
}
