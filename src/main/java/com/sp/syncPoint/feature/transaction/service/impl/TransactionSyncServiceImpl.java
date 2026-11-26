package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.exception.NotFoundException;
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
        int completedTransferCount = completedResponses.size() / 2;
        int failedTransferCount = failedResponses.size();

        String message = String.format("Processed %d new transfer(s): %d completed, %d failed, %d already-processed", newRequests.size(), completedTransferCount, failedTransferCount, totalSkipped);
        return responseAssembler.successResponse(completedResponses, failedResponses, totalSkipped, message);
    }

    ProcessingResult processSingleTransfer(SyncTransactionRequest request) {
        String senderPhone = request.getSenderPhone();
        String receiverPhone = request.getReceiverPhone();

        List<String> sortedPhones = Stream.of(senderPhone, receiverPhone).sorted().collect(Collectors.toList());

        List<String> acquiredLocks = lockService.acquireAllLocks(sortedPhones);

        try {

            if (transactionRepository.existsByTransactionReference(
                    request.getTransactionRef())) {
                log.info("[Sync] Transaction {} already exists (inner idempotency check)",
                        request.getTransactionRef());
                return ProcessingResult.skipped(request.getTransactionRef());
            }

            Wallet senderWallet = walletRepository.findByPhoneNumberWithLock(senderPhone)
                    .orElseThrow(() -> new NotFoundException("Sender wallet not found: " + senderPhone));

            Wallet receiverWallet = walletRepository.findByPhoneNumberWithLock(receiverPhone)
                    .orElseThrow(() -> new NotFoundException("Receiver wallet not found: " + receiverPhone));

            if (!Objects.equals(senderWallet.getWalletId(), request.getSenderWalletId())) {
                log.warn("[Sync] Rejected transfer {} — sender phone {} maps to wallet {}, not signed sender {}", request.getTransactionRef(), senderPhone, senderWallet.getWalletId(), request.getSenderWalletId());
                return ProcessingResult.rejected(request, "Sender wallet does not match the signed transfer");
            }
            if (!Objects.equals(receiverWallet.getWalletId(), request.getReceiverWalletId())) {
                log.warn("[Sync] Rejected transfer {} — receiver phone {} maps to wallet {}, not signed receiver {}", request.getTransactionRef(), receiverPhone, receiverWallet.getWalletId(), request.getReceiverWalletId());
                return ProcessingResult.rejected(request, "Receiver wallet does not match the signed transfer");
            }

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
                log.warn("[Sync] Rejected transfer {} — signature invalid or sender {} not enrolled", request.getTransactionRef(), request.getSenderWalletId());
                return ProcessingResult.rejected(request, "Transfer signature is invalid or the sender has no registered signing key");
            }

            BigDecimal senderMirror = senderWallet.getMirrorBalance() != null
                    ? MoneyUtils.normalize(senderWallet.getMirrorBalance()) : ZERO;
            BigDecimal receiverMirror = receiverWallet.getMirrorBalance() != null
                    ? MoneyUtils.normalize(receiverWallet.getMirrorBalance()) : ZERO;

            LocalDateTime now = LocalDateTime.now();
            String syncId = valueGeneratorService.generateSyncId();

            Transaction debit = mapper.buildEntry(request, DEBIT, TransactionStatus.COMPLETED, null);
            debit.setSenderBalanceBefore(senderMirror);
            debit.setSenderBalanceAfter(senderMirror);
            debit.setCompletedAt(now);
            debit.setSyncedAt(now);
            debit.setSyncId(syncId);

            Transaction credit = mapper.buildEntry(request, CREDIT, TransactionStatus.COMPLETED, null);
            credit.setReceiverBalanceBefore(receiverMirror);
            credit.setReceiverBalanceAfter(receiverMirror);
            credit.setCompletedAt(now);
            credit.setSyncedAt(now);
            credit.setSyncId(syncId);

            transactionRepository.saveAll(List.of(debit, credit));

            log.info("[Sync] Transfer {} recorded (audit-only). No mirror moved — sender {} at {}, receiver {} at {}", request.getTransactionRef(), request.getSenderWalletId(), senderMirror, request.getReceiverWalletId(), receiverMirror);
            return ProcessingResult.success(debit, credit);

        } finally {
            lockService.releaseAllLocks(acquiredLocks);
        }
    }
}