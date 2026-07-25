package com.sp.syncPoint.feature.wallet.repository;

import com.sp.syncPoint.feature.wallet.model.SystemWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SystemWalletRepository extends JpaRepository<SystemWallet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT systemWallet FROM SystemWallet systemWallet WHERE systemWallet.id = :id")
    Optional<SystemWallet> findByIdForUpdate(Long id);
}
