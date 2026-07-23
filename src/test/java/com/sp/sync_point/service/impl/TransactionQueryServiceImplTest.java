package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.EmptyFieldException;
import com.sp.sync_point.exception.WalletNotFoundException;
import com.sp.sync_point.model.Transaction;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.TransactionResponse;
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
        transaction.setTransactionType(com.sp.sync_point.enums.TransactionType.P2P_TRANSFER);
        transaction.setEntryType(com.sp.sync_point.enums.EntryType.DEBIT);
        transaction.setStatus(com.sp.sync_point.enums.TransactionStatus.COMPLETED);

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
    void getTransactionsByWmUniqueId_nullId_throwsEmptyFieldException() {
        assertThatThrownBy(() -> service.getTransactionsByWmUniqueId((String) null))
                .isInstanceOf(EmptyFieldException.class);
    }

    @Test
    void getTransactionsByWmUniqueId_walletNotFound_throwsWalletNotFoundException() {
        when(walletRepository.findByWalletId("WM-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionsByWmUniqueId("WM-1"))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void getTransactionsByWmUniqueId_callerDoesNotOwnWallet_isDenied() {
        Wallet wallet = new Wallet();
        wallet.setPhoneNumber("08010000001");
        when(walletRepository.findByWalletId("WM-1")).thenReturn(Optional.of(wallet));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.getTransactionsByWmUniqueId("WM-1"))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).findTransactionByWalletId(any());
    }

    @Test
    void getTransactionsByWmUniqueId_ownedWallet_returnsTransactions() {
        Wallet wallet = new Wallet();
        wallet.setPhoneNumber("08010000001");
        Transaction transaction = new Transaction();
        transaction.setTransactionReference("TXN-1");
        transaction.setTransactionType(com.sp.sync_point.enums.TransactionType.P2P_TRANSFER);
        transaction.setEntryType(com.sp.sync_point.enums.EntryType.CREDIT);
        transaction.setStatus(com.sp.sync_point.enums.TransactionStatus.COMPLETED);

        when(walletRepository.findByWalletId("WM-1")).thenReturn(Optional.of(wallet));
        when(transactionRepository.findTransactionByWalletId("WM-1")).thenReturn(List.of(transaction));

        List<TransactionResponse> responses = service.getTransactionsByWmUniqueId("WM-1");

        assertThat(responses).hasSize(1);
    }

    @Test
    void getTransactionsBetweenTwoParties_neitherOwnedByCaller_isDenied() {
        when(walletRepository.findByWalletId("WM-SENDER")).thenReturn(Optional.empty());
        when(walletRepository.findByWalletId("WM-RECEIVER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionsByWmUniqueId("WM-SENDER", "WM-RECEIVER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTransactionsBetweenTwoParties_callerIsReceiver_isAllowed() {
        Wallet senderWallet = new Wallet();
        senderWallet.setPhoneNumber("08010000001");
        Wallet receiverWallet = new Wallet();
        receiverWallet.setPhoneNumber("08010000002");

        when(walletRepository.findByWalletId("WM-SENDER")).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByWalletId("WM-RECEIVER")).thenReturn(Optional.of(receiverWallet));
        when(currentUserService.ownsPhoneNumber("08010000001")).thenReturn(false);
        when(currentUserService.ownsPhoneNumber("08010000002")).thenReturn(true);
        when(transactionRepository.findBySenderWalletIdOrReceiverWalletId("WM-SENDER", "WM-RECEIVER"))
                .thenReturn(List.of());

        assertThat(service.getTransactionsByWmUniqueId("WM-SENDER", "WM-RECEIVER")).isEmpty();
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
        when(walletRepository.findByWalletId("WM-1")).thenReturn(Optional.of(wallet));

        BaseResponse response = service.saveMirrorBalance("WM-1", new BigDecimal("750.00"));

        assertThat(wallet.getMirrorBalance()).isEqualByComparingTo("750.00");
        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(walletRepository).save(wallet);
    }

    @Test
    void saveMirrorBalance_walletNotFound_throwsWalletNotFoundException() {
        when(walletRepository.findByWalletId("WM-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveMirrorBalance("WM-1", new BigDecimal("750.00")))
                .isInstanceOf(WalletNotFoundException.class);
    }
}