package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;

import com.sp.syncPoint.common.exception.EmptyFieldException;
import com.sp.syncPoint.common.exception.WalletNotFoundException;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private CurrentUserService currentUserService;

    private final DtoMapper mapper = new DtoMapper();

    private TransactionQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionQueryServiceImpl(transactionRepository, walletRepository, mapper, currentUserService);
    }

    @Test
    void getAllTransactions_returnsMappedResponses() {
        Transaction transaction = new Transaction();
        transaction.setTransactionReference("TXN-1");
        transaction.setTransactionType(com.sp.syncPoint.feature.transaction.enums.TransactionType.P2P_TRANSFER);
        transaction.setEntryType(com.sp.syncPoint.feature.transaction.enums.EntryType.DEBIT);
        transaction.setStatus(com.sp.syncPoint.feature.transaction.enums.TransactionStatus.COMPLETED);

        when(transactionRepository.findAll()).thenReturn(List.of(transaction));

        List<TransactionResponse> responses = service.getAllTransactions();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTransactionReference()).isEqualTo("TXN-1");
    }

    @Test
    void getAllTransactions_noTransactions_returnsEmptyList() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        assertThat(service.getAllTransactions()).isEmpty();
    }

    @Test
    void getTransactionsBySpId_nullId_throwsEmptyFieldException() {
        assertThatThrownBy(() -> service.getTransactionsBySpId((String) null))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void getTransactionsBySpId_walletNotFound_throwsWalletNotFoundException() {
        when(walletRepository.findByWalletId("SP-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionsBySpId("SP-1"))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void getTransactionsBySpId_callerDoesNotOwnWallet_isDenied() {
        Wallet wallet = new Wallet();
        wallet.setPhoneNumber("08010000001");
        when(walletRepository.findByWalletId("SP-1")).thenReturn(Optional.of(wallet));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.getTransactionsBySpId("SP-1"))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).findTransactionByWalletId(any());
    }

    @Test
    void getTransactionsBySpId_ownedWallet_returnsTransactions() {
        Wallet wallet = new Wallet();
        wallet.setPhoneNumber("08010000001");
        Transaction transaction = new Transaction();
        transaction.setTransactionReference("TXN-1");
        transaction.setTransactionType(com.sp.syncPoint.feature.transaction.enums.TransactionType.P2P_TRANSFER);
        transaction.setEntryType(com.sp.syncPoint.feature.transaction.enums.EntryType.CREDIT);
        transaction.setStatus(com.sp.syncPoint.feature.transaction.enums.TransactionStatus.COMPLETED);

        when(walletRepository.findByWalletId("SP-1")).thenReturn(Optional.of(wallet));
        when(transactionRepository.findTransactionByWalletId("SP-1")).thenReturn(List.of(transaction));

        List<TransactionResponse> responses = service.getTransactionsBySpId("SP-1");

        assertThat(responses).hasSize(1);
    }

    @Test
    void getTransactionsBetweenTwoParties_neitherOwnedByCaller_isDenied() {
        when(walletRepository.findByWalletId("SP-SENDER")).thenReturn(Optional.empty());
        when(walletRepository.findByWalletId("SP-RECEIVER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionsBySpId("SP-SENDER", "SP-RECEIVER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTransactionsBetweenTwoParties_callerIsReceiver_isAllowed() {
        Wallet senderWallet = new Wallet();
        senderWallet.setPhoneNumber("08010000001");
        Wallet receiverWallet = new Wallet();
        receiverWallet.setPhoneNumber("08010000002");

        when(walletRepository.findByWalletId("SP-SENDER")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByWalletId("SP-RECEIVER")).thenReturn(Optional.of(receiverWallet));
        when(currentUserService.ownsPhoneNumber("08010000001")).thenReturn(false);
        when(currentUserService.ownsPhoneNumber("08010000002")).thenReturn(true);
        when(transactionRepository.findBySenderWalletIdOrReceiverWalletId("SP-SENDER", "SP-RECEIVER"))
                .thenReturn(List.of());

        assertThat(service.getTransactionsBySpId("SP-SENDER", "SP-RECEIVER")).isEmpty();
    }

    @Test
    void deleteTransactions_hasTransactions_returnsSuccessMessage() {
        when(transactionRepository.count()).thenReturn(3L);

        String result = service.deleteTransactions();

        assertThat(result).isEqualTo("All transactions deleted successfully");
        verify(transactionRepository).deleteAll();
    }

    @Test
    void deleteTransactions_noTransactions_returnsNoneFoundMessage() {
        when(transactionRepository.count()).thenReturn(0L);

        assertThat(service.deleteTransactions()).isEqualTo("No transactions found to delete");
    }

    @Test
    void saveMirrorBalance_ownedWallet_persistsAndReturnsSuccess() {
        Wallet wallet = new Wallet();
        wallet.setPhoneNumber("08010000001");
        when(walletRepository.findByWalletId("SP-1")).thenReturn(Optional.of(wallet));

        BaseResponse response = service.saveMirrorBalance("SP-1", new BigDecimal("750.00"));

        assertThat(wallet.getMirrorBalance()).isEqualByComparingTo("750.00");
        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(walletRepository).save(wallet);
    }

    @Test
    void saveMirrorBalance_walletNotFound_throwsWalletNotFoundException() {
        when(walletRepository.findByWalletId("SP-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveMirrorBalance("SP-1", new BigDecimal("750.00")))
                .isInstanceOf(WalletNotFoundException.class);
    }
}