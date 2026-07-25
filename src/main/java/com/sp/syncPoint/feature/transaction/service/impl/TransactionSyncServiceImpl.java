package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.money.MoneyUtils;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.common.util.UniqueValueGeneratorService;
import com.sp.syncPoint.feature.transaction.dto.ProcessingResult;
import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.transaction.service.TransactionSyncService;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.impl.WalletFundingServiceImpl;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sp.syncPoint.common.constants.GeneralConstants.FAILURE_REASONS;
import static com.sp.syncPoint.feature.transaction.enums.EntryType.CREDIT;
import static com.sp.syncPoint.feature.transaction.enums.EntryType.DEBIT;
import static java.math.BigDecimal.ZERO;

/**
 * Batch sync engine for offline-queued P2P transfers.
 * <p>
 * Extracted from the original {@code TransactionServiceImpl} god class as
 * part of the earlier SOLID refactor. In the phase-2 separation-of-concerns
 * pass this class was slimmed to orchestration: entity construction moved to
 * {@link DtoMapper#buildEntry}, response shaping to
 * {@link TransactionResponseAssembler}, and transfer-level field validation
 * to {@link TransactionValidator#validateTransferRules}. It now owns the
 * reconciliation flow, the sender-ownership authorization check, and the
 * lock/idempotency/balance orchestration — nothing else. Wallet
 * funding/transfer and reads live in {@link WalletFundingServiceImpl} and
 * {@link TransactionQueryServiceImpl}; no behaviour changed during any split.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncServiceImpl implements TransactionSyncService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionLockService lockService;
    private final DtoMapper mapper;
    private final IdempotencyValidator idempotencyValidator;
    private final TransactionValidator transactionValidator;
    private final UniqueValueGeneratorService valueGeneratorService;
    private final CurrentUserService currentUserService;
    private final TransactionResponseAssembler responseAssembler;

    /**
     * Sync a batch of offline transactions from a client device.
     * <p>
     * Fix issue #23: per-transactionRef idempotency is the primary dedup mechanism.
     * The batch is filtered to only new refs before processing. Clients may safely
     * retry full batches — already-processed refs are skipped silently.
     * <p>
     * Fix issue #11: @Retryable handles OptimisticLockingFailureException on
     * Transaction rows. Wallet locking uses PESSIMISTIC_WRITE (WalletRepository)
     * to guarantee balance correctness. These two strategies are intentionally
     * separated — pessimistic on wallets (high contention, financial critical),
     * optimistic on transaction records (lower contention, audit trail).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public BaseResponse syncTransactions(List<SyncTransactionRequest> requests) {
        log.info("[Sync] Processing {} transaction request(s)", requests.size());

        for (SyncTransactionRequest request : requests) {
            transactionValidator.validateTransaction(request);
            // Offline-sync requests carry no PIN — the client already enforced that
            // client-side before queuing the transaction. This ownership check is
            // therefore the ONLY server-side proof that the caller is entitled to
            // debit this sender's wallet; without it, any authenticated user could
            // submit a sync batch claiming to be any other user's sender. Kept in
            // the service (authorization, not field validation) and in its original
            // position — after validateTransaction, before the transfer rules.
            currentUserService.assertOwnsPhoneNumber(request.getSenderPhone());
            transactionValidator.validateTransferRules(request);
        }

        List<String> incomingRefs = requests.stream()
                .map(SyncTransactionRequest::getTransactionRef)
                .collect(Collectors.toList());

        List<Transaction> alreadyProcessed = idempotencyValidator
                .getProcessedTransactions(incomingRefs);

        Set<String> processedRefs = alreadyProcessed.stream()
                .map(Transaction::getTransactionReference)
                .collect(Collectors.toSet());

        List<SyncTransactionRequest> newRequests = requests.stream()
                .filter(r -> !processedRefs.contains(r.getTransactionRef()))
                .collect(Collectors.toList());

        if (newRequests.isEmpty()) {
            log.info("[Sync] All {} transaction(s) already processed — idempotent response",
                    requests.size());
            return responseAssembler.idempotentResponse(alreadyProcessed);
        }

        newRequests.sort(Comparator.comparing(SyncTransactionRequest::getCreatedAt));

        List<TransactionResponse> completedResponses = new ArrayList<>();
        List<TransactionResponse> failedResponses = new ArrayList<>();

        int outerSkippedCount = (int) alreadyProcessed.stream()
                .map(Transaction::getTransactionReference)
                .distinct()
                .count();

        int innerSkippedCount = 0;

        for (SyncTransactionRequest request : newRequests) {
            try {
                ProcessingResult result = processSingleTransfer(request);

                if (result.isSuccess()) {
                    completedResponses.addAll(mapper.mapTransactionToResponses(result.getTransactions()));
                } else if (result.isFailed()) {
                    failedResponses.addAll(mapper.mapTransactionToResponses(result.getTransactions()));
                } else if (result.isSkipped()) {
                    log.info("[Sync] Transaction {} skipped (inner idempotency — race condition)", result.getSkippedRef());
                    innerSkippedCount++;
                }

            } catch (Exception e) {
                log.error("[Sync] Failed to process transaction {}: {}", request.getTransactionRef(), e.getMessage(), e);
                failedResponses.add(responseAssembler.failedResponse(request, e.getMessage()));
            }
        }

        int totalSkipped = outerSkippedCount + innerSkippedCount;
        int completedTransferCount = completedResponses.size() / 2;
        int failedTransferCount = failedResponses.size() / 2;

        String message = String.format("Processed %d new transfer(s): %d completed, %d failed, %d already-processed", newRequests.size(), completedTransferCount, failedTransferCount, totalSkipped);
        return responseAssembler.successResponse(completedResponses, failedResponses, totalSkipped, message);
    }

    /**
     * Process a single TRANSFER atomically:
     * 1. Acquire distributed locks on both wallets (sorted order — deadlock prevention)
     * 2. Re-check idempotency inside the lock (fix issue #12 — TOCTOU)
     * 3. Validate balance
     * 4. Build DEBIT + CREDIT transaction records
     * 5. Save transaction records FIRST (fix issues #14 and #15)
     * 6. Update wallet balances AFTER successful transaction save
     * 7. Release locks in finally
     * <p>
     * This method is package-private (not private) so Spring's proxy can wrap it
     * with its own @Transactional if this class is ever refactored into a delegate.
     * <p>
     * Fix issue #13: removed @Transactional from private method. This method
     * participates in the caller's transaction via propagation=REQUIRED. The
     * annotation on a private method called within the same class is silently
     * ignored by Spring's proxy — removed to prevent misleading future readers.
     */
    ProcessingResult processSingleTransfer(SyncTransactionRequest request) {
        String senderPhone = request.getSenderPhone();
        String receiverPhone = request.getReceiverPhone();

        // Acquire locks in sorted order to prevent deadlock when A→B and B→A
        // requests arrive concurrently
        List<String> sortedPhones = Stream.of(senderPhone, receiverPhone).sorted().collect(Collectors.toList());

        List<String> acquiredLocks = lockService.acquireAllLocks(sortedPhones);

        try {
            // Fix issue #12 — TOCTOU: re-check idempotency inside the lock.
            // The outer check in syncTransactions is a fast pre-filter; this
            // is the authoritative check under the lock.
            if (transactionRepository.existsByTransactionReference(
                    request.getTransactionRef())) {
                log.info("[Sync] Transaction {} already exists (inner idempotency check)",
                        request.getTransactionRef());
                return ProcessingResult.skipped(request.getTransactionRef());
            }

            // Fetch wallets under pessimistic write lock
            Wallet senderWallet = walletRepository.findByPhoneNumberWithLock(senderPhone)
                    .orElseThrow(() -> new NotFoundException(
                            "Sender wallet not found: " + senderPhone));

            Wallet receiverWallet = walletRepository.findByPhoneNumberWithLock(receiverPhone)
                    .orElseThrow(() -> new NotFoundException(
                            "Receiver wallet not found: " + receiverPhone));

            BigDecimal amount = request.getAmount();
            BigDecimal senderBalance = senderWallet.getMirrorBalance() != null ? MoneyUtils.normalize(senderWallet.getMirrorBalance()) : ZERO;

            if (MoneyUtils.isLessThan(senderBalance, amount)) {
                log.warn("[Sync] Insufficient balance for {}. Required: {}, Available: {}", request.getTransactionRef(), amount, senderBalance);

                Transaction failedDebit = mapper.buildEntry(request, DEBIT, TransactionStatus.FAILED, FAILURE_REASONS);
                Transaction failedCredit = mapper.buildEntry(request, CREDIT, TransactionStatus.FAILED, "Insufficient balance in sender wallet");

                transactionRepository.saveAll(List.of(failedDebit, failedCredit));
                return ProcessingResult.failed(failedDebit, failedCredit);
            }

            BigDecimal senderNewBalance = MoneyUtils.subtract(senderBalance, amount);
            BigDecimal receiverBalance = receiverWallet.getMirrorBalance() != null ? MoneyUtils.normalize(receiverWallet.getMirrorBalance()) : ZERO;
            BigDecimal receiverNewBalance = MoneyUtils.add(receiverBalance, amount);
            LocalDateTime now = LocalDateTime.now();
            String syncId = valueGeneratorService.generateSyncId();

            Transaction debit = mapper.buildEntry(request, DEBIT, TransactionStatus.COMPLETED, null);
            debit.setSenderBalanceBefore(senderBalance);
            debit.setSenderBalanceAfter(senderNewBalance);
            debit.setCompletedAt(now);
            debit.setSyncedAt(now);
            debit.setSyncId(syncId);

            Transaction credit = mapper.buildEntry(request, CREDIT, TransactionStatus.COMPLETED, null);
            credit.setReceiverBalanceBefore(receiverBalance);
            credit.setReceiverBalanceAfter(receiverNewBalance);
            credit.setCompletedAt(now);
            credit.setSyncedAt(now);
            credit.setSyncId(syncId);

            // Fix issues #14 and #15:
            // CRITICAL ORDERING — save transaction records BEFORE updating wallet balances.
            // If the save fails, no balance is touched. If the balance update fails,
            // the transaction records exist as an audit trail and the @Transactional
            // rollback reverts both within the same unit of work.
            transactionRepository.saveAll(List.of(debit, credit));

            // Update wallet balances only after transaction records are persisted
            senderWallet.setMirrorBalance(senderNewBalance);
            senderWallet.setUpdatedAt(now);
            receiverWallet.setMirrorBalance(receiverNewBalance);
            receiverWallet.setUpdatedAt(now);
            walletRepository.saveAll(List.of(senderWallet, receiverWallet));

            log.info("[Sync] Transfer {} completed. Sender: {} → {}, Receiver: {} → {}", request.getTransactionRef(), senderBalance, senderNewBalance, receiverBalance, receiverNewBalance);
            return ProcessingResult.success(debit, credit);

        } finally {
            // Release exactly the locks that were acquired, in reverse order.
            // acquireAllLocks returns only successfully acquired locks, so if
            // acquisition failed partway through we release only those held.
            lockService.releaseAllLocks(acquiredLocks);
        }
    }
}