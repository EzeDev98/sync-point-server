package com.sp.syncPoint.feature.wallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sp.syncPoint.client.dto.request.LoginRequest;
import com.sp.syncPoint.client.dto.response.InternalAuthApiResponse;
import com.sp.syncPoint.client.dto.response.InternalAuthLoginResponse;
import com.sp.syncPoint.client.service.InternalAuthClient;
import com.sp.syncPoint.common.exception.AccountNotVerifiedException;
import com.sp.syncPoint.common.exception.ActiveSessionExistsException;
import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.exception.UnauthorizedException;
import com.sp.syncPoint.common.exception.WalletLockedException;
import com.sp.syncPoint.common.support.EntityUtilityService;
import com.sp.syncPoint.feature.auth.dto.response.LoginResponse;
import com.sp.syncPoint.feature.auth.dto.request.UserRequest;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import static com.sp.syncPoint.common.constants.GeneralConstants.MAX_LOGIN_ATTEMPTS;
import static com.sp.syncPoint.common.constants.GeneralConstants.WALLET_LOCKED_DURATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletAuthServiceImpl implements WalletAuthService {

    private final WalletRepository walletRepository;
    private final SessionManagementService sessionManagementService;
    private final EntityUtilityService entityUtilityService;
    private final WalletLockoutService walletLockoutService;
    private final InternalAuthClient authClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public LoginResponse walletLogin(UserRequest request) {

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            throw new BadRequestException("Phone number is required");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BadRequestException("Password is required");
        }

        Wallet wallet = walletRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> {
                    log.warn("Login attempt for unregistered phone: {}", request.getPhoneNumber());
                    return new UnauthorizedException("Invalid phone number or password");
                });

        if (walletLockoutService.isLoginLocked(wallet)) {
            long remainingMinutes = walletLockoutService.getRemainingLoginLockTime(wallet);
            log.warn("Wallet login locked for phone: {}. Remaining lock time: {} minutes", request.getPhoneNumber(), remainingMinutes);
            throw new WalletLockedException("Wallet is temporarily locked. Try again in " + remainingMinutes + " minutes", remainingMinutes);
        }

        User user = wallet.getUser();
        if (user == null) {
            log.error("No user associated with wallet for phone: {}", request.getPhoneNumber());
            throw new UnauthorizedException("Invalid phone number or password");
        }

        boolean activeSessionElsewhere = sessionManagementService.hasActiveSession(request.getPhoneNumber());

        InternalAuthApiResponse<InternalAuthLoginResponse> authApiResponse;

        try {
           authApiResponse = authClient.login(new LoginRequest(user.getUsername(), request.getPassword()));
        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode().value() == 403) {
                log.warn("Login blocked for phone: {} — account not verified", request.getPhoneNumber());
                throw new AccountNotVerifiedException(extractAuthErrorMessage(ex));
            }

            int attempts = walletLockoutService.recordFailedLoginAttempt(request.getPhoneNumber());

            log.warn("Invalid login attempt for phone: {}. Attempt {} of {}", request.getPhoneNumber(), attempts, MAX_LOGIN_ATTEMPTS);
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                throw new WalletLockedException("Wallet locked due to too many failed attempts. Try again later.", WALLET_LOCKED_DURATION);
            }

            throw new UnauthorizedException("Invalid phone number or password");
        }

        InternalAuthLoginResponse loginResponse = authApiResponse.getData();
        walletLockoutService.resetLoginAttempts(wallet);

        if (activeSessionElsewhere) {
            try {
                authClient.logout(loginResponse.getAccessToken());
            } catch (RuntimeException ex) {
                log.warn("Could not revoke the token issued for a refused login for phone: {}: {}", request.getPhoneNumber(), ex.getMessage());
            }
            log.warn("Login blocked for phone: {} — active session exists on another device", request.getPhoneNumber());
            throw new ActiveSessionExistsException("This account is currently active on another device. Please unlock and log out on that device before signing in here.");
        }

        sessionManagementService.createSession(user.getId(), request.getPhoneNumber(), loginResponse.getAccessToken());
        wallet.setStatus(true);
        walletRepository.save(wallet);

        log.info("Wallet login successful for user ID: {}", user.getUserRefId());
        return new LoginResponse(loginResponse.getAccessToken(), loginResponse.getRefreshToken(), loginResponse.getExpiresIn(), loginResponse.getRefreshExpiresIn(), request.getPhoneNumber(), user.getUsername(), user.getEmail(), user.getUserRefId(), wallet.getWalletId(), wallet.getMirrorBalance(), user.getId(), user.getFirstName(), user.getMiddleName(), user.getLastName(), wallet.getOfflineLimit(), wallet.isStatus());
    }

    @Override
    public boolean doesAccountExist(String phoneNumber) {
        return entityUtilityService.existByPhoneNumber(phoneNumber);
    }

    private String extractAuthErrorMessage(RestClientResponseException ex) {
        try {
            InternalAuthApiResponse<?> body = objectMapper.readValue(
                    ex.getResponseBodyAsString(), InternalAuthApiResponse.class);
            if (body.getMessage() != null) {
                return body.getMessage();
            }
        } catch (Exception parseFailure) {
            // internal-auth's response wasn't parseable — fall back below.
        }
        return "Your account is not verified yet. Please complete verification to continue.";
    }
}