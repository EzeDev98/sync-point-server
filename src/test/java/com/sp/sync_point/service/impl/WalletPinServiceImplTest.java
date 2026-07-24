package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.exception.PinChangeLockedException;
import com.sp.sync_point.exception.TransactionPinNotSetException;
import com.sp.sync_point.exception.WalletLockedException;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.service.EntityUtilityService;
import com.sp.sync_point.service.ResponseService;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPinServiceImplTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PinValidator pinValidator;
    @Mock
    private ResponseService responseService;
    @Mock
    private EntityUtilityService entityUtilityService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private WalletLockoutService walletLockoutService;

    private WalletPinServiceImpl service;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new WalletPinServiceImpl(
                walletRepository, passwordEncoder, pinValidator, responseService,
                entityUtilityService, currentUserService, walletLockoutService);

        wallet = new Wallet();
        wallet.setWalletId("WM-1");
        wallet.setPhoneNumber("08010000001");
    }

    private SetPinRequest setPinRequest(String pin, String confirm) {
        SetPinRequest request = new SetPinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin(pin);
        request.setConfirmNewPin(confirm);
        return request;
    }

    // ── setPin ─────────────────────────────────────────────────────

    @Test
    void setPin_callerDoesNotOwnPhoneNumber_isDenied() {
        SetPinRequest request = setPinRequest("4321", "4321");
        doThrow(new AccessDeniedException("denied"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(AccessDeniedException.class);
        verify(pinValidator, never()).setPinValidation(any());
    }

    @Test
    void setPin_walletNotFound_throwsNotFoundException() {
        SetPinRequest request = setPinRequest("4321", "4321");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void setPin_walletLocked_throwsWalletLockedException() {
        SetPinRequest request = setPinRequest("4321", "4321");
        wallet.setLocked(true);
        wallet.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(WalletLockedException.class);
    }

    @Test
    void setPin_weakPin_throwsBadRequestException() {
        // isWeakPin is checked before the PIN-match check, so a weak PIN is
        // rejected even when pin == confirmNewPin.
        SetPinRequest request = setPinRequest("1234", "1234");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void setPin_mismatchedPins_recordsFailedAttemptAndThrowsBadRequest() {
        SetPinRequest request = setPinRequest("7358", "1234");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
        when(walletLockoutService.recordFailedPinChangeAttempt("08010000001")).thenReturn(1);

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void setPin_alreadySet_throwsBadRequestException() {
        SetPinRequest request = setPinRequest("7358", "7358");
        wallet.setPin("already-hashed");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.setPin(request))
                .isInstanceOf(BadRequestException.class);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void setPin_happyPath_hashesAndSavesPin() {
        SetPinRequest request = setPinRequest("7358", "7358");
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.encode("7358")).thenReturn("hashed-7358");

        BaseResponse response = service.setPin(request);

        assertThat(wallet.getPin()).isEqualTo("hashed-7358");
        assertThat(wallet.getPinAttempts()).isEqualTo(0);
        assertThat(response.getStatusCode()).isEqualTo(201);
        verify(walletRepository).save(wallet);
    }

    // ── changePin ──────────────────────────────────────────────────

    @Test
    void changePin_nullRequest_returnsErrorResponseWithoutTouchingWallet() {
        BaseResponse errorResponse = new BaseResponse();
        when(responseService.createErrorResponse(any(), eq("PIN request is required"), eq(400)))
                .thenReturn(errorResponse);

        BaseResponse response = service.changePin(null);

        assertThat(response).isSameAs(errorResponse);
        verify(currentUserService, never()).assertOwnsPhoneNumber(anyString());
    }

    @Test
    void changePin_walletLocked_returnsErrorResponse() {
        PinRequest request = new PinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin("7358");
        request.setOldPin("1111");

        wallet.setLocked(true);
        wallet.setLockedUntil(LocalDateTime.now().plusMinutes(5));

        when(pinValidator.validateRequiredFields(any(), any(), any(), any())).thenReturn(true);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        BaseResponse errorResponse = new BaseResponse();
        when(responseService.createErrorResponse(any(), eq("Wallet is locked. Please try again later."), anyInt()))
                .thenReturn(errorResponse);

        BaseResponse response = service.changePin(request);

        assertThat(response).isSameAs(errorResponse);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void changePin_sameAsOldPin_returnsErrorResponse() {
        PinRequest request = new PinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin("7358");
        request.setOldPin("1111");
        wallet.setPin("hashed-1111");

        when(pinValidator.validateRequiredFields(any(), any(), any(), any())).thenReturn(true);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1111", "hashed-1111")).thenReturn(true); // old-pin check
        when(passwordEncoder.matches("7358", "hashed-1111")).thenReturn(true); // same-as-old check
        BaseResponse errorResponse = new BaseResponse();
        when(responseService.createErrorResponse(any(), eq("New PIN cannot be the same as old PIN"), anyInt()))
                .thenReturn(errorResponse);

        BaseResponse response = service.changePin(request);

        assertThat(response).isSameAs(errorResponse);
    }

    @Test
    void changePin_wrongOldPin_incrementsPinChangeAttemptsAndReturnsErrorResponse() {
        PinRequest request = new PinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin("7358");
        request.setOldPin("1111");
        wallet.setPin("hashed-1111");

        when(pinValidator.validateRequiredFields(any(), any(), any(), any())).thenReturn(true);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1111", "hashed-1111")).thenReturn(false);
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
        BaseResponse errorResponse = new BaseResponse();
        when(responseService.createErrorResponse(any(), eq("Incorrect old PIN"), anyInt())).thenReturn(errorResponse);

        BaseResponse response = service.changePin(request);

        assertThat(response).isSameAs(errorResponse);
        assertThat(wallet.getPinChangeAttempts()).isEqualTo(1);
        verify(walletRepository).save(wallet);
    }

    @Test
    void changePin_happyPath_hashesNewPinAndResetsAttempts() {
        PinRequest request = new PinRequest();
        request.setPhoneNumber("08010000001");
        request.setPin("7358");
        request.setOldPin("1111");
        wallet.setPin("hashed-1111");
        wallet.setPinChangeAttempts(2);

        when(pinValidator.validateRequiredFields(any(), any(), any(), any())).thenReturn(true);
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.matches("1111", "hashed-1111")).thenReturn(true);
        when(passwordEncoder.matches("7358", "hashed-1111")).thenReturn(false);
        when(passwordEncoder.encode("7358")).thenReturn("hashed-7358");
        BaseResponse successResponse = new BaseResponse();
        when(responseService.createSuccessResponse(any(), eq("PIN changed successfully"))).thenReturn(successResponse);

        BaseResponse response = service.changePin(request);

        assertThat(response).isSameAs(successResponse);
        assertThat(wallet.getPin()).isEqualTo("hashed-7358");
        assertThat(wallet.getPinChangeAttempts()).isEqualTo(0);
        verify(walletRepository).save(wallet);
    }

    // ── isWalletPinSet / verifyPin ─────────────────────────────────

    @Test
    void isWalletPinSet_walletNotFound_throwsTransactionPinNotSetException() {
        when(entityUtilityService.fetchWallet("WM-1")).thenReturn(null);

        assertThatThrownBy(() -> service.isWalletPinSet("WM-1"))
                .isInstanceOf(TransactionPinNotSetException.class);
    }

    @Test
    void isWalletPinSet_callerDoesNotOwnWallet_isDenied() {
        when(entityUtilityService.fetchWallet("WM-1")).thenReturn(wallet);
        doThrow(new AccessDeniedException("denied"))
                .when(currentUserService).assertOwnsPhoneNumber("08010000001");

        assertThatThrownBy(() -> service.isWalletPinSet("WM-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isWalletPinSet_pinSet_returnsTrue() {
        wallet.setPin("hashed-4321");
        when(entityUtilityService.fetchWallet("WM-1")).thenReturn(wallet);

        assertThat(service.isWalletPinSet("WM-1")).isTrue();
    }

    @Test
    void verifyPin_walletHasNoPin_returnsFalseWithoutOwnershipCheck() {
        when(entityUtilityService.fetchWallet("WM-1")).thenReturn(wallet);

        assertThat(service.verifyPin("WM-1", "4321")).isFalse();
        verify(currentUserService, never()).assertOwnsPhoneNumber(anyString());
    }

    @Test
    void verifyPin_correctPin_returnsTrue() {
        wallet.setPin("hashed-4321");
        when(entityUtilityService.fetchWallet("WM-1")).thenReturn(wallet);
        when(passwordEncoder.matches("4321", "hashed-4321")).thenReturn(true);

        assertThat(service.verifyPin("WM-1", "4321")).isTrue();
    }

    // ── resetPin ───────────────────────────────────────────────────

    @Test
    void resetPin_weakPin_throwsInvalidPinException() {
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.resetPin("08010000001", "1234", "1234"))
                .isInstanceOf(InvalidPinException.class);
    }

    @Test
    void resetPin_mismatchedConfirmation_throwsBadRequestException() {
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.resetPin("08010000001", "7358", "1234"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPin_doesNotRequireOldPin_happyPath() {
        wallet.setPin("some-forgotten-hash");
        when(walletRepository.findByPhoneNumberWithLock("08010000001")).thenReturn(Optional.of(wallet));
        when(passwordEncoder.encode("7358")).thenReturn("hashed-7358");

        service.resetPin("08010000001", "7358", "7358");

        assertThat(wallet.getPin()).isEqualTo("hashed-7358");
        assertThat(wallet.getPinChangeAttempts()).isEqualTo(0);
        assertThat(wallet.getLocked()).isFalse();
        verify(walletRepository).save(wallet);
        // Confirms the documented difference from changePin: no old-PIN
        // check is performed anywhere in this flow.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}