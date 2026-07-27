package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.common.security.TransferSignatureVerifier;
import com.sp.syncPoint.common.util.UniqueValueGeneratorService;
import com.sp.syncPoint.feature.transaction.dto.ProcessingResult;
import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.transaction.enums.EntryType;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.enums.TransactionType;
import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the batch sync engine under signature-authorized, audit-only
 * settlement.
 * <p>
 * Two things define this suite's shape:
 * <ul>
 *   <li><b>Authorization is a signature, not caller ownership.</b> There is no
 *       {@code CurrentUserService} anymore; the collaborator is
 *       {@link TransferSignatureVerifier}, mocked here so each test declares
 *       whether the transfer's signature verifies.</li>
 *   <li><b>Sync is audit-only.</b> A P2P transfer moves value between two
 *       device ledgers, not between mirrors, so a successful sync records the
 *       DEBIT + CREDIT but saves no wallet and moves no mirror balance —
 *       neither the sender's nor the receiver's. There is no server-side
 *       insufficient-balance path either: an amount larger than the sender's
 *       mirror is still recorded, because the mirror is not the source of an
 *       offline spend.</li>
 * </ul>
 */
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
    private TransferSignatureVerifier signatureVerifier;

    // DtoMapper has no dependencies, so we use the real implementation
    // rather than mocking it — this exercises the actual mapping logic too.
    private final DtoMapper mapper = new DtoMapper();
    // Real assembler over the real mapper, so response-shape assertions
    // (counts, description, data map) still exercise the actual logic.
    private final TransactionResponseAssembler responseAssembler = new TransactionResponseAssembler(mapper);

    private TransactionSyncServiceImpl service;

    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        service = new TransactionSyncServiceImpl(
                transactionRepository, walletRepository, lockService, mapper,
                idempotencyValidator, transactionValidator, valueGeneratorService, signatureVerifier,
                responseAssembler);

        User senderUser = new User();
        senderUser.setUsername("sender-user");

        User receiverUser = new User();
        receiverUser.setUsername("receiver-user");

        senderWallet = new Wallet();
        senderWallet.setWalletId("WM-SENDER");
        senderWallet.setPhoneNumber("08010000001");
        senderWallet.setMirrorBalance(new BigDecimal("5000.00"));
        senderWallet.setSigningPublicKey("SENDER-PUBLIC-KEY"); // verifier is mocked; value is irrelevant
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
        request.setSignature("BASE64-SIGNATURE");
        return request;
    }

    /** Stubs a valid signature for the (mocked) verifier. */
    private void signatureIsValid() {
        when(signatureVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void syncTransactions_allAlreadyProcessed_returnsIdempotentResponseWithoutTouchingWallets() {
        SyncTransactionRequest request = validRequest();
        Transaction prior = new Transaction();
        prior.setTransactionReference("TXN-1");
        prior.setTransactionType(TransactionType.P2P_TRANSFER);
        prior.setEntryType(EntryType.DEBIT);
        prior.setStatus(TransactionStatus.COMPLETED);

        when(idempotencyValidator.getProcessedTransactions(List.of("TXN-1")))
                .thenReturn(List.of(prior));

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(transactionValidator).validateTransaction(request);
        verifyNoInteractions(walletRepository, lockService, signatureVerifier);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getDescription()).contains("Idempotent");
    }

    @Test
    void syncTransactions_newTransfer_recorded_noMirrorMoved() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));
        when(valueGeneratorService.generateSyncId()).thenReturn("SYNC-1");
        signatureIsValid();

        BaseResponse response = service.syncTransactions(List.of(request));

        // Audit-only: neither mirror moves, and no wallet is saved. The two
        // ledger records are still persisted.
        assertThat(senderWallet.getMirrorBalance()).isEqualByComparingTo("5000.00");
        assertThat(receiverWallet.getMirrorBalance()).isEqualByComparingTo("1000.00");

        verify(transactionRepository).saveAll(anyList());
        verify(walletRepository, never()).save(any());
        verify(walletRepository, never()).saveAll(anyList());
        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(1);
        assertThat(data.get("failedCount")).isEqualTo(0);
        assertThat(data.get("skippedCount")).isEqualTo(0);
    }

    @Test
    void syncTransactions_amountExceedsSenderMirror_stillRecorded() {
        // The server does not re-check sender funds — that is the client's
        // responsibility. An amount larger than the sender's mirror is still
        // recorded: the mirror is not the source of an offline spend, and the
        // sync moves no balance either way.
        SyncTransactionRequest request = validRequest();
        request.setAmount(new BigDecimal("999999.00"));

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));
        when(valueGeneratorService.generateSyncId()).thenReturn("SYNC-1");
        signatureIsValid();

        BaseResponse response = service.syncTransactions(List.of(request));

        assertThat(senderWallet.getMirrorBalance()).isEqualByComparingTo("5000.00");
        assertThat(receiverWallet.getMirrorBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository).saveAll(anyList());
        verify(walletRepository, never()).save(any());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(1);
        assertThat(data.get("failedCount")).isEqualTo(0);
    }

    @Test
    void syncTransactions_invalidSignature_rejected_persistsNothing() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));
        when(signatureVerifier.verify(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        BaseResponse response = service.syncTransactions(List.of(request));

        // Nothing persisted, no balance moved, locks still released.
        assertThat(senderWallet.getMirrorBalance()).isEqualByComparingTo("5000.00");
        assertThat(receiverWallet.getMirrorBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).saveAll(anyList());
        verify(walletRepository, never()).save(any());
        verify(walletRepository, never()).saveAll(anyList());
        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(0);
        assertThat(data.get("failedCount")).isEqualTo(1);
        assertThat(data.get("skippedCount")).isEqualTo(0);
    }

    @Test
    void syncTransactions_senderWalletDoesNotMatchSignedSender_rejected() {
        // The wallet reached via senderPhone is not the signed sender wallet —
        // an attempt to borrow a signature from a different sender.
        SyncTransactionRequest request = validRequest();
        senderWallet.setWalletId("WM-SOMEONE-ELSE");

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(signatureVerifier, never()).verify(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(transactionRepository, never()).saveAll(anyList());
        verify(walletRepository, never()).save(any());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(0);
        assertThat(data.get("failedCount")).isEqualTo(1);
    }

    @Test
    void syncTransactions_receiverWalletDoesNotMatchSignedReceiver_rejected() {
        // Redirection attempt: a valid "pay WM-RECEIVER" signature pointed at a
        // wallet reached via a different receiverPhone.
        SyncTransactionRequest request = validRequest();
        receiverWallet.setWalletId("WM-SOMEONE-ELSE");

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.of(receiverWallet));

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(signatureVerifier, never()).verify(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletRepository, never()).save(any());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("completedCount")).isEqualTo(0);
        assertThat(data.get("failedCount")).isEqualTo(1);
    }

    @Test
    void syncTransactions_innerIdempotencyRace_skipsWithoutError() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        // Simulates a concurrent request having already applied this transactionRef
        // between the outer pre-filter and lock acquisition (TOCTOU).
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(true);

        BaseResponse response = service.syncTransactions(List.of(request));

        verify(walletRepository, never()).findByPhoneNumberWithLock(anyString());
        verifyNoInteractions(signatureVerifier);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("skippedCount")).isEqualTo(1);
    }

    @Test
    void syncTransactions_locksAlwaysReleasedEvenOnError() {
        SyncTransactionRequest request = validRequest();

        when(idempotencyValidator.getProcessedTransactions(anyList())).thenReturn(List.of());
        when(lockService.acquireAllLocks(anyList())).thenReturn(List.of("08010000001", "08010000002"));
        when(transactionRepository.existsByTransactionReference("TXN-1")).thenReturn(false);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByPhoneNumberWithLock("08010000002")).thenReturn(Optional.empty());

        // processSingleTransfer throws internally (receiver wallet not found); the
        // outer loop catches it per-request and records a failure, but the lock
        // must still be released via the finally block either way.
        service.syncTransactions(List.of(request));

        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));
    }

    @Test
    void syncTransactions_fieldValidationFailure_shortCircuitsBeforeLocking() {
        SyncTransactionRequest request = validRequest();

        doThrow(new BadRequestException("Sender and receiver cannot be the same"))
                .when(transactionValidator).validateTransaction(request);
        // validateTransaction runs first in the loop and short-circuits the whole
        // batch before any idempotency lookup or locking.

        assertThatThrownBy(() -> service.syncTransactions(List.of(request)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(lockService, signatureVerifier);
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
        signatureIsValid();

        ProcessingResult result = service.processSingleTransfer(request);

        assertThat(result.isSuccess()).isTrue();
        verify(lockService).acquireAllLocks(List.of("08010000001", "08020000002"));
    }
}