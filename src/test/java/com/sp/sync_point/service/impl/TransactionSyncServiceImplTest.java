package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.ProcessingResult;
import com.sp.sync_point.dto.SyncTransactionRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.User;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSyncServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionLockService lockService;
    @Mock
    private IdempotencyValidator idempotencyValidator;
    @Mock
    private TransactionValidator transactionValidator;
    @Mock
    private UniqueValueGeneratorService valueGeneratorService;
    @Mock
    private CurrentUserService currentUserService;

    // DtoMapper has no dependencies, so we use the real implementation
    // rather than mocking it — this exercises the actual mapping logic too.
    private final DtoMapper mapper = new DtoMapper();

    private TransactionSyncServiceImpl service;

    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        service = new TransactionSyncServiceImpl(
                transactionRepository, walletRepository, lockService, mapper,
                idempotencyValidator, transactionValidator, valueGeneratorService, currentUserService);

        User senderUser = new User();
        senderUser.setUsername("sender-user");

        User receiverUser = new User();
        receiverUser.setUsername("receiver-user");

        senderWallet = new Wallet();
        senderWallet.setWalletId("WM-SENDER");
        senderWallet.setPhoneNumber("08010000001");
        senderWallet.setMirrorBalance(new BigDecimal("5000.00"));
        senderWallet.setUser(senderUser);

        receiverWallet = new Wallet();
        receiverWallet.setWalletId("WM-RECEIVER");
        receiverWallet.setPhoneNumber("08010000002");
        receiverWallet.setMirrorBalance(new BigDecimal("1000.00"));
        receiverWallet.setUser(receiverUser);
    }

    private SyncTransactionRequest validRequest() {
        SyncTransactionRequest request = new SyncTransactionRequest();
        request.setTransactionRef("TXN-1");
        request.setRequestRef("REQ-1");
        request.setType("P2P_TRANSFER");
        request.setEntryType("DEBIT");
        request.setCurrency("NGN");
        request.setSenderWalletId("WM-SENDER");
        request.setSenderPhone("08010000001");
        request.setReceiverWalletId("WM-RECEIVER");
        request.setReceiverPhone("08010000002");
        request.setAmount(new BigDecimal("500.00"));
        request.setCreatedAt(Instant.now());
        return request;
    }

    @Test
    void syncTransactions_allAlreadyProcessed_returnsIdempotentResponseWithoutTouchingWallets() {
        SyncTransactionRequest request = validRequest();
        Transaction prior = new Transaction();
        prior.setTransactionReference("TXN-1");
        prior.setTransactionType(com.sp.sync_point.enums.TransactionType.P2P_TRANSFER);
        prior.setEntryType(com.sp.sync_point.enums.EntryType.DEBIT);
        prior.setStatus(com.sp.sync_point.enums.TransactionStatus.COMPLETED);

        when(idempotencyValidator.getProcessedTransactions(List.of("TXN-1")))
                .thenReturn(List.of(prior));

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(transactionValidator).validateTransaction(request);
        verify(currentUserService).assertOwnsPhoneNumber("08010000001");
        verifyNoInteractions(walletRepository, lockService);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getDescription()).contains("Idempotent");
    }

    @Test
    void syncTransactions_newTransfer_completesAndUpdatesBothWalletBalances() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));
        when(valueGeneratorService.generateSyncId()).thenReturn("SYNC-1");

        BaseResponse response = service.syncTransactions(List.of(request));

        assertThat(senderWallet.getMirrorBalance()).isEqualByComparingTo("4500.00");
        assertThat(receiverWallet.getMirrorBalance()).isEqualByComparingTo("1500.00");
        verify(transactionRepository).saveAll(anyList());
        verify(walletRepository).saveAll(List.of(senderWallet, receiverWallet));
        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(1);
        assertThat(data.get("failedCount")).isEqualTo(0);
        assertThat(data.get("skippedCount")).isEqualTo(0);
    }

    @Test
    void syncTransactions_insufficientBalance_recordsFailedPairWithoutMovingFunds() {
        SyncTransactionRequest request = validRequest();
        request.setAmount(new BigDecimal("999999.00"));

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));

        BigDecimal senderBalanceBefore = senderWallet.getMirrorBalance();

        BaseResponse response = service.syncTransactions(List.of(request));

        assertThat(senderWallet.getMirrorBalance()).isEqualByComparingTo(senderBalanceBefore);
        verify(walletRepository, never()).saveAll(anyList());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(0);
        // Pre-existing asymmetry carried over unchanged from the original
        // TransactionServiceImpl: completedCount is halved (a completed
        // transfer produces a DEBIT+CREDIT pair) but failedCount is NOT
        // halved, so one failed transfer reports as 2, not 1. Flagged to
        // the team as a possible follow-up fix; not changed here since it
        // would alter response data.
        assertThat(data.get("failedCount")).isEqualTo(2);
    }

    @Test
    void syncTransactions_innerIdempotencyRace_skipsWithoutError() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        // Simulates a concurrent request having already applied this transactionRef
        // between the outer pre-filter and lock acquisition (fix issue #12 — TOCTOU).
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(true);

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(walletRepository, never()).findByPhoneNumberWithLock(anyString());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("skippedCount")).isEqualTo(1);
    }

    @Test
    void syncTransactions_locksAlwaysReleasedEvenOnFailure() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.empty());

        // processSingleTransfer throws internally (receiver wallet not found); the
        // outer syncTransactions loop catches it per-request and records a failure,
        // but the lock must still be released via the finally block either way.
        service.syncTransactions(List.of(request));

        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));
    }

    @Test
    void processSingleTransfer_sendersMissingOwnership_isRejectedBeforeAnyLocking() {
        SyncTransactionRequest request = validRequest();
        request.setReceiverPhone(request.getSenderPhone());

        doThrow(new BadRequestException("Sender and receiver cannot be the same"))
                .when(transactionValidator).validateTransaction(request);
        // validateTransaction runs first in the loop; short-circuits before our
        // own validateTransactionRequest and before any locking occurs.

        assertThatThrownBy(() -> service.syncTransactions(List.of(request)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(lockService);
    }

    @Test
    void processSingleTransfer_callerDoesNotOwnSenderPhone_isDenied() {
        SyncTransactionRequest request = validRequest();
        doThrow(new AccessDeniedException("You do not have permission to access this resource"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.syncTransactions(List.of(request)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(lockService);
    }

    @Test
    void processSingleTransfer_acquiresLocksInSortedOrder() {
        SyncTransactionRequest request = validRequest();
        request.setSenderPhone("08020000002");
        request.setReceiverPhone("08010000001");
        senderWallet.setPhoneNumber("08020000002");
        receiverWallet.setPhoneNumber("08010000001");

        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08020000002")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(receiverWallet));
        when(lockService.acquireAllLocks(List.of("08010000001", "08020000002")))
                .thenReturn(List.of("08010000001", "08020000002"));
        when(valueGeneratorService.generateSyncId()).thenReturn("SYNC-1");

        ProcessingResult result = service.processSingleTransfer(request);

        assertThat(result.isSuccess()).isTrue();
        verify(lockService).acquireAllLocks(List.of("08010000001", "08020000002"));
    }
}