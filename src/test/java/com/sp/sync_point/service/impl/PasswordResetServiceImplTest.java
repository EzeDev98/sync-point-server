package com.sp.sync_point.service.impl;

import com.sp.sync_point.config.PasswordResetProperties;
import com.sp.sync_point.dto.CompletePasswordResetRequest;
import com.sp.sync_point.dto.PasswordResetRequestDto;
import com.sp.sync_point.dto.VerifyOtpRequest;
import com.sp.sync_point.exception.OtpInvalidException;
import com.sp.sync_point.exception.ResetTokenInvalidException;
import com.sp.sync_point.exception.UserNotFoundException;
import com.sp.sync_point.model.PasswordResetOtp;
import com.sp.sync_point.model.PasswordResetToken;
import com.sp.sync_point.model.User;
import com.sp.sync_point.repository.PasswordResetOtpRepository;
import com.sp.sync_point.repository.PasswordResetTokenRepository;
import com.sp.sync_point.repository.UserRepository;
import com.sp.sync_point.response.PasswordResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.OtpDeliveryService;
import com.sp.sync_point.service.SessionManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private OtpService otpService;
    @Mock
    private OtpDeliveryService otpDeliveryService;
    @Mock
    private KeycloakService keycloakService;
    @Mock
    private SessionManagementService sessionManagementService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ValidateService validateService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> cooldownBucket;

    // OtpResetEngine has a single real dependency (OtpService), so we wire a
    // real engine backed by the mocked OtpService rather than mocking the
    // engine itself — this exercises the actual extracted logic, which is
    // the point of this refactor.
    private OtpResetEngine otpResetEngine;

    private PasswordResetServiceImpl service;

    private final PasswordResetProperties properties = new PasswordResetProperties();

    @BeforeEach
    void setUp() {
        otpResetEngine = new OtpResetEngine(otpService);
        service = new PasswordResetServiceImpl(
                userRepository, passwordResetOtpRepository, passwordResetTokenRepository,
                otpService, otpDeliveryService, keycloakService, sessionManagementService,
                passwordEncoder, properties, validateService, redissonClient, otpResetEngine);

        when(redissonClient.getBucket(anyString())).thenReturn(cooldownBucket);
    }

    // ── requestOtp ─────────────────────────────────────────────────

    @Test
    void requestOtp_activeCooldown_throwsWithoutTouchingUserRepository() {
        when(cooldownBucket.isExists()).thenReturn(true);
        when(cooldownBucket.remainTimeToLive()).thenReturn(30_000L);

        PasswordResetRequestDto request = new PasswordResetRequestDto("08010000001");

        assertThatThrownBy(() -> service.requestOtp(request))
                .isInstanceOf(com.sp.sync_point.exception.OtpResendCooldownException.class);
        verify(userRepository, never()).findByPhoneNumber(anyString());
    }

    @Test
    void requestOtp_unregisteredPhoneNumber_returnsGenericResponseWithoutSendingOtp() {
        when(cooldownBucket.isExists()).thenReturn(false);
        when(userRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());

        PasswordResetRequestDto request = new PasswordResetRequestDto("08010000001");
        PasswordResetResponse response = service.requestOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(otpDeliveryService, never()).sendPasswordResetOtp(anyString(), anyString());
        // Cooldown still starts even for an unregistered number — this is
        // the user-enumeration protection: behavior must be indistinguishable.
        verify(cooldownBucket).set(anyString(), any());
    }

    @Test
    void requestOtp_registeredPhoneNumber_generatesAndSendsOtp() {
        when(cooldownBucket.isExists()).thenReturn(false);
        User user = new User();
        user.setId(1L);
        when(userRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(otpService.generateOtp(6)).thenReturn("123456");
        when(otpService.hashOtp("123456")).thenReturn("hashed-123456");

        PasswordResetRequestDto request = new PasswordResetRequestDto("08010000001");
        PasswordResetResponse response = service.requestOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(otpDeliveryService).sendPasswordResetOtp("08010000001", "123456");
        verify(passwordResetOtpRepository).save(any(PasswordResetOtp.class));
        verify(cooldownBucket).set(anyString(), any());
    }

    // ── verifyOtp ──────────────────────────────────────────────────

    @Test
    void verifyOtp_noActiveOtp_throwsOtpInvalidException() {
        when(passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        VerifyOtpRequest request = new VerifyOtpRequest("08010000001", "123456");

        assertThatThrownBy(() -> service.verifyOtp(request))
                .isInstanceOf(OtpInvalidException.class);
    }

    @Test
    void verifyOtp_correctCode_issuesResetTokenBoundToOtpsUserId() {
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setUserId(42L);
        otp.setOtpHash("hashed-123456");
        otp.setAttemptCount(0);
        otp.setMaxAttempts(5);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(passwordResetOtpRepository.findTopByPhoneNumberAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(otp));
        when(otpService.matchesOtp("123456", "hashed-123456")).thenReturn(true);
        when(otpService.generateResetToken()).thenReturn("raw-token");
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");

        VerifyOtpRequest request = new VerifyOtpRequest("08010000001", "123456");
        VerifyOtpResponse response = service.verifyOtp(request);

        assertThat(response.getResetToken()).isEqualTo("raw-token");
        org.mockito.ArgumentCaptor<PasswordResetToken> captor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hashed-token");
    }

    // ── completeReset ──────────────────────────────────────────────

    @Test
    void completeReset_blankResetToken_throwsResetTokenInvalidException() {
        CompletePasswordResetRequest request = new CompletePasswordResetRequest(" ", "NewPass123!", "NewPass123!");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(ResetTokenInvalidException.class);
        verify(passwordResetTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void completeReset_tokenNotFound_throwsResetTokenInvalidException() {
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.empty());

        CompletePasswordResetRequest request = new CompletePasswordResetRequest("raw-token", "NewPass123!", "NewPass123!");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(ResetTokenInvalidException.class);
    }

    @Test
    void completeReset_usedToken_throwsResetTokenInvalidException() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUsed(true);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));

        CompletePasswordResetRequest request = new CompletePasswordResetRequest("raw-token", "NewPass123!", "NewPass123!");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(ResetTokenInvalidException.class);
        verify(keycloakService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void completeReset_userNotFound_throwsUserNotFoundException() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setUserId(99L);
        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        CompletePasswordResetRequest request = new CompletePasswordResetRequest("raw-token", "NewPass123!", "NewPass123!");

        assertThatThrownBy(() -> service.completeReset(request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void completeReset_happyPath_updatesKeycloakLocalPasswordAndInvalidatesSessions() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setUserId(99L);

        User user = new User();
        user.setId(99L);
        user.setKeycloakId("kc-99");
        user.setPhoneNumber("08010000001");

        when(otpService.hashResetToken("raw-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-password");

        CompletePasswordResetRequest request = new CompletePasswordResetRequest("raw-token", "NewPass123!", "NewPass123!");
        PasswordResetResponse response = service.completeReset(request);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(keycloakService).resetPassword("kc-99", "NewPass123!");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(user);
        assertThat(token.isUsed()).isTrue();
        verify(passwordResetTokenRepository).save(token);
        verify(sessionManagementService).invalidateUserSessionByAdmin("08010000001");
    }
}