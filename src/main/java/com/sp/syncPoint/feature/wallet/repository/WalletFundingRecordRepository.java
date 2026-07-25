package com.sp.syncPoint.feature.wallet.repository;

import com.sp.syncPoint.feature.wallet.model.WalletFundingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletFundingRecordRepository extends JpaRepository<WalletFundingRecord, Long> {
}
