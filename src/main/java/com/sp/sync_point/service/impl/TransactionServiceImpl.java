package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.ProcessingResult;
import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.dto.WalletTransferRequest;
import com.sp.sync_point.enums.EntryType;
import com.sp.sync_point.enums.TransactionStatus;
import com.sp.sync_point.enums.TransactionType;
import com.sp.sync_point.exception.*;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;

import com.sp.sync_point.response.WalletFundingResponse;
import com.sp.sync_point.service.TransactionService;
import com.sp.sync_point.utils.MoneyUtils;
import com.sp.sync_point.utils.TransactionUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sp.sync_point.constants.GeneralConstants.CURRENCY;
import static com.sp.sync_point.constants.GeneralConstants.FAILURE_REASONS;
import static com.sp.sync_point.enums.EntryType.CREDIT;
import static com.sp.sync_point.enums.EntryType.DEBIT;
import static com.sp.sync_point.enums.TransactionStatus.SUCCESS;
import static com.sp.sync_point.enums.TransactionType.*;
import static java.math.BigDecimal.ZERO;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionLockService lockService;
    private final DtoMapper mapper;
    private final IdempotencyValidator idempotencyValidator;
    private final ValidateService validateService;
    private final UniqueValueGeneratorService valueGeneratorService;
    private final TransactionUtils transactionUtils;
    private final PasswordEncoder passwordEncoder;


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
            validateService.validateTransaction(request);
            validateTransactionRequest(request);
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
            return buildIdempotentResponse(alreadyProcessed);
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
                failedResponses.add(buildFailedResponse(request, e.getMessage()));
            }
        }

        int totalSkipped = outerSkippedCount + innerSkippedCount;
        int completedTransferCount = completedResponses.size() / 2;
        int failedTransferCount = failedResponses.size() / 2;

        String message = String.format("Processed %d new transfer(s): %d completed, %d failed, %d already-processed", newRequests.size(), completedTransferCount, failedTransferCount, totalSkipped);
        return buildSuccessResponse(completedResponses, failedResponses, totalSkipped, message);
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

                Transaction failedDebit = buildEntry(request, DEBIT, TransactionStatus.FAILED, FAILURE_REASONS);
                Transaction failedCredit = buildEntry(request, CREDIT, TransactionStatus.FAILED, "Insufficient balance in sender wallet");

                transactionRepository.saveAll(List.of(failedDebit, failedCredit));
                return ProcessingResult.failed(failedDebit, failedCredit);
            }

            BigDecimal senderNewBalance = MoneyUtils.subtract(senderBalance, amount);
            BigDecimal receiverBalance = receiverWallet.getMirrorBalance() != null ? MoneyUtils.normalize(receiverWallet.getMirrorBalance()) : ZERO;
            BigDecimal receiverNewBalance = MoneyUtils.add(receiverBalance, amount);
            LocalDateTime now = LocalDateTime.now();
            String syncId = valueGeneratorService.generateSyncId();

            Transaction debit = buildEntry(request, DEBIT, TransactionStatus.COMPLETED, null);
            debit.setSenderBalanceBefore(senderBalance);
            debit.setSenderBalanceAfter(senderNewBalance);
            debit.setCompletedAt(now);
            debit.setSyncedAt(now);
            debit.setSyncId(syncId);

            Transaction credit = buildEntry(request, CREDIT, TransactionStatus.COMPLETED, null);
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

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private Transaction buildEntry(SyncTransactionRequest request, EntryType entryType, TransactionStatus status, String failureReason) {
        Transaction transaction = mapper.mapToTransaction(request);
        transaction.setEntryType(entryType);
        transaction.setTransactionType(TransactionType.P2P_TRANSFER);
        transaction.setStatus(status);
        if (failureReason != null) {
            transaction.setFailureReason(failureReason);
        }
        return transaction;
    }

    private void validateTransactionRequest(SyncTransactionRequest request) {
        if (request.getSenderPhone() == null || request.getSenderPhone().isBlank()) {
            throw new BadRequestException("Sender phone number is required");
        }
        if (request.getReceiverPhone() == null || request.getReceiverPhone().isBlank()) {
            throw new BadRequestException("Receiver phone number is required");
        }
        if (request.getAmount() == null ||
                MoneyUtils.isLessThanOrEqual(request.getAmount(), ZERO)) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (request.getSenderPhone().equals(request.getReceiverPhone())) {
            throw new BadRequestException("Sender and receiver cannot be the same");
        }

        if (!CURRENCY.equals(request.getCurrency())) {
            throw new BadRequestException("Only NGN is supported. Received: " + request.getCurrency());
        }
        if (request.getRequestRef() == null || request.getRequestRef().isBlank()) {
            throw new BadRequestException("requestRef is required for reconciliation");
        }
    }

    private TransactionResponse buildFailedResponse(SyncTransactionRequest request,
                                                    String reason) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionReference(request.getTransactionRef());
        response.setRequestRef(request.getRequestRef());
        response.setStatus(TransactionStatus.FAILED.name());
        response.setAmount(request.getAmount());
        response.setSenderWalletId(request.getSenderWalletId());
        response.setReceiverWalletId(request.getReceiverWalletId());
        response.setFailureReason(reason);
        return response;
    }

    private BaseResponse buildIdempotentResponse(List<Transaction> existing) {
        List<TransactionResponse> responses = mapper.mapTransactionToResponses(existing);

        long uniqueTransferCount = existing.stream()
                .map(Transaction::getTransactionReference)
                .distinct()
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("message", "All transactions already processed");
        data.put("existingTransactions", responses);
        data.put("duplicateTransferCount", uniqueTransferCount);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(HttpServletResponse.SC_OK);
        response.setDescription("Idempotent request — all transactions already processed");
        response.setData(data);
        return response;
    }

    private BaseResponse buildSuccessResponse(List<TransactionResponse> completed, List<TransactionResponse> failed, int skippedCount, String message) {

        int completedTransferCount = completed.size() / 2;
        int failedTransferCount = failed.size();

        Map<String, Object> data = new HashMap<>();
        data.put("completedTransactions", completed);
        data.put("failedTransactions", failed);
        data.put("completedCount", completedTransferCount);
        data.put("failedCount", failedTransferCount);
        data.put("skippedCount", skippedCount);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(HttpServletResponse.SC_OK);
        response.setDescription(message);
        response.setData(data);
        return response;
    }


    @Override
    public String deleteTransactions() {
        try {
            long transactionCounts = transactionRepository.count();
            transactionRepository.deleteAll();

            if (transactionCounts > 0) {
                return "All transactions deleted successfully";
            } else {
                return "No transactions found to delete";
            }
        } catch (Exception e) {
            throw new RuntimeException("Error deleting transactions", e);
        }
    }

    @Override
    public List<TransactionResponse> getAllTransactions() {

        List<Transaction> transactions = transactionRepository.findAll();
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getTransactionsByWmUniqueId(String userWmUniqueId, String receiverWmUniqueId) {

        if (userWmUniqueId == null || receiverWmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        List<Transaction> transactions = transactionRepository.findBySenderWalletIdOrReceiverWalletId(userWmUniqueId, receiverWmUniqueId);
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {} and receiverWmUniqueId: {}", userWmUniqueId, receiverWmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getTransactionsByWmUniqueId(String wmUniqueId) {

        if (wmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        List<Transaction> transactions = transactionRepository.findTransactionByWalletId(wmUniqueId);
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {}", wmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public List<TransactionResponse> getLatestTransactionsByWmUniqueId(String wmUniqueId) {

        if (wmUniqueId == null) {
            throw new EmptyFieldException("wmUniqueId cannot be null or empty", HttpStatus.BAD_REQUEST);
        }

        List<Transaction> transactions = transactionRepository.findLatestTransactionByWalletId(wmUniqueId, PageRequest.of(0, 10));
        if (transactions == null || transactions.isEmpty()) {
            log.error("No transactions found for user with wmUniqueId: {}", wmUniqueId);
            return Collections.emptyList();
        }

        return mapper.mapTransactionToResponses(transactions);
    }

    @Override
    public BaseResponse saveMirrorBalance(String walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByWalletId(walletId).orElseThrow(() -> new WalletNotFoundException("No wallet found for the ID provided"));
        wallet.setMirrorBalance(amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        return new BaseResponse(HttpServletResponse.SC_OK, "Mirror balance persisted successfully");
    }

    @Transactional
    @Override
    public WalletFundingResponse fundOfflineWallet(WalletTransferRequest request) {

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new EmptyFieldException("Phone number must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getTransactionPin() == null || request.getTransactionPin().isBlank()) {
            throw new EmptyFieldException("PIN must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be greater than zero");
        }

        if (request.getRequestRef() == null || request.getRequestRef().isBlank()) {
            throw new EmptyFieldException("Request reference must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        try {

            Optional<Transaction> existing = transactionRepository.findByRequestRef(request.getRequestRef());
            if (existing.isPresent()) {
                Transaction priorTransaction = existing.get();
                return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", priorTransaction.getAmount(), priorTransaction.getTransactionReference());
            }

            Wallet wallet = walletRepository.findByPhoneNumberWithLock(request.getPhoneNumber())
                    .orElseThrow(() -> new EntityNotFoundException("No wallet found for phone number: " + request.getPhoneNumber()));

            if (!passwordEncoder.matches(request.getTransactionPin(), wallet.getPin())) {
                throw new InvalidPinException("Pin doesn't match");
            }

            if (wallet.getMirrorBalance() == null || wallet.getMirrorBalance().compareTo(ZERO) < 0) {
                throw new InsufficientFundsException("Wallet does not have any funds");
            }

            if (wallet.getOfflineLimit().compareTo(request.getAmount()) < 0) {
                throw new BadRequestException("Your offline limit is " + wallet.getOfflineLimit() + "The amount you are requesting for is more than your offline limit try a lesser amount");
            }

            if (wallet.getMirrorBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException("You don't have enough funds in your wallet");
            }

            BigDecimal balanceBefore = wallet.getMirrorBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());
            wallet.setMirrorBalance(balanceAfter);

            String description = String.format("Offline wallet funded with %s", request.getAmount());

            Transaction transaction = transactionUtils.createTransaction(request.getRequestRef(), OFFLINE_WALLET_FUNDING, DEBIT, wallet.getWalletId(), wallet.getUser().getUsername(), wallet.getPhoneNumber(), balanceBefore, balanceAfter, null, null, null, null, null, request.getAmount(), description, SUCCESS, null);

            try {
                transactionRepository.save(transaction);
            } catch (DataIntegrityViolationException dup) {
                Transaction priorTransaction = transactionRepository.findByRequestRef(request.getRequestRef())
                        .orElseThrow(() -> dup);
                return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", priorTransaction.getAmount(), priorTransaction.getTransactionReference());
            }

            walletRepository.save(wallet);

            return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", request.getAmount(), transaction.getTransactionReference());

        } catch (EmptyFieldException | InvalidInputException | EntityNotFoundException | InvalidPinException | InsufficientFundsException | InsufficientBalanceException | BadRequestException ex) {
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new InternalServerErrorException("Transaction is being processed concurrently. Please retry.", ex);
        } catch (JpaSystemException | TransactionSystemException ex) {
            throw new InternalServerErrorException("Database transaction failed.", ex);
        } catch (Exception ex) {
            throw new InternalServerErrorException("Transfer failed. Please try again later.", ex);
        }
    }

    @Transactional
    @Override
    public WalletFundingResponse transferToOnlineAccount(WalletTransferRequest request) {

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new EmptyFieldException("Phone number must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getTransactionPin() == null || request.getTransactionPin().isBlank()) {
            throw new EmptyFieldException("PIN must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be greater than zero");
        }

        if (request.getRequestRef() == null || request.getRequestRef().isBlank()) {
            throw new EmptyFieldException("Request reference must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        try {

            Optional<Transaction> existing = transactionRepository.findByRequestRef(request.getRequestRef());
            if (existing.isPresent()) {
                Transaction priorTransaction = existing.get();
                return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", priorTransaction.getAmount(), priorTransaction.getTransactionReference());
            }

            Wallet wallet = walletRepository.findByPhoneNumberWithLock(request.getPhoneNumber())
                    .orElseThrow(() -> new EntityNotFoundException("No wallet found for phone number: " + request.getPhoneNumber()));

            if (!passwordEncoder.matches(request.getTransactionPin(), wallet.getPin())) {
                throw new InvalidPinException("Pin doesn't match");
            }

            BigDecimal balanceBefore = wallet.getMirrorBalance();
            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());
            wallet.setMirrorBalance(balanceAfter);

            String description = String.format("Online wallet funded with %s", request.getAmount());

            Transaction transaction = transactionUtils.createTransaction(request.getRequestRef(), ONLINE_WALLET_FUNDING, CREDIT, null, null, null, null, null, wallet.getWalletId(), wallet.getUser().getUsername(), wallet.getPhoneNumber(), balanceBefore, balanceAfter, request.getAmount(), description, SUCCESS, null);

            try {
                transactionRepository.save(transaction);
            } catch (DataIntegrityViolationException dup) {
                Transaction priorTransaction = transactionRepository.findByRequestRef(request.getRequestRef())
                        .orElseThrow(() -> dup);
                return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", priorTransaction.getAmount(), priorTransaction.getTransactionReference());
            }

            walletRepository.save(wallet);

            return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", request.getAmount(), transaction.getTransactionReference());

        } catch (EmptyFieldException | InvalidInputException | EntityNotFoundException | InvalidPinException | InsufficientFundsException | InsufficientBalanceException | BadRequestException ex) {
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new InternalServerErrorException("Transaction is being processed concurrently. Please retry.", ex);
        } catch (JpaSystemException | TransactionSystemException ex) {
            throw new InternalServerErrorException("Database transaction failed.", ex);
        } catch (Exception ex) {
            throw new InternalServerErrorException("Transfer failed. Please try again later.", ex);
        }
    }
}