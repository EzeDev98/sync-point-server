package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.WalletTransferRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InsufficientBalanceException;
import com.sp.sync_point.exception.InsufficientFundsException;
import com.sp.sync_point.exception.InternalServerErrorException;
import com.sp.sync_point.exception.InvalidInputException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.User;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.WalletFundingResponse;
import com.sp.sync_point.utils.TransactionUtils;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletFundingServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionUtils transactionUtils;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUserService currentUserService;

    private WalletFundingServiceImpl service;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new WalletFundingServiceImpl(
                transactionRepository, walletRepository, transactionUtils, passwordEncoder, currentUserService);

        User user = new User();
        user.setUsername("jane-doe");

        wallet = new Wallet();
        wallet.setWalletId("WM-1");
        wallet.setPhoneNumber("08010000001");
        wallet.setPin("hashed-pin");
        wallet.setMirrorBalance(new BigDecimal("5000.00"));
        wallet.setOfflineLimit(new BigDecimal("2000.00"));
        wallet.setUser(user);
    }

    private WalletTransferRequest validRequest(BigDecimal amount) {
        WalletTransferRequest request = new WalletTransferRequest();
        request.setPhoneNumber("08010000001");
        request.setTransactionPin("1234");
        request.setAmount(amount);
        request.setRequestRef("REQ-1");
        return request;
    }

    // ── fundOfflineWallet ──────────────────────────────────────────

    @Test
    void fundOfflineWallet_missingPhoneNumber_throwsEmptyFieldException() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        request.setPhoneNumber(" ");

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(EmptyFieldException.class);
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void fundOfflineWallet_missingPin_throwsEmptyFieldException() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        request.setTransactionPin("");

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void fundOfflineWallet_zeroAmount_throwsInvalidInputException() {
        WalletTransferRequest request = validRequest(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void fundOfflineWallet_missingRequestRef_throwsEmptyFieldException() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        request.setRequestRef(null);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void fundOfflineWallet_callerDoesNotOwnPhoneNumber_isDenied() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        doThrow(new AccessDeniedException("denied"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(walletRepository);
    }

    @Test
    void fundOfflineWallet_existingRequestRef_returnsPriorTransactionWithoutTouchingWallet() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        Transaction prior = new Transaction();
        prior.setAmount(new BigDecimal("500.00"));
        prior.setTransactionReference("TXN-PRIOR");

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.of(prior));

        WalletFundingResponse response = service.fundOfflineWallet(request);

        assertThat(response.getTransactionRef()).isEqualTo("TXN-PRIOR");
        assertThat(response.getMessage()).isEqualTo("Wallet funded successfully");
        verifyNoInteractions(walletRepository);
    }

    @Test
    void fundOfflineWallet_wrongPin_throwsInvalidPinException() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(InvalidPinException.class);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void fundOfflineWallet_walletNotFound_throwsEntityNotFoundException() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void fundOfflineWallet_amountAboveOfflineLimit_throwsBadRequestException() {
        // offlineLimit is 2000.00 on the fixture wallet
        WalletTransferRequest request = validRequest(new BigDecimal("2500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void fundOfflineWallet_amountAboveMirrorBalance_throwsInsufficientBalanceException() {
        wallet.setMirrorBalance(new BigDecimal("100.00"));
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void fundOfflineWallet_negativeMirrorBalance_throwsInsufficientFundsException() {
        wallet.setMirrorBalance(new BigDecimal("-50.00"));
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void fundOfflineWallet_happyPath_debitsMirrorBalanceAndSavesTransaction() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        Transaction newTransaction = new Transaction();
        newTransaction.setTransactionReference("TXN-NEW");

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(transactionUtils.createTransaction(
                eq("REQ-1"), any(), any(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), any(BigDecimal.class),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                any(BigDecimal.class), anyString(), any(), isNull()))
                .thenReturn(newTransaction);

        WalletFundingResponse response = service.fundOfflineWallet(request);

        assertThat(wallet.getMirrorBalance()).isEqualByComparingTo("4500.00");
        assertThat(response.getTransactionRef()).isEqualTo("TXN-NEW");
        verify(walletRepository).save(wallet);
    }

    @Test
    void fundOfflineWallet_concurrentDuplicateSave_replaysExistingTransaction() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));
        Transaction newTransaction = new Transaction();
        Transaction winningTransaction = new Transaction();
        winningTransaction.setAmount(new BigDecimal("500.00"));
        winningTransaction.setTransactionReference("TXN-WINNER");

        when(transactionRepository.findByRequestRef("REQ-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winningTransaction));
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(transactionUtils.createTransaction(
                eq("REQ-1"), any(), any(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), any(BigDecimal.class),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                any(BigDecimal.class), anyString(), any(), isNull()))
                .thenReturn(newTransaction);
        when(transactionRepository.save(newTransaction))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        WalletFundingResponse response = service.fundOfflineWallet(request);

        assertThat(response.getTransactionRef()).isEqualTo("TXN-WINNER");
        // The racing request that lost must not also mutate the wallet balance.
        verify(walletRepository, never()).save(wallet);
    }

    @Test
    void transferToOnlineAccount_happyPath_creditsMirrorBalanceWithNoOfflineLimitCheck() {
        // Deliberately above offlineLimit (2000.00) to prove the offline-limit
        // check that applies to fundOfflineWallet does NOT apply here.
        WalletTransferRequest request = validRequest(new BigDecimal("3000.00"));
        Transaction newTransaction = new Transaction();
        newTransaction.setTransactionReference("TXN-ONLINE");

        when(transactionRepository.findByRequestRef("REQ-1")).thenReturn(Optional.empty());
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(transactionUtils.createTransaction(
                eq("REQ-1"), any(), any(), isNull(), isNull(), isNull(), isNull(), isNull(),
                anyString(), anyString(), anyString(),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), anyString(), any(), isNull()))
                .thenReturn(newTransaction);

        WalletFundingResponse response = service.transferToOnlineAccount(request);

        assertThat(wallet.getMirrorBalance()).isEqualByComparingTo("8000.00");
        assertThat(response.getTransactionRef()).isEqualTo("TXN-ONLINE");
    }

    @Test
    void unexpectedRuntimeException_isWrappedAsInternalServerError() {
        WalletTransferRequest request = validRequest(new BigDecimal("500.00"));

        when(transactionRepository.findByRequestRef("REQ-1")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.fundOfflineWallet(request))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage("Transfer failed. Please try again later.");
    }
}