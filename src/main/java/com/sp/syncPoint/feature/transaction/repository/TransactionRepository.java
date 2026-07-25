package com.sp.syncPoint.feature.transaction.repository;

import com.sp.syncPoint.feature.transaction.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySenderWalletIdOrReceiverWalletId(String senderWalletId, String receiverWalletId);

    @Query("SELECT t FROM Transaction t " + "WHERE t.senderWalletId = :walletId OR t.receiverWalletId = :walletId")
    List<Transaction> findTransactionByWalletId(@Param("walletId") String walletId);

    @Query("SELECT t FROM Transaction t " + "WHERE t.senderWalletId = :walletId OR t.receiverWalletId = :walletId " + "ORDER BY t.createdAt DESC")
    List<Transaction> findLatestTransactionByWalletId(@Param("walletId") String walletId, Pageable pageable);

    boolean existsByTransactionReference(String transactionReference);

    Optional<Transaction> findByTransactionReference(String transactionReference);

    Optional<Transaction> findByRequestRef(String requestRef);

    List<Transaction> findByTransactionReferenceIn(List<String> transactionReferences);

    @Query("SELECT t FROM Transaction t " + "WHERE t.senderWalletPhoneNumber = :phone " + "   OR t.receiverWalletPhoneNumber = :phone " + "ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByPhoneNumber(@Param("phone") String phoneNumber);
}