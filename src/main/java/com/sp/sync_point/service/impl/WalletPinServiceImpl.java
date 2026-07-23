package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.exception.BadRequestException;
import com.sp.sync_point.exception.IncorrectPinException;
import com.sp.sync_point.exception.InternalServerErrorException;
import com.sp.sync_point.exception.InvalidPasswordException;
import com.sp.sync_point.exception.InvalidPinException;
import com.sp.sync_point.exception.PinChangeLockedException;
import com.sp.sync_point.exception.TransactionPinNotSetException;
import com.sp.sync_point.exception.UnauthorizedException;
import com.sp.sync_point.exception.UserNotFoundException;
import com.sp.sync_point.exception.WalletLockedException;
import com.sp.sync_point.exception.WalletNotFoundException;
import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.service.EntityUtilityService;
import com.sp.sync_point.service.ResponseService;
import com.sp.sync_point.service.WalletPinService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static com.sp.sync_point.constants.GeneralConstants.MAX_PIN_CHANGE_ATTEMPTS;
import static com.sp.sync_point.constants.GeneralConstants.PIN_CHANGE_LOCK_MINUTES;

/**
 * Full lifecycle of a wallet's transaction PIN: initial set, authenticated
 * change, OTP-verified reset, and verification.
 * <p>
 * Extracted from the original {@code WalletServiceImpl} god class as part
 * of the SOLID refactor — this class owns exactly one responsibility: PIN
 * state and its associated lockout. Wallet provisioning lives in
 * {@link WalletProvisioningServiceImpl}, login in
 * {@link WalletAuthServiceImpl}; no logic in any of the three changed
 * during the split.
 * <p>
 * Note this class owns the PIN-CHANGE lockout counters
 * ({@code pinChangeAttempts}/{@code lastPinChangeAttempt}) and the
 * wallet-lock flag ({@code locked}/{@code lockedUntil}). LOGIN lockout
 * ({@code loginAttempts}) is a separate counter owned by
 * {@link WalletAuthServiceImpl} — the two were already tracked as distinct
 * fields on {@code Wallet} in the original code, and remain so here.
 */
@Service
@RequiredArgsConstructor
public class WalletPinServiceImpl implements WalletPinService {

    private static final Logger log = LoggerFactory.getLogger(WalletPinServiceImpl.class);

    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidateService validateService;
    private final ResponseService responseService;
    private final EntityUtilityService entityUtilityService;
    private final CurrentUserService currentUserService;
    private final WalletLockoutService walletLockoutService;

    @Transactional
    @Override
    public BaseResponse setPin(SetPinRequest request) {
        currentUserService.assertOwnsPhoneNumber(request.getPhoneNumber());

        try {
            validateService.setPinValidation(request);

            Wallet wallet = walletRepository.findByPhoneNumber(request.getPhoneNumber())
                    .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + request.getPhoneNumber()));

            if (isWalletLocked(wallet)) {
                throw new WalletLockedException("Wallet is locked. Cannot set PIN.");
            }

            if (isLockedForPinChange(wallet)) {
                long remainingMinutes = getRemainingPinChangeLockTime(wallet);
                throw new PinChangeLockedException("PIN set is temporarily locked. Try again after " + remainingMinutes + " minutes.", remainingMinutes);
            }

            if (isNotValidPinFormat(request.getPin())) {
                throw new InvalidPinException("PIN must be exactly 4 digits");
            }

            if (isWeakPin(request.getPin())) {
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

            String hashedPin = hashPin(request.getPin());
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
            if (!validateService.validateRequiredFields(response, phoneNumber, pinRequest.getPin(), pinRequest.getOldPin())) {
                return response;
            }

            if (isNotValidPinFormat(pinRequest.getPin())) {
                return responseService.createErrorResponse(response, "New PIN must be exactly 4 digits", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (isNotValidPinFormat(pinRequest.getOldPin())) {
                return responseService.createErrorResponse(response, "Old PIN must be exactly 4 digits", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (isWeakPin(pinRequest.getPin())) {
                return responseService.createErrorResponse(response, "PIN is too weak or commonly used. Choose a stronger PIN.", HttpServletResponse.SC_BAD_REQUEST);
            }

            Wallet wallet = walletRepository.findByPhoneNumberWithLock(phoneNumber)
                    .orElseThrow(() -> new NotFoundException("No wallet found for the provided phone number " + phoneNumber));

            if (isWalletLocked(wallet)) {
                return responseService.createErrorResponse(response, "Wallet is locked. Please try again later.", HttpServletResponse.SC_BAD_REQUEST);
            }

            if (isLockedForPinChange(wallet)) {
                long remainingMinutes = getRemainingPinChangeLockTime(wallet);
                return responseService.createErrorResponse(response, "Too many PIN change attempts. Try again in " + remainingMinutes + " minutes.", HttpServletResponse.SC_BAD_REQUEST);
            }

            validateHashedPin(wallet.getPin(), pinRequest.getOldPin());

            if (passwordEncoder.matches(pinRequest.getPin(), wallet.getPin())) {
                return responseService.createErrorResponse(response, "New PIN cannot be the same as old PIN", HttpServletResponse.SC_BAD_REQUEST);
            }

            wallet.setPin(hashPin(pinRequest.getPin()));
            wallet.setPinLastChanged(LocalDateTime.now());
            wallet.setPinAttempts(0);
            wallet.setLocked(false);
            wallet.setLockedUntil(null);
            wallet.setUpdatedAt(LocalDateTime.now());
            resetPinChangeAttempts(wallet);
            walletRepository.save(wallet);

            return responseService.createSuccessResponse(response, "PIN changed successfully");

        } catch (NotFoundException ex) {
            log.error("Wallet not found for phone: {}", phoneNumber, ex);
            return responseService.createErrorResponse(response, "Wallet not found", HttpServletResponse.SC_NOT_FOUND);

        } catch (IncorrectPinException ex) {
            log.error("Incorrect old PIN for phone: {}", phoneNumber, ex);
            walletRepository.findByPhoneNumber(phoneNumber).ifPresent(wallet -> {
                incrementPinChangeAttempt(wallet);
                walletRepository.save(wallet);
            });
            return responseService.createErrorResponse(response, "Incorrect old PIN", HttpServletResponse.SC_BAD_REQUEST);

        } catch (InvalidPasswordException ex) {
            log.error("Invalid password for phone: {}", phoneNumber, ex);
            walletRepository.findByPhoneNumber(phoneNumber).ifPresent(wallet -> {
                incrementPinChangeAttempt(wallet);
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

        return passwordEncoder.matches(unverifiedPin, wallet.getPin());
    }

    /**
     * Overwrites a wallet's PIN as part of the OTP-verified PIN-reset flow.
     * Reuses the same private hashing/validation helpers as setPin, but
     * skips the "PIN already set" guard (the whole point here is
     * overwriting a forgotten one) and requires no old PIN, since
     * PinResetServiceImpl has already proven ownership via OTP.
     */
    @Transactional
    @Override
    public void resetPin(String phoneNumber, String newPin, String confirmNewPin) {
        currentUserService.assertOwnsPhoneNumber(phoneNumber);

        Wallet wallet = walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .orElseThrow(() -> new NotFoundException("No wallet found for the phone number provided: " + phoneNumber));

        if (isNotValidPinFormat(newPin)) {
            throw new InvalidPinException("PIN must be exactly 4 digits");
        }

        if (isWeakPin(newPin)) {
            throw new InvalidPinException("PIN is too weak or commonly used. Choose a stronger PIN.");
        }

        if (!newPin.equals(confirmNewPin)) {
            throw new BadRequestException("PINs do not match");
        }

        wallet.setPin(hashPin(newPin));
        wallet.setPinAttempts(0);
        wallet.setPinChangeAttempts(0);
        wallet.setLocked(false);
        wallet.setLockedUntil(null);
        wallet.setPinLastChanged(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        walletRepository.save(wallet);
        log.info("PIN reset successfully for wallet belonging to user: {}", phoneNumber);
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String hashPin(String rawPin) {
        return passwordEncoder.encode(rawPin);
    }

    private void validateHashedPin(String storedHashedPin, String rawPin) {
        if (storedHashedPin == null) {
            throw new IncorrectPinException("No PIN set for this wallet");
        }

        if (!passwordEncoder.matches(rawPin, storedHashedPin)) {
            throw new IncorrectPinException("Incorrect PIN");
        }
    }

    private boolean isNotValidPinFormat(String pin) {
        return pin == null || !pin.matches("\\d{4}");
    }

    private boolean isWeakPin(String pin) {
        if (pin == null) return false;

        Set<String> commonPins = Set.of(
                "1234", "1111", "0000", "1212", "1004",
                "2000", "4444", "2222", "6969", "9999",
                "7777", "5555", "6666", "1122", "1313"
        );

        if (commonPins.contains(pin)) {
            return true;
        }

        if (pin.chars().allMatch(c -> c == pin.charAt(0))) {
            return true;
        }

        if (isSequential(pin)) {
            return true;
        }

        if (pin.length() == 4 && pin.charAt(0) == pin.charAt(2) && pin.charAt(1) == pin.charAt(3)) {
            return true;
        }

        return false;
    }

    private boolean isSequential(String pin) {
        boolean ascending = true;
        boolean descending = true;

        for (int i = 0; i < pin.length() - 1; i++) {
            int current = Character.getNumericValue(pin.charAt(i));
            int next = Character.getNumericValue(pin.charAt(i + 1));

            if (next != current + 1) ascending = false;
            if (next != current - 1) descending = false;
        }

        return ascending || descending;
    }

    private boolean isWalletLocked(Wallet wallet) {
        if (!Boolean.TRUE.equals(wallet.getLocked())) return false;

        LocalDateTime lockedUntil = wallet.getLockedUntil();
        if (lockedUntil == null) return true;

        LocalDateTime now = LocalDateTime.now();
        if (lockedUntil.isBefore(now)) {
            unlockWallet(wallet);
            return false;
        }
        return true;
    }

    private boolean isLockedForPinChange(Wallet wallet) {
        Integer attempts = wallet.getPinChangeAttempts();
        LocalDateTime lastAttempt = wallet.getLastPinChangeAttempt();

        if (attempts != null && attempts >= MAX_PIN_CHANGE_ATTEMPTS && lastAttempt != null) {
            return lastAttempt.plusMinutes(PIN_CHANGE_LOCK_MINUTES).isAfter(LocalDateTime.now());
        }
        return false;
    }

    private long getRemainingPinChangeLockTime(Wallet wallet) {
        LocalDateTime lastAttempt = wallet.getLastPinChangeAttempt();
        if (lastAttempt == null) return 0;

        LocalDateTime unlockTime = lastAttempt.plusMinutes(PIN_CHANGE_LOCK_MINUTES);
        LocalDateTime now = LocalDateTime.now();

        if (unlockTime.isAfter(now)) {
            return Duration.between(now, unlockTime).toMinutes();
        }
        return 0;
    }

    private void incrementPinChangeAttempt(Wallet wallet) {
        Integer currentAttempts = wallet.getPinChangeAttempts();
        wallet.setPinChangeAttempts((currentAttempts == null) ? 1 : currentAttempts + 1);
        wallet.setLastPinChangeAttempt(LocalDateTime.now());
    }

    private void resetPinChangeAttempts(Wallet wallet) {
        wallet.setPinChangeAttempts(0);
        wallet.setLastPinChangeAttempt(null);
    }

    private void unlockWallet(Wallet wallet) {
        wallet.setLocked(false);
        wallet.setLockedUntil(null);
    }
}