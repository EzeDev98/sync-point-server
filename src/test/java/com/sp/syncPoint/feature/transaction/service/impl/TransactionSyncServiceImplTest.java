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

    private final DtoMapper mapper = new DtoMapper();
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

        service.syncTransactions(List.of(request));

        verify(lockService).releaseAllLocks(List.of("08010000001", "08010000002"));
    }

    @Test
    void syncTransactions_fieldValidationFailure_shortCircuitsBeforeLocking() {
        SyncTransactionRequest request = validRequest();

        doThrow(new BadRequestException("Sender and receiver cannot be the same"))
                .when(transactionValidator).validateTransaction(request);

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