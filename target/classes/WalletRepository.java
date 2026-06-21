package com.sp.sync_point.repository;

import com.sp.sync_point.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByWalletId(String walletId);

    Optional<Wallet> findByPhoneNumber(String phoneNumber);

    List<Wallet> findByPhoneNumberIn(List<String> phoneNumbers);

    boolean existsByPhoneNumber(String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.phoneNumber = :phoneNumber")
    Optional<Wallet> findByPhoneNumberWithLock(@Param("phoneNumber") String phoneNumber);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT w FROM Wallet w WHERE w.phoneNumber = :phoneNumber")
    Optional<Wallet> findByPhoneNumberWithOptimisticLock(@Param("phoneNumber") String phoneNumber);
}
