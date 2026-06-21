package com.sp.sync_point.repository;

import com.sp.sync_point.model.WalletFundingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletFundingRecordRepository extends JpaRepository<WalletFundingRecord, Long> {
}
