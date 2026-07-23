package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.AlreadyExistException;
import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.InvalidInputException;
import com.sp.sync_point.exception.SystemWalletInsufficientBalanceException;
import com.sp.sync_point.exception.WalletNotFoundException;
import com.sp.sync_point.model.SystemWallet;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.User;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.SystemWalletRepository;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletFundingRecordRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.utils.TransactionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.sp.sync_point.constants.GeneralConstants.SYSTEM_WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletProvisioningServiceImplTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private UniqueValueGeneratorService uniqueValueService;
    @Mock
    private SystemWalletRepository systemWalletRepo;
    @Mock
    private WalletFundingRecordRepository walletFundingRecordRepo;
    @Mock
    private TransactionUtils transactionUtils;
    @Mock
    private TransactionRepository transactionRepo;

    private WalletProvisioningServiceImpl service;

    private User user;
    private SystemWallet systemWallet;

    @BeforeEach
    void setUp() {
        service = new WalletProvisioningServiceImpl(
                walletRepository, uniqueValueService, systemWalletRepo, walletFundingRecordRepo,
                transactionUtils, transactionRepo);

        user = new User();
        user.setId(1L);
        user.setPhoneNumber("08010000001");
        user.setUsername("jane-doe");

        systemWallet = new SystemWallet();
        systemWallet.setWalletId("SYSTEM-1");
        systemWallet.setAvailableBalance(new BigDecimal("5000000.00"));
        systemWallet.setCurrentLiability(BigDecimal.ZERO);
        systemWallet.setTotalDisbursed(BigDecimal.ZERO);
        systemWallet.setActiveFundedUsers(0L);
        systemWallet.setTotalUsersFunded(0L);
    }

    @Test
    void setupWallet_nullUserId_throwsEmptyFieldException() {
        user.setId(null);

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(EmptyFieldException.class);
        verifyNoWalletCreationSideEffects();
    }

    @Test
    void setupWallet_blankPhoneNumber_throwsEmptyFieldException() {
        user.setPhoneNumber(" ");

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(EmptyFieldException.class);
        verifyNoWalletCreationSideEffects();
    }

    @Test
    void setupWallet_invalidPhoneFormat_throwsInvalidInputException() {
        user.setPhoneNumber("not-a-phone");

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(InvalidInputException.class);
        verifyNoWalletCreationSideEffects();
    }

    @Test
    void setupWallet_walletAlreadyExists_throwsAlreadyExistException() {
        Wallet existing = new Wallet();
        existing.setWalletId("WM-EXISTING");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(AlreadyExistException.class);
        verify(systemWalletRepo, never()).findByIdForUpdate(any());
    }

    @Test
    void setupWallet_systemWalletNotFound_throwsWalletNotFoundException() {
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());
        when(systemWalletRepo.findByIdForUpdate(SYSTEM_WALLET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void setupWallet_systemWalletInsufficientBalance_throwsSystemWalletInsufficientBalanceException() {
        systemWallet.setAvailableBalance(new BigDecimal("100.00"));
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());
        when(systemWalletRepo.findByIdForUpdate(SYSTEM_WALLET_ID)).thenReturn(Optional.of(systemWallet));

        assertThatThrownBy(() -> service.setupWallet(user))
                .isInstanceOf(SystemWalletInsufficientBalanceException.class);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void setupWallet_happyPath_debitsSystemWalletAndCreatesFundedWallet() {
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());
        when(systemWalletRepo.findByIdForUpdate(SYSTEM_WALLET_ID)).thenReturn(Optional.of(systemWallet));
        when(uniqueValueService.generateWalletId()).thenReturn("WM-NEW");
        when(transactionUtils.createTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Transaction());

        service.setupWallet(user);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        Wallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getWalletId()).isEqualTo("WM-NEW");
        assertThat(savedWallet.getPhoneNumber()).isEqualTo("08010000001");
        assertThat(savedWallet.getMirrorBalance()).isEqualByComparingTo("1000000");

        // System wallet debited by the funded amount, liability/disbursed/user
        // counters incremented — this is the "treasury accounting" invariant.
        assertThat(systemWallet.getAvailableBalance()).isEqualByComparingTo("4000000.00");
        assertThat(systemWallet.getCurrentLiability()).isEqualByComparingTo("1000000");
        assertThat(systemWallet.getTotalDisbursed()).isEqualByComparingTo("1000000");
        assertThat(systemWallet.getActiveFundedUsers()).isEqualTo(1L);
        assertThat(systemWallet.getTotalUsersFunded()).isEqualTo(1L);

        verify(transactionRepo).save(any(Transaction.class));
        verify(walletFundingRecordRepo).save(any());
        verify(systemWalletRepo).save(systemWallet);
    }

    private void verifyNoWalletCreationSideEffects() {
        verify(walletRepository, never()).save(any());
        verify(systemWalletRepo, never()).save(any());
        verify(transactionRepo, never()).save(any());
    }
}