package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.money.MoneyUtils;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.common.security.TransferSignatureVerifier;
import com.sp.syncPoint.common.util.UniqueValueGeneratorService;
import com.sp.syncPoint.feature.transaction.dto.ProcessingResult;
import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.transaction.service.TransactionSyncService;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sp.syncPoint.feature.transaction.enums.EntryType.CREDIT;
import static com.sp.syncPoint.feature.transaction.enums.EntryType.DEBIT;
import static java.math.BigDecimal.ZERO;

/**
 * Batch sync engine for offline-queued P2P transfers.
 * <p>
 * Extracted from the original {@code TransactionServiceImpl} god class as
 * part of the earlier SOLID refactor. It owns the reconciliation flow, the
 * per-transfer authorization, and the lock/idempotency/settlement
 * orchestration — entity construction lives in {@link DtoMapper#buildEntry},
 * response shaping in {@link TransactionResponseAssembler}, and field
 * validation in {@link TransactionValidator}. Wallet funding/transfer live in
 * {@link WalletFundingServiceImpl}.
 *
 * <h2>Settlement model (Model B)</h2>
 * Offline float is the client's money once loaded: funding an offline wallet
 * already moved the money out of the sender's mirror balance. So at sync time
 * a transfer's <b>DEBIT does NOT touch the sender's mirror</b> — charging it
 * again would double-spend the sender. Only the <b>CREDIT applies</b>, raising
 * the receiver's mirror. Funding and transfer-to-online still adjust the
 * mirror directly; that is unchanged and lives elsewhere.
 *
 * <h2>Authorization (signatures, not caller identity)</h2>
 * Because a receiver must be able to settle money they received even if the
 * sender never reconnects, "the caller owns the sender phone" can no longer be
 * the guard — the caller is often not the sender. Each transfer instead
 * carries the sender's signature over a canonical description of that exact
 * transfer; we verify it against the sender wallet's registered public key
 * (see {@link TransferSignatureVerifier}). Whoever submits the batch, only a
 * transfer the genuine sender signed can move money, and it can only ever
 * credit the receiver the sender named. A rejected transfer persists nothing,
 * so a corrected one can be retried.
 * <p>
 * Note: signatures prove authorization, not that the sender had sufficient
 * offline float. Under Model B that sufficiency is the client's
 * responsibility (enforced at funding and before each offline spend); the
 * server does not re-check it. A future float-tracking layer can close the
 * residual collusion gap without changing this contract.
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
    private final TransferSignatureVerifier signatureVerifier;
    private final TransactionResponseAssembler responseAssembler;

    /**
     * Sync a batch of offline transactions from a client device.
     * <p>
     * Per-transactionRef idempotency is the primary dedup mechanism: the batch
     * is filtered to only new refs before processing, so clients may safely
     * retry full batches — already-processed refs are skipped silently.
     * <p>
     * {@code @Retryable} handles OptimisticLockingFailureException on
     * Transaction rows; wallet locking uses PESSIMISTIC_WRITE to guarantee
     * balance correctness. The two strategies are intentionally separated —
     * pessimistic on wallets (financial critical), optimistic on transaction
     * records (lower contention, audit trail).
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
                } else if (result.isRejected()) {
                    // Refused on its own merits (bad signature / wallet mismatch).
                    // Nothing was persisted, so this transfer can be retried once
                    // corrected. Reported as one failed entry, from the request.
                    log.warn("[Sync] Transaction {} rejected: {}", request.getTransactionRef(), result.getFailureReason());
                    failedResponses.add(responseAssembler.failedResponse(request, result.getFailureReason()));
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
        int completedTransferCount = completedResponses.size() / 2; // two ledger entries per settled transfer
        int failedTransferCount = failedResponses.size();           // one entry per rejected/errored transfer

        String message = String.format("Processed %d new transfer(s): %d completed, %d failed, %d already-processed", newRequests.size(), completedTransferCount, failedTransferCount, totalSkipped);
        return responseAssembler.successResponse(completedResponses, failedResponses, totalSkipped, message);
    }

    /**
     * Process a single TRANSFER atomically:
     * <ol>
     *   <li>Acquire distributed locks on both wallets (sorted order — deadlock prevention)</li>
     *   <li>Re-check idempotency inside the lock (TOCTOU)</li>
     *   <li>Bind the signed wallet identifiers to the wallets actually loaded</li>
     *   <li>Verify the sender's signature against the sender wallet's registered key</li>
     *   <li>Build DEBIT + CREDIT records and persist them FIRST</li>
     *   <li>Apply the CREDIT to the receiver's mirror only — the sender's mirror is untouched (Model B)</li>
     *   <li>Release locks in finally</li>
     * </ol>
     * Package-private (not private) so Spring's proxy could wrap it if this
     * logic is ever moved to a delegate; today it participates in the caller's
     * transaction via propagation=REQUIRED.
     */
    ProcessingResult processSingleTransfer(SyncTransactionRequest request) {
        String senderPhone = request.getSenderPhone();
        String receiverPhone = request.getReceiverPhone();

        // Acquire locks in sorted order to prevent deadlock when A→B and B→A
        // requests arrive concurrently
        List<String> sortedPhones = Stream.of(senderPhone, receiverPhone).sorted().collect(Collectors.toList());

        List<String> acquiredLocks = lockService.acquireAllLocks(sortedPhones);

        try {
            // TOCTOU: re-check idempotency inside the lock. The outer check in
            // syncTransactions is a fast pre-filter; this is authoritative.
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

            // Bind the signed wallet identifiers to the wallets we actually
            // loaded (by phone). The signature authorizes moving money between
            // two specific walletIds; without this check a valid signature for
            // "pay wallet B" could be redirected to wallet C by swapping the
            // phone in the request. Nothing is persisted on a mismatch.
            if (!Objects.equals(senderWallet.getWalletId(), request.getSenderWalletId())) {
                log.warn("[Sync] Rejected transfer {} — sender phone {} maps to wallet {}, not signed sender {}",
                        request.getTransactionRef(), senderPhone, senderWallet.getWalletId(), request.getSenderWalletId());
                return ProcessingResult.rejected(request, "Sender wallet does not match the signed transfer");
            }
            if (!Objects.equals(receiverWallet.getWalletId(), request.getReceiverWalletId())) {
                log.warn("[Sync] Rejected transfer {} — receiver phone {} maps to wallet {}, not signed receiver {}",
                        request.getTransactionRef(), receiverPhone, receiverWallet.getWalletId(), request.getReceiverWalletId());
                return ProcessingResult.rejected(request, "Receiver wallet does not match the signed transfer");
            }

            // Authorization: the signature — not who is calling — proves the
            // sender authorized this exact transfer. Verified against the
            // sender wallet's registered public key. A missing key (sender
            // never enrolled) or a bad signature is a rejection, not an error.
            boolean authorized = signatureVerifier.verify(
                    senderWallet.getSigningPublicKey(),
                    request.getTransactionRef(),
                    request.getRequestRef(),
                    request.getSenderWalletId(),
                    request.getReceiverWalletId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getCreatedAt(),
                    request.getSignature());

            if (!authorized) {
                log.warn("[Sync] Rejected transfer {} — signature invalid or sender {} not enrolled",
                        request.getTransactionRef(), request.getSenderWalletId());
                return ProcessingResult.rejected(request, "Transfer signature is invalid or the sender has no registered signing key");
            }

            BigDecimal amount = MoneyUtils.normalize(request.getAmount());
            BigDecimal senderMirror = senderWallet.getMirrorBalance() != null
                    ? MoneyUtils.normalize(senderWallet.getMirrorBalance()) : ZERO;
            BigDecimal receiverBalance = receiverWallet.getMirrorBalance() != null
                    ? MoneyUtils.normalize(receiverWallet.getMirrorBalance()) : ZERO;
            BigDecimal receiverNewBalance = MoneyUtils.add(receiverBalance, amount);

            LocalDateTime now = LocalDateTime.now();
            String syncId = valueGeneratorService.generateSyncId();

            // DEBIT — the sender's audit record. Model B: the sender's mirror is
            // NOT touched (the money left the mirror at funding time), so the
            // before/after snapshot is deliberately equal.
            Transaction debit = mapper.buildEntry(request, DEBIT, TransactionStatus.COMPLETED, null);
            debit.setSenderBalanceBefore(senderMirror);
            debit.setSenderBalanceAfter(senderMirror);
            debit.setCompletedAt(now);
            debit.setSyncedAt(now);
            debit.setSyncId(syncId);

            // CREDIT — the receiver's money actually arrives in the mirror.
            Transaction credit = mapper.buildEntry(request, CREDIT, TransactionStatus.COMPLETED, null);
            credit.setReceiverBalanceBefore(receiverBalance);
            credit.setReceiverBalanceAfter(receiverNewBalance);
            credit.setCompletedAt(now);
            credit.setSyncedAt(now);
            credit.setSyncId(syncId);

            // CRITICAL ORDERING — persist the ledger records BEFORE touching a
            // balance, so a failed save leaves no balance changed and a failed
            // balance update still rolls back both within this transaction.
            transactionRepository.saveAll(List.of(debit, credit));

            // Model B: only the receiver's mirror moves. The sender wallet is
            // intentionally left untouched (not saved) — its mirror already
            // parted with this money when the offline wallet was funded.
            receiverWallet.setMirrorBalance(receiverNewBalance);
            receiverWallet.setUpdatedAt(now);
            walletRepository.save(receiverWallet);

            log.info("[Sync] Transfer {} settled. Receiver {} mirror: {} → {} (sender {} mirror unchanged at {})",
                    request.getTransactionRef(), request.getReceiverWalletId(), receiverBalance, receiverNewBalance,
                    request.getSenderWalletId(), senderMirror);
            return ProcessingResult.success(debit, credit);

        } finally {
            // Release exactly the locks that were acquired, in reverse order.
            lockService.releaseAllLocks(acquiredLocks);
        }
    }
}