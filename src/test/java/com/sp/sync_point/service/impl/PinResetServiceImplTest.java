package com.sp.sync_point.service.impl;

import com.sp.sync_point.config.PinResetProperties;
import com.sp.sync_point.dto.CompletePinResetRequest;
import com.sp.sync_point.dto.PinResetRequestDto;
import com.sp.sync_point.dto.VerifyPinOtpRequest;
import com.sp.sync_point.exception.OtpInvalidException;
import com.sp.sync_point.exception.ResetTokenInvalidException;
import com.sp.sync_point.model.PinResetOtp;
import com.sp.sync_point.model.PinResetToken;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.PinResetOtpRepository;
import com.sp.sync_point.repository.PinResetTokenRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.PinResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.OtpDeliveryService;
import com.sp.sync_point.service.WalletPinService;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PinResetServiceImplTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PinResetOtpRepository pinResetOtpRepository;
    @Mock
    private PinResetTokenRepository pinResetTokenRepository;
    @Mock
    private OtpService otpService;
    @Mock
    private OtpDeliveryService otpDeliveryService;
    @Mock
    private WalletPinService walletService;
    @Mock
    private ResetValidator resetValidator;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> cooldownBucket;

    // Real engine backed by the mocked OtpService, same rationale as
    // PasswordResetServiceImplTest — this exercises the actual shared logic.
    private OtpResetEngine otpResetEngine;

    private PinResetServiceImpl service;

    private final PinResetProperties properties = new PinResetProperties();

    @BeforeEach
    void setUp() {
        otpResetEngine = new OtpResetEngine(otpService);
        service = new PinResetServiceImpl(
                walletRepository, pinResetOtpRepository, pinResetTokenRepository,
                otpService, otpDeliveryService, walletService, properties,
                resetValidator, redissonClient, otpResetEngine);
    }

    private Wallet wallet() {
        Wallet wallet = new Wallet();
        wallet.setId(7L);
        wallet.setWalletId("WM-1");
        wallet.setPhoneNumber("08010000001");
        return wallet;
    }

    // ── requestOtp ─────────────────────────────────────────────────

    @Test
    void requestOtp_walletNotFound_throwsBeforeCheckingCooldown() {
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());

        PinResetRequestDto request = new PinResetRequestDto("08010000001");

        assertThatThrownBy(() -> service.requestOtp(request))
                .isInstanceOf(NotFoundException.class);
        // Unlike PasswordResetServiceImpl, wallet existence is checked
        // BEFORE the cooldown bucket — PIN reset has no user-enumeration
        // concern (the caller already knows the phone number is registered),
        // so this ordering difference from password reset is intentional
        // and preserved, not a bug.
        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    void requestOtp_activeCooldown_throwsAfterWalletFound() {
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet()));
        when(redissonClient.<String>getBucket(anyString())).thenReturn(cooldownBucket);
        when(cooldownBucket.isExists()).thenReturn(true);
        when(cooldownBucket.remainTimeToLive()).thenReturn(30_000L);

        PinResetRequestDto request = new PinResetRequestDto("08010000001");

        assertThatThrownBy(() -> service.requestOtp(request))
                .isInstanceOf(com.sp.sync_point.exception.OtpResendCooldownException.class);
        verify(pinResetOtpRepository, never()).save(any());
    }

    @Test
    void requestOtp_happyPath_generatesAndSendsOtpBoundToWalletId() {
        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet()));
        when(redissonClient.<String>getBucket(anyString())).thenReturn(cooldownBucket);
        when(cooldownBucket.isExists()).thenReturn(false);
        when(pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(otpService.generateOtp(6)).thenReturn("654321");
        when(otpService.hashOtp("654321")).thenReturn("hashed-654321");

        PinResetRequestDto request = new PinResetRequestDto("08010000001");
        PinResetResponse response = service.requestOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(otpDeliveryService).sendPasswordResetOtp("08010000001", "654321");
        ArgumentCaptor<PinResetOtp> captor = ArgumentCaptor.forClass(PinResetOtp.class);
        verify(pinResetOtpRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletId()).isEqualTo(7L);
        verify(cooldownBucket).set(anyString(), any());
    }

    // ── verifyOtp ──────────────────────────────────────────────────

    @Test
    void verifyOtp_noActiveOtp_throwsOtpInvalidException() {
        when(pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        VerifyPinOtpRequest request = new VerifyPinOtpRequest("08010000001", "654321");

        assertThatThrownBy(() -> service.verifyOtp(request))
                .isInstanceOf(OtpInvalidException.class);
    }

    @Test
    void verifyOtp_correctCode_issuesResetTokenBoundToOtpsWalletId() {
        PinResetOtp otp = new PinResetOtp();
        otp.setWalletId(7L);
        otp.setOtpHash("hashed-654321");
        otp.setAttemptCount(0);
        otp.setMaxAttempts(5);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(pinResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(otp));
        when(otpService.matchesOtp("654321", "hashed-654321")).thenReturn(true);
        when(otpService.generateResetToken()).thenReturn("raw-token");
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");

        VerifyPinOtpRequest request = new VerifyPinOtpRequest("08010000001", "654321");
        VerifyOtpResponse response = service.verifyOtp(request);

        assertThat(response.getResetToken()).isEqualTo("raw-token");
        ArgumentCaptor<PinResetToken> captor = ArgumentCaptor.forClass(PinResetToken.class);
        verify(pinResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletId()).isEqualTo(7L);
    }

    // ── completeReset ──────────────────────────────────────────────

    @Test
    void completeReset_blankResetToken_throwsResetTokenInvalidException() {
        CompletePinResetRequest request = new CompletePinResetRequest(" ", "7358", "7358");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(ResetTokenInvalidException.class);
        verify(pinResetTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void completeReset_usedToken_throwsResetTokenInvalidExceptionBeforeTouchingWallet() {
        PinResetToken token = new PinResetToken();
        token.setUsed(true);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(pinResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        CompletePinResetRequest request = new CompletePinResetRequest("raw-token", "7358", "7358");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(ResetTokenInvalidException.class);
        verify(walletRepository, never()).findById(any());
    }

    @Test
    void completeReset_walletNotFound_throwsNotFoundException() {
        PinResetToken token = new PinResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setWalletId(7L);
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(pinResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(walletRepository.findById(7L)).thenReturn(Optional.empty());

        CompletePinResetRequest request = new CompletePinResetRequest("raw-token", "7358", "7358");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void completeReset_happyPath_delegatesToWalletPinServiceAndMarksTokenUsed() {
        PinResetToken token = new PinResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setWalletId(7L);
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(pinResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(walletRepository.findById(7L)).thenReturn(Optional.of(wallet()));

        CompletePinResetRequest request = new CompletePinResetRequest("raw-token", "7358", "7358");
        PinResetResponse response = service.completeReset(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        // The actual PIN-format/strength/mismatch validation is owned by
        // WalletPinService.resetPin (see WalletPinServiceImplTest) — this
        // class's only job is to authorize the call via the reset token
        // and delegate.
        verify(walletService).resetPin("08010000001", "7358", "7358");
        assertThat(token.isUsed()).isTrue();
        verify(pinResetTokenRepository).save(token);
    }
}