package com.sp.sync_point.service.impl;

import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.UserRequest;
import com.sp.sync_point.exception.*;
import com.sp.sync_point.model.*;

import com.sp.sync_point.repository.SystemWalletRepository;
import com.sp.sync_point.repository.TransactionRepository;
import com.sp.sync_point.repository.WalletFundingRecordRepository;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.LoginResponse;
import com.sp.sync_point.service.*;

import com.sp.sync_point.utils.TransactionUtils;
import io.micrometer.core.annotation.Timed;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static com.sp.sync_point.constants.GeneralConstants.*;
import static com.sp.sync_point.enums.EntryType.CREDIT;
import static com.sp.sync_point.enums.TransactionStatus.COMPLETED;
import static com.sp.sync_point.enums.TransactionType.WALLET_CAPITALIZATION;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);

    private final WalletRepository walletRepository;
    private final UniqueValueGeneratorService uniqueValueService;
    private final PasswordEncoder passwordEncoder;
    private final ValidateService validateService;
    private final ResponseService responseService;
    private final EntityUtilityService entityUtilityService;
    private final AuthService authService;
    private final SessionManagementService sessionManagementService;
    private final SystemWalletRepository systemWalletRepo;
    private final WalletFundingRecordRepository walletFundingRecordRepo;
    private final TransactionUtils transactionUtils;
    private final TransactionRepository transactionRepo;

    @Timed(value = "wallet.setup", description = "Time taken to setup wallet")
    @Retryable(
            value = {DeadlockLoserDataAccessException.class, OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    @Override
    public void setupWallet(User user) {

        log.info("Starting wallet setup for user ID: {}, phone: {}", user.getId(), user.getPhoneNumber());

        try {

            if (user.getId() == null) {
                throw new EmptyFieldException("User ID cannot be null", HttpStatus.BAD_REQUEST);
            }

            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                throw new EmptyFieldException("Phone number cannot be null or empty", HttpStatus.BAD_REQUEST);
            }

            if (!user.getPhoneNumber().matches("^\\+?[0-9]{10,15}$")) {
                throw new InvalidInputException("Invalid phone number format");
            }

            Optional<Wallet> existingWallet = walletRepository.findByPhoneNumber(user.getPhoneNumber());
            if (existingWallet.isPresent()) {
                throw new AlreadyExistException("Wallet already exists" + existingWallet.get().getWalletId());
            }

            Wallet wallet = createNewWallet(FUND_AMOUNT, user);

            log.info("Successfully created wallet with ID: {} for user ID: {}", wallet.getWalletId(), user.getId());
        } catch (Exception e) {
            log.error("Failed to create wallet for user ID: {}, phone: {}", user.getId(), user.getPhoneNumber(), e);
            throw e;
        }
    }

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
                .orElseThrow(() -> new EntityNotFoundException("No wallet found for phone number: " + request.getPhoneNumber()));

        if (isLoginLocked(wallet)) {
            long remainingMinutes = getRemainingLoginLockTime(wallet);
            log.warn("Wallet login locked for phone: {}. Remaining lock time: {} minutes", request.getPhoneNumber(), remainingMinutes);
            throw new WalletLockedException("Wallet is temporarily locked. Try again in "+ remainingMinutes + " minutes", remainingMinutes);
        }

        User user = wallet.getUser();
        if (user == null) {
            log.error("No user associated with wallet for phone: {}", request.getPhoneNumber());
            throw new EntityNotFoundException("No user associated with this wallet");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            incrementLoginAttempt(wallet);

            log.warn("Invalid login attempt for phone: {}. Attempt {} of {}", request.getPhoneNumber(), wallet.getLoginAttempts(), MAX_LOGIN_ATTEMPTS);
            if (wallet.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
                lockWallet(wallet, WALLET_LOCKED_DURATION);
                throw new WalletLockedException("Wallet locked due to too many failed attempts. Try again later.", WALLET_LOCKED_DURATION);
            }

            throw new UnauthorizedException("Invalid phone number or password");
        }

        if (sessionManagementService.hasActiveSession(request.getPhoneNumber())) {
            log.warn("Login blocked for phone: {} — active session exists on another device", request.getPhoneNumber());
            throw new ActiveSessionExistsException("This account is currently active on another device. Please unlock and log out on that device before signing in here.");
        }

        AccessTokenResponse accessTokenResponse = authService.authenticateUser(user.getUsername(), request.getPassword());
        resetLoginAttempts(wallet);

        sessionManagementService.createSession(user.getId(), request.getPhoneNumber(), accessTokenResponse.getToken());
        wallet.setStatus(true);
        walletRepository.save(wallet);

        log.info("Wallet login successful for user ID: {}", user.getUserRefId());
        return new LoginResponse(accessTokenResponse.getToken(), accessTokenResponse.getRefreshToken(), accessTokenResponse.getExpiresIn(), accessTokenResponse.getRefreshExpiresIn(), request.getPhoneNumber(), user.getUsername(), user.getEmail(), user.getUserRefId(), wallet.getWalletId(), wallet.getMirrorBalance(), user.getId(), user.getFirstName(), user.getMiddleName(), user.getLastName(), wallet.getOfflineLimit(), wallet.isStatus());
    }

    @Transactional
    @Override
    public BaseResponse setPin(SetPinRequest request) {
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
                incrementPinChangeAttempt(wallet);
                log.warn("PIN mismatch for user: {}. Attempt: {}", request.getPhoneNumber(), wallet.getPinChangeAttempts());

                if (wallet.getPinChangeAttempts() >= 3) {
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

            Wallet wallet = walletRepository.findByPhoneNumber(phoneNumber)
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

        return wallet.getPin() != null;
    }

    @Override
    public boolean doesAccountExist(String phoneNumber) {
        return entityUtilityService.existByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean verifyPin(String walletId, String unverifiedPin) {
        Wallet wallet = entityUtilityService.fetchWallet(walletId);
        if (wallet == null || wallet.getPin() == null) {
            return false;
        }
        return passwordEncoder.matches(unverifiedPin, wallet.getPin());
    }


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

    private boolean isLoginLocked(Wallet wallet) {
        LocalDateTime loginLockUntil = wallet.getLoginLockUntil();
        if (loginLockUntil == null) return false;

        LocalDateTime now = LocalDateTime.now();
        if (loginLockUntil.isAfter(now)) {
            return true;
        }

        resetLoginAttempts(wallet);
        return false;
    }

    private void incrementLoginAttempt(Wallet wallet) {
        Integer currentAttempts = wallet.getLoginAttempts();
        wallet.setLoginAttempts((currentAttempts == null) ? 1 : currentAttempts + 1);
        wallet.setLastLoginAttempt(LocalDateTime.now());

        if (wallet.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            wallet.setLoginLockUntil(LocalDateTime.now().plusMinutes(WALLET_LOCKED_DURATION));
        }
    }

    private void resetLoginAttempts(Wallet wallet) {
        wallet.setLoginAttempts(0);
        wallet.setLastLoginAttempt(null);
        wallet.setLoginLockUntil(null);
    }

    private long getRemainingLoginLockTime(Wallet wallet) {
        LocalDateTime loginLockUntil = wallet.getLoginLockUntil();
        if (loginLockUntil == null || loginLockUntil.isBefore(LocalDateTime.now())) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), loginLockUntil).toMinutes();
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

    private void lockWallet(Wallet wallet, int minutes) {
        wallet.setLocked(true);
        wallet.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
    }

    private void unlockWallet(Wallet wallet) {
        wallet.setLocked(false);
        wallet.setLockedUntil(null);
    }

    private Wallet createNewWallet(BigDecimal amount, User user) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be positive");
        }

        log.debug("Creating wallet for user ID: {} with amount: {}", user.getId(), amount);

        SystemWallet systemWallet = systemWalletRepo.findByIdForUpdate(SYSTEM_WALLET_ID)
                .orElseThrow(() -> new WalletNotFoundException("No system wallet found for the provided Id" + SYSTEM_WALLET_ID));

        BigDecimal balanceBeforeTransfer = systemWallet.getAvailableBalance();

        if (balanceBeforeTransfer.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(String.format("System wallet insufficient balance. Available: %s, Required: %s", balanceBeforeTransfer, amount));
        }

        systemWallet.setAvailableBalance(balanceBeforeTransfer.subtract(amount));

        BigDecimal balanceAfterTransfer = systemWallet.getAvailableBalance();

        systemWallet.setCurrentLiability(systemWallet.getCurrentLiability().add(amount));
        systemWallet.setTotalDisbursed(systemWallet.getTotalDisbursed().add(amount));
        systemWallet.setActiveFundedUsers(systemWallet.getActiveFundedUsers() + 1);
        systemWallet.setTotalUsersFunded(systemWallet.getTotalUsersFunded() + 1);
        systemWallet.setUpdatedAt(LocalDateTime.now());

        Wallet wallet = createWallet(user, amount);
        Transaction transaction = createTransaction(systemWallet, wallet, user, balanceBeforeTransfer, balanceAfterTransfer, ZERO, wallet.getMirrorBalance(), amount);
        WalletFundingRecord walletFundingRecord = walletFundingRecord(user.getId(), wallet.getWalletId(), amount);

        walletRepository.save(wallet);
        transactionRepo.save(transaction);
        walletFundingRecordRepo.save(walletFundingRecord);
        systemWalletRepo.save(systemWallet);
        return wallet;
    }

    private Wallet createWallet(User user, BigDecimal amount) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(uniqueValueService.generateWalletId());
        wallet.setPhoneNumber(user.getPhoneNumber());
        wallet.setUser(user);
        wallet.setStatus(true);
        wallet.setMirrorBalance(amount);
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        return wallet;
    }

    private Transaction createTransaction(SystemWallet systemWallet, Wallet wallet, User user, BigDecimal balanceBeforeTransfer, BigDecimal balanceAfterTransfer, BigDecimal receiverBalanceBeforeTransfer, BigDecimal receiverBalanceAfterTransfer, BigDecimal amount) {
        String description = String.format("Wallet capitalization for user: %s (ID: %d)", user.getUsername(), user.getId());
        return transactionUtils.createTransaction(WALLET_CAPITALIZATION, CREDIT, systemWallet.getWalletId(), systemWallet.getName(), systemWallet.getPhoneNumber(), balanceBeforeTransfer, balanceAfterTransfer, wallet.getWalletId(), user.getUsername(), wallet.getPhoneNumber(), receiverBalanceBeforeTransfer, receiverBalanceAfterTransfer, amount, description, COMPLETED, null);
    }

    private WalletFundingRecord walletFundingRecord(Long userId, String walletId, BigDecimal amount) {
        WalletFundingRecord walletFunding = new WalletFundingRecord();

        walletFunding.setUserId(userId);
        walletFunding.setWalletId(walletId);
        walletFunding.setAmount(amount);
        walletFunding.setReclaimed(false);
        walletFunding.setFundedAt(LocalDateTime.now());
        walletFunding.setReclaimedAt(null);

        return walletFunding;
    }
}
