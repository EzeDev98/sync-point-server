//package com.sp.syncPoint.feature.wallet.service.impl;
//
//import com.sp.syncPoint.common.exception.ActiveSessionExistsException;
//import com.sp.syncPoint.common.exception.BadRequestException;
//import com.sp.syncPoint.common.exception.UnauthorizedException;
//import com.sp.syncPoint.common.exception.WalletLockedException;
//import com.sp.syncPoint.common.support.EntityUtilityService;
//import com.sp.syncPoint.feature.auth.dto.LoginResponse;
//import com.sp.syncPoint.feature.auth.dto.UserRequest;
//import com.sp.syncPoint.feature.auth.service.AuthService;
//import com.sp.syncPoint.feature.session.service.SessionManagementService;
//import com.sp.syncPoint.feature.user.model.User;
//import com.sp.syncPoint.feature.wallet.model.Wallet;
//import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
//import jakarta.persistence.EntityNotFoundException;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.keycloak.representations.AccessTokenResponse;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//import java.time.LocalDateTime;
//import java.util.Optional;
//
//import static com.sp.syncPoint.common.constants.GeneralConstants.MAX_LOGIN_ATTEMPTS;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.Mockito.doReturn;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.spy;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class WalletAuthServiceImplTest {
//
//    @Mock
//    private WalletRepository walletRepository;
//    @Mock
//    private PasswordEncoder passwordEncoder;
//    @Mock
//    private AuthService authService;
//    @Mock
//    private SessionManagementService sessionManagementService;
//    @Mock
//    private EntityUtilityService entityUtilityService;
//    // Mocked rather than constructed directly: org.keycloak.representations
//    // .AccessTokenResponse is a third-party class whose setter API I can't
//    // verify without the Keycloak jar on the classpath in this sandbox —
//    // mocking only the getters this code actually calls avoids guessing at
//    // a constructor/setter signature that might not match.
//    @Mock
//    private AccessTokenResponse accessTokenResponse;
//
//    // Spy running the real (relocated) login-lock logic while remaining
//    // verifiable, so the lock/expiry/reset assertions below still exercise
//    // the same behaviour they did when this logic lived inside the service.
//    private WalletLockoutService walletLockoutService;
//
//    private WalletAuthServiceImpl service;
//
//    private Wallet wallet;
//    private User user;
//
//    @BeforeEach
//    void setUp() {
//        walletLockoutService = spy(new WalletLockoutService(walletRepository));
//
//        service = new WalletAuthServiceImpl(
//                walletRepository, authService, sessionManagementService,
//                entityUtilityService, walletLockoutService);
//
//        user = new User();
//        user.setId(1L);
//        user.setUsername("jane-doe");
//        user.setUserRefId("USR-1");
//
//        wallet = new Wallet();
//        wallet.setWalletId("WM-1");
//        wallet.setPhoneNumber("08010000001");
//        wallet.setMirrorBalance(java.math.BigDecimal.TEN);
//        wallet.setUser(user);
//    }
//
//    private UserRequest validRequest() {
//        UserRequest request = new UserRequest();
//        request.setPhoneNumber("08010000001");
//        request.setPassword("correct-password");
//        return request;
//    }
//
//    @Test
//    void walletLogin_blankPhoneNumber_throwsBadRequestException() {
//        UserRequest request = validRequest();
//        request.setPhoneNumber(" ");
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(BadRequestException.class);
//    }
//
//    @Test
//    void walletLogin_blankPassword_throwsBadRequestException() {
//        UserRequest request = validRequest();
//        request.setPassword(" ");
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(BadRequestException.class);
//    }
//
//    @Test
//    void walletLogin_walletNotFound_throwsEntityNotFoundException() {
//        UserRequest request = validRequest();
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.empty());
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(EntityNotFoundException.class);
//    }
//
//    @Test
//    void walletLogin_activeLoginLock_throwsWalletLockedException() {
//        UserRequest request = validRequest();
//        wallet.setLoginLockUntil(LocalDateTime.now().plusMinutes(10));
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(WalletLockedException.class);
//        verify(authService, never()).authenticateUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
//    }
//
//    @Test
//    void walletLogin_expiredLoginLock_resetsAttemptsAndProceeds() {
//        UserRequest request = validRequest();
//        wallet.setLoginLockUntil(LocalDateTime.now().minusMinutes(1));
//        wallet.setLoginAttempts(5);
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
//        when(sessionManagementService.hasActiveSession("08010000001")).thenReturn(false);
//        when(accessTokenResponse.getToken()).thenReturn("access-token");
//        when(authService.authenticateUser("jane-doe", "correct-password"))
//                .thenReturn(accessTokenResponse);
//
//        service.walletLogin(request);
//
//        assertThat(wallet.getLoginAttempts()).isEqualTo(0);
//        assertThat(wallet.getLoginLockUntil()).isNull();
//    }
//
//    @Test
//    void walletLogin_wrongPassword_recordsFailedAttemptAndThrowsUnauthorized() {
//        UserRequest request = validRequest();
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(false);
//        doReturn(2).when(walletLockoutService).recordFailedLoginAttempt("08010000001");
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(UnauthorizedException.class);
//    }
//
//    @Test
//    void walletLogin_wrongPasswordMaxAttemptsReached_throwsWalletLockedException() {
//        UserRequest request = validRequest();
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(false);
//        doReturn(MAX_LOGIN_ATTEMPTS).when(walletLockoutService).recordFailedLoginAttempt("08010000001");
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(WalletLockedException.class);
//    }
//
//    @Test
//    void walletLogin_activeSessionElsewhere_throwsActiveSessionExistsException() {
//        UserRequest request = validRequest();
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
//        when(sessionManagementService.hasActiveSession("08010000001")).thenReturn(true);
//
//        assertThatThrownBy(() -> service.walletLogin(request))
//                .isInstanceOf(ActiveSessionExistsException.class);
//        verify(authService, never()).authenticateUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
//    }
//
//    @Test
//    void walletLogin_happyPath_createsSessionAndReturnsLoginResponse() {
//        UserRequest request = validRequest();
//        when(walletRepository.findByPhoneNumber("08010000001")).thenReturn(Optional.of(wallet));
//        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
//        when(sessionManagementService.hasActiveSession("08010000001")).thenReturn(false);
//        when(accessTokenResponse.getToken()).thenReturn("access-token");
//        when(accessTokenResponse.getRefreshToken()).thenReturn("refresh-token");
//        when(accessTokenResponse.getExpiresIn()).thenReturn(3600L);
//        when(accessTokenResponse.getRefreshExpiresIn()).thenReturn(7200L);
//        when(authService.authenticateUser("jane-doe", "correct-password")).thenReturn(accessTokenResponse);
//
//        LoginResponse response = service.walletLogin(request);
//
//        assertThat(response.getToken()).isEqualTo("access-token");
//        assertThat(response.getWalletId()).isEqualTo("WM-1");
//        assertThat(wallet.isStatus()).isTrue();
//        verify(sessionManagementService).createSession(1L, "08010000001", "access-token");
//        verify(walletRepository).save(wallet);
//    }
//
//    @Test
//    void doesAccountExist_delegatesToEntityUtilityService() {
//        when(entityUtilityService.existByPhoneNumber("08010000001")).thenReturn(true);
//
//        assertThat(service.doesAccountExist("08010000001")).isTrue();
//    }
//}