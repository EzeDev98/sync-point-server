package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.feature.passwordreset.enums.PasswordResetStatus;
import com.sp.syncPoint.common.exception.MaxAttemptsExceededException;
import com.sp.syncPoint.common.exception.OtpExpiredException;
import com.sp.syncPoint.common.exception.OtpInvalidException;
import com.sp.syncPoint.common.exception.OtpResendCooldownException;
import com.sp.syncPoint.common.exception.ResetTokenInvalidException;
import com.sp.syncPoint.feature.pinreset.model.PinResetOtp;
import com.sp.syncPoint.feature.pinreset.model.PinResetToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpResetEngineTest {

    @Mock
    private OtpService otpService;
    @Mock
    private RBucket<String> cooldownBucket;

    private OtpResetEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OtpResetEngine(otpService);
    }

    private PinResetOtp otp(int attemptCount, int maxAttempts, LocalDateTime expiresAt) {
        PinResetOtp otp = new PinResetOtp();
        otp.setOtpHash("hashed-otp");
        otp.setAttemptCount(attemptCount);
        otp.setMaxAttempts(maxAttempts);
        otp.setExpiresAt(expiresAt);
        otp.setStatus(PasswordResetStatus.ACTIVE);
        return otp;
    }

    @Test
    void rejectIfCoolingDown_bucketExists_throwsWithRemainingSeconds() {
        when(cooldownBucket.isExists()).thenReturn(true);
        when(cooldownBucket.remainTimeToLive()).thenReturn(45_000L);

        assertThatThrownBy(() -> engine.rejectIfCoolingDown(cooldownBucket))
                .isInstanceOf(OtpResendCooldownException.class)
                .satisfies(ex -> assertThat(((OtpResendCooldownException) ex).getRemainingSeconds()).isEqualTo(45));
    }

    @Test
    void rejectIfCoolingDown_remainingSecondsFlooredToOne() {
        when(cooldownBucket.isExists()).thenReturn(true);
        when(cooldownBucket.remainTimeToLive()).thenReturn(200L); // < 1000ms → 0s raw

        assertThatThrownBy(() -> engine.rejectIfCoolingDown(cooldownBucket))
                .isInstanceOf(OtpResendCooldownException.class)
                .satisfies(ex -> assertThat(((OtpResendCooldownException) ex).getRemainingSeconds()).isEqualTo(1));
    }

    @Test
    void rejectIfCoolingDown_noActiveBucket_doesNothing() {
        when(cooldownBucket.isExists()).thenReturn(false);

        engine.rejectIfCoolingDown(cooldownBucket);
    }

    @Test
    void invalidateExisting_noPriorOtp_doesNotInvokeSave() {
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);

        engine.invalidateExisting(Optional.<PinResetOtp>empty(), save);

        verify(save, never()).accept(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidateExisting_priorOtp_marksInvalidatedAndSaves() {
        PinResetOtp existing = otp(0, 5, LocalDateTime.now().plusMinutes(5));
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);

        engine.invalidateExisting(Optional.of(existing), save);

        assertThat(existing.getStatus()).isEqualTo(PasswordResetStatus.INVALIDATED);
        assertThat(existing.getInvalidatedAt()).isNotNull();
        verify(save).accept(existing);
    }

    @Test
    void populateNewOtp_setsAllSharedFields() {
        PinResetOtp otp = new PinResetOtp();
        when(otpService.hashOtp("123456")).thenReturn("hashed-123456");

        engine.populateNewOtp(otp, "08010000001", "123456", 10, 3);

        assertThat(otp.getPhoneNumber()).isEqualTo("08010000001");
        assertThat(otp.getOtpHash()).isEqualTo("hashed-123456");
        assertThat(otp.getAttemptCount()).isEqualTo(0);
        assertThat(otp.getMaxAttempts()).isEqualTo(3);
        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.ACTIVE);
        assertThat(otp.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(otp.getLastSentAt()).isNotNull();
    }

    @Test
    void verify_expiredOtp_marksExpiredSavesAndThrows() {
        PinResetOtp otp = otp(0, 3, LocalDateTime.now().minusMinutes(1));
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);

        assertThatThrownBy(() -> engine.verify(otp, "123456", save))
                .isInstanceOf(OtpExpiredException.class);

        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.EXPIRED);
        verify(save).accept(otp);
    }

    @Test
    void verify_attemptsAlreadyExhausted_marksInvalidatedSavesAndThrows() {
        PinResetOtp otp = otp(3, 3, LocalDateTime.now().plusMinutes(5));
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);

        assertThatThrownBy(() -> engine.verify(otp, "123456", save))
                .isInstanceOf(MaxAttemptsExceededException.class);

        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.INVALIDATED);
        verify(save).accept(otp);
    }

    @Test
    void verify_wrongCode_incrementsAttemptsSavesAndThrowsInvalid() {
        PinResetOtp otp = otp(0, 3, LocalDateTime.now().plusMinutes(5));
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);
        when(otpService.matchesOtp("000000", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> engine.verify(otp, "000000", save))
                .isInstanceOf(OtpInvalidException.class);

        assertThat(otp.getAttemptCount()).isEqualTo(1);
        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.ACTIVE); // not yet exhausted
        verify(save).accept(otp);
    }

    @Test
    void verify_wrongCodeExhaustsAttempts_invalidatesAndThrowsMaxAttempts() {
        PinResetOtp otp = otp(2, 3, LocalDateTime.now().plusMinutes(5)); // one more failure exhausts
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);
        when(otpService.matchesOtp("000000", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> engine.verify(otp, "000000", save))
                .isInstanceOf(MaxAttemptsExceededException.class);

        assertThat(otp.getAttemptCount()).isEqualTo(3);
        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.INVALIDATED);
        verify(save).accept(otp);
    }

    @Test
    void verify_correctCode_marksVerifiedAndSaves() {
        PinResetOtp otp = otp(0, 3, LocalDateTime.now().plusMinutes(5));
        @SuppressWarnings("unchecked")
        Consumer<PinResetOtp> save = mock(Consumer.class);
        when(otpService.matchesOtp("123456", "hashed-otp")).thenReturn(true);

        engine.verify(otp, "123456", save);

        assertThat(otp.getStatus()).isEqualTo(PasswordResetStatus.VERIFIED);
        assertThat(otp.getVerifiedAt()).isNotNull();
        verify(save).accept(otp);
    }

    @Test
    void assertUsable_usedToken_throwsWithGivenMessage() {
        PinResetToken token = new PinResetToken();
        token.setUsed(true);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> engine.assertUsable(token, "custom expired message"))
                .isInstanceOf(ResetTokenInvalidException.class)
                .hasMessage("custom expired message");
    }

    @Test
    void assertUsable_expiredToken_throws() {
        PinResetToken token = new PinResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> engine.assertUsable(token, "custom expired message"))
                .isInstanceOf(ResetTokenInvalidException.class);
    }

    @Test
    void assertUsable_validToken_doesNotThrow() {
        PinResetToken token = new PinResetToken();
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        engine.assertUsable(token, "custom expired message");
    }

    @Test
    void markUsed_setsUsedFlagTimestampAndSaves() {
        PinResetToken token = new PinResetToken();
        @SuppressWarnings("unchecked")
        Consumer<PinResetToken> save = mock(Consumer.class);

        engine.markUsed(token, save);

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(save).accept(token);
    }
}