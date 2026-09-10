package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.*;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.common.response.ResponseService;
import com.sp.syncPoint.common.support.EntityUtilityService;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;
import com.sp.syncPoint.feature.wallet.dto.request.PinRequest;
import com.sp.syncPoint.feature.wallet.dto.request.SetPinRequest;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletPinService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.sp.syncPoint.common.constants.GeneralConstants.MAX_PIN_CHANGE_ATTEMPTS;


/**
 * Full lifecycle of a wallet's transaction PIN: initial set, authenticated
 * change, OTP-verified reset, and verification.
 * <p>
 * Extracted from the original {@code WalletServiceImpl} god class as part
 * of the earlier SOLID refactor. In the phase-2 separation-of-concerns
 * pass this class was slimmed to pure orchestration — the cryptographic
 * detail moved to {@link PinHasher}, the format/strength rules to
 * {@link PinValidator}, and the lock-state mechanics to
 * {@link WalletLockoutService}. It decides the flow; the collaborators
 * decide the details. Wallet provisioning lives in
 * {@link WalletProvisioningServiceImpl}, login in
 * {@link WalletAuthServiceImpl}; no behaviour changed during any split.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletPinServiceImpl implements WalletPinService {

    private final WalletRepository walletRepository;
    private final PinHasher pinHasher;
    private final PinValidator pinValidator;
    private final ResponseService responseService;
    private final EntityUtilityService entityUtilityService;
    private final CurrentUserService currentUserService;
    private final WalletLockoutService walletLockoutService;

    @Transactional
    @Override
    public BaseResponse setPin(SetPinRequest request) {
        currentUserService.assertOwnsPhoneNumber(request.getPhoneNumber());

        try {
            pinValidator.setPinValidation(request);

            Wallet wallet = walletRepository.findByPhoneNumber(request.getPhoneNumber())
                    .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + request.getPhoneNumber()));

            if (walletLockoutService.isWalletLocked(wallet)) {
                throw new WalletLockedException("Wallet is locked. Cannot set PIN.");
            }

            if (walletLockoutService.isLockedForPinChange(wallet)) {
                long remainingMinutes = walletLockoutService.getRemainingPinChangeLockTime(wallet);
                throw new PinChangeLockedException("PIN set is temporarily locked. Try again after " + remainingMinutes + " minutes.", remainingMinutes);
            }

            if (pinValidator.isWeak(request.getPin())) {
                throw new InvalidPinException("PIN is too weak or commonly used. Choose a stronger PIN.");
            }

            if (!request.getPin().equals(request.getConfirmNewPin())) {
                int attempts = walletLockoutService.recordFailedPinChangeAttempt(request.getPhoneNumber());
                log.warn("PIN mismatch for user: {}. Attempt: {}", request.getPhoneNumber(), attempts);

                if (attempts >= MAX_PIN_CHANGE_ATTEMPTS) {
                    throw new PinChangeLockedException("Too many failed PIN set attempts. Locked for 15 minutes.", 15);
                }

                throw new BadRequestException("PINs do not match");
            }

            if (wallet.getPin() != null) {
                throw new BadRequestException("PIN already set. Use change PIN instead.");
            }

            String hashedPin = pinHasher.hash(request.getPin());
            wallet.setPin(hashedPin);
            wallet.setPinAttempts(0);
            wallet.setLocked(false);
            wallet.setLockedUntil(null);
            wallet.setUpdatedAt(LocalDateTime.now());

            walletRepository.save(wallet);
            return new BaseResponse(HttpServletResponse.SC_CREATED, "PIN set successfully", request.getPhoneNumber());

        } catch (NotFoundException ex) {
            log.error("Wallet not found for user: {}", request.getPhoneNumber(), ex);
            throw ex;

        } catch (UserNotFoundException ex) {
            log.error("User not found: {}", request.getPhoneNumber(), ex);
            throw new EntityNotFoundException("User not found");

        } catch (InvalidPasswordException ex) {
            log.error("Invalid password for user: {}", request.getPhoneNumber(), ex);
            throw new UnauthorizedException("Invalid password");

        } catch (WalletNotFoundException ex) {
            log.error("Wallet not found for user: {}", request.getPhoneNumber(), ex);
            throw new EntityNotFoundException("Wallet not found");

        } catch (WalletLockedException | PinChangeLockedException ex) {
            log.warn("Wallet/PIN change locked for user: {}", request.getPhoneNumber(), ex);
            throw ex;

        } catch (InvalidPinException ex) {
            log.error("Invalid PIN for user: {}", request.getPhoneNumber(), ex);
            throw new BadRequestException(ex.getMessage());

        } catch (BadRequestException ex) {
            log.warn("Bad request for user: {}: {}", request.getPhoneNumber(), ex.getMessage());
            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected error setting PIN for user: {}", request.getPhoneNumber(), ex);
            throw new InternalServerErrorException("Failed to set PIN. Please try again later.", ex);
        }
    }

    @Transactional
    @Override
    public BaseResponse changePin(PinRequest pinRequest) {
        BaseResponse response = new BaseResponse();

        if (pinRequest == null) {
            return responseService.createErrorResponse(response, "PIN request is required", HttpServletResponse.SC_BAD_REQUEST);
        }

        String phoneNumber = pinRequest.getPhoneNumber();
        currentUserService.assertOwnsPhoneNumber(phoneNumber);

        try {
            if (!pinValidator.validateRequiredFields(response, phoneNumber, pinRequest.getPin(), pinRequest.getOldPin())) {
                return response;
            }

            if (pinValidator.isNotValidFormat(pinRequest.getPin())) {
                return responseService.createErrorResponse(response, "New PIN must be exactly 4 digits", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (pinValidator.isNotValidFormat(pinRequest.getOldPin())) {
                return responseService.createErrorResponse(response, "Old PIN must be exactly 4 digits", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (pinValidator.isWeak(pinRequest.getPin())) {
                return responseService.createErrorResponse(response, "PIN is too weak or commonly used. Choose a stronger PIN.", HttpServletResponse.SC_BAD_REQUEST);
            }

            Wallet wallet = walletRepository.findByPhoneNumberWithLock(phoneNumber)
                    .orElseThrow(() -> new NotFoundException("No wallet found for the provided phone number " + phoneNumber));

            if (walletLockoutService.isWalletLocked(wallet)) {
                return responseService.createErrorResponse(response, "Wallet is locked. Please try again later.", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (walletLockoutService.isLockedForPinChange(wallet)) {
                long remainingMinutes = walletLockoutService.getRemainingPinChangeLockTime(wallet);
                return responseService.createErrorResponse(response, "Too many PIN change attempts. Try again in " + remainingMinutes + " minutes.", HttpServletResponse.SC_BAD_REQUEST);
            }

            pinHasher.assertMatches(wallet.getPin(), pinRequest.getOldPin());

            if (pinHasher.matches(pinRequest.getPin(), wallet.getPin())) {
                return responseService.createErrorResponse(response, "New PIN cannot be the same as old PIN", HttpServletResponse.SC_BAD_REQUEST);
            }

            wallet.setPin(pinHasher.hash(pinRequest.getPin()));
            wallet.setPinLastChanged(LocalDateTime.now());
            wallet.setPinAttempts(0);
            wallet.setLocked(false);
            wallet.setLockedUntil(null);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletLockoutService.resetPinChangeAttempts(wallet);
            walletRepository.save(wallet);

            return responseService.createSuccessResponse(response, "PIN changed successfully");

        } catch (NotFoundException ex) {
            log.error("Wallet not found for phone: {}", phoneNumber, ex);
            return responseService.createErrorResponse(response, "Wallet not found", HttpServletResponse.SC_NOT_FOUND);

        } catch (IncorrectPinException ex) {
            log.error("Incorrect old PIN for phone: {}", phoneNumber, ex);
            walletRepository.findByPhoneNumber(phoneNumber).ifPresent(wallet -> {
                walletLockoutService.incrementPinChangeAttempt(wallet);
                walletRepository.save(wallet);
            });
            return responseService.createErrorResponse(response, "Incorrect old PIN", HttpServletResponse.SC_BAD_REQUEST);

        } catch (InvalidPasswordException ex) {
            log.error("Invalid password for phone: {}", phoneNumber, ex);
            walletRepository.findByPhoneNumber(phoneNumber).ifPresent(wallet -> {
                walletLockoutService.incrementPinChangeAttempt(wallet);
                walletRepository.save(wallet);
            });
            return responseService.createErrorResponse(response, "Invalid password", HttpServletResponse.SC_BAD_REQUEST);

        } catch (Exception ex) {
            log.error("Unexpected error during PIN change for phone: {}", phoneNumber, ex);
            return responseService.createErrorResponse(response, "Failed to change PIN due to an unexpected error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean isWalletPinSet(String walletId) {
        Wallet wallet = entityUtilityService.fetchWallet(walletId);

        if (wallet == null) {
            throw new TransactionPinNotSetException("Wallet not found with id: " + walletId);
        }

        currentUserService.assertOwnsPhoneNumber(wallet.getPhoneNumber());

        return wallet.getPin() != null;
    }

    @Override
    public boolean verifyPin(String walletId, String unverifiedPin) {
        Wallet wallet = entityUtilityService.fetchWallet(walletId);
        if (wallet == null || wallet.getPin() == null) {
            return false;
        }

        currentUserService.assertOwnsPhoneNumber(wallet.getPhoneNumber());

        return pinHasher.matches(unverifiedPin, wallet.getPin());
    }

    /**
     * Overwrites a wallet's PIN as part of the OTP-verified PIN-reset flow.
     * Skips the "PIN already set" guard (the whole point here is
     * overwriting a forgotten one) and requires no old PIN, since
     * PinResetServiceImpl has already proven ownership via OTP.
     */
    @Transactional
    @Override
    public void resetPin(String phoneNumber, String newPin, String confirmNewPin) {
        currentUserService.assertOwnsPhoneNumber(phoneNumber);

        Wallet wallet = walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + phoneNumber));

        if (pinValidator.isNotValidFormat(newPin)) {
            throw new InvalidPinException("PIN must be exactly 4 digits");
        }

        if (pinValidator.isWeak(newPin)) {
            throw new InvalidPinException("PIN is too weak or commonly used. Choose a stronger PIN.");
        }

        if (!newPin.equals(confirmNewPin)) {
            throw new BadRequestException("PINs do not match");
        }

        wallet.setPin(pinHasher.hash(newPin));
        wallet.setPinAttempts(0);
        wallet.setPinChangeAttempts(0);
        wallet.setLocked(false);
        wallet.setLockedUntil(null);
        wallet.setPinLastChanged(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        walletRepository.save(wallet);
        log.info("PIN reset successfully for wallet belonging to user: {}", phoneNumber);
    }
}