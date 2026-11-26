package com.sp.syncPoint.feature.wallet.service.impl;

import com.sp.syncPoint.common.exception.*;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import com.sp.syncPoint.feature.transaction.repository.TransactionRepository;
import com.sp.syncPoint.feature.transaction.util.TransactionUtils;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;
import com.sp.syncPoint.feature.wallet.dto.response.WalletFundingResponse;
import com.sp.syncPoint.feature.wallet.dto.request.WalletTransferRequest;
import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
import com.sp.syncPoint.feature.wallet.service.WalletFundingService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static com.sp.syncPoint.feature.transaction.enums.EntryType.CREDIT;
import static com.sp.syncPoint.feature.transaction.enums.EntryType.DEBIT;
import static com.sp.syncPoint.feature.transaction.enums.TransactionStatus.SUCCESS;
import static com.sp.syncPoint.feature.transaction.enums.TransactionType.OFFLINE_WALLET_FUNDING;
import static com.sp.syncPoint.feature.transaction.enums.TransactionType.ONLINE_WALLET_FUNDING;
import static java.math.BigDecimal.ZERO;

@Service
@RequiredArgsConstructor
public class WalletFundingServiceImpl implements WalletFundingService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionUtils transactionUtils;
    private final PinHasher pinHasher;
    private final CurrentUserService currentUserService;

    @Transactional
    @Override
    public WalletFundingResponse fundOfflineWallet(WalletTransferRequest request) {
        validateFundingRequest(request);

        return executeFundingOperation(() -> {
            Optional<Transaction> existingTransaction = transactionRepository.findByRequestRef(request.getRequestRef());
            if (existingTransaction.isPresent()) {
                return toReplayResponse(existingTransaction.get());
            }

            Wallet wallet = getWalletOrThrow(request.getPhoneNumber());
            verifyPin(wallet, request.getTransactionPin());

            if (wallet.getMirrorBalance() == null || wallet.getMirrorBalance().compareTo(ZERO) < 0) {
                throw new InsufficientFundsException("Wallet does not have any funds");
            }

            if (wallet.getOfflineLimit().compareTo(request.getAmount()) < 0) {
                throw new BadRequestException("Your offline limit is " + wallet.getOfflineLimit() + "The amount you are requesting for is more than your offline limit try a lesser amount");
            }

            if (wallet.getMirrorBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException("You don't have enough funds in your wallet");
            }

            BigDecimal balanceBefore = wallet.getMirrorBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());
            wallet.setMirrorBalance(balanceAfter);

            String description = String.format("Offline wallet funded with %s", request.getAmount());

            Transaction transaction = transactionUtils.createTransaction(request.getRequestRef(), OFFLINE_WALLET_FUNDING, DEBIT, wallet.getWalletId(), wallet.getUser().getUsername(), wallet.getPhoneNumber(), balanceBefore, balanceAfter, null, null, null, null, null, request.getAmount(), description, SUCCESS, null);

            Optional<WalletFundingResponse> duplicateTransaction = saveTransactionIdempotently(transaction, request.getRequestRef());
            if (duplicateTransaction.isPresent()) {
                return duplicateTransaction.get();
            }

            walletRepository.save(wallet);

            return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", request.getAmount(), transaction.getTransactionReference());
        });
    }

    @Transactional
    @Override
    public WalletFundingResponse transferToOnlineAccount(WalletTransferRequest request) {
        validateFundingRequest(request);

        return executeFundingOperation(() -> {
            Optional<Transaction> existingTransaction = transactionRepository.findByRequestRef(request.getRequestRef());
            if (existingTransaction.isPresent()) {
                return toReplayResponse(existingTransaction.get());
            }

            Wallet wallet = getWalletOrThrow(request.getPhoneNumber());
            verifyPin(wallet, request.getTransactionPin());

            BigDecimal balanceBefore = wallet.getMirrorBalance();
            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());
            wallet.setMirrorBalance(balanceAfter);

            String description = String.format("Online wallet funded with %s", request.getAmount());

            Transaction transaction = transactionUtils.createTransaction(request.getRequestRef(), ONLINE_WALLET_FUNDING, CREDIT, null, null, null, null, null, wallet.getWalletId(), wallet.getUser().getUsername(), wallet.getPhoneNumber(), balanceBefore, balanceAfter, request.getAmount(), description, SUCCESS, null);

            Optional<WalletFundingResponse> duplicateTransaction = saveTransactionIdempotently(transaction, request.getRequestRef());
            if (duplicateTransaction.isPresent()) {
                return duplicateTransaction.get();
            }

            walletRepository.save(wallet);

            return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", request.getAmount(), transaction.getTransactionReference());
        });
    }

    private void validateFundingRequest(WalletTransferRequest request) {
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new EmptyFieldException("Phone number must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getTransactionPin() == null || request.getTransactionPin().isBlank()) {
            throw new EmptyFieldException("PIN must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Amount must be greater than zero");
        }

        if (request.getRequestRef() == null || request.getRequestRef().isBlank()) {
            throw new EmptyFieldException("Request reference must not be null or empty", HttpStatus.BAD_REQUEST);
        }

        currentUserService.assertOwnsPhoneNumber(request.getPhoneNumber());
    }

    private Wallet getWalletOrThrow(String phoneNumber) {
        return walletRepository.findByPhoneNumberWithLock(phoneNumber)
                .orElseThrow(() -> new EntityNotFoundException("No wallet found for phone number: " + phoneNumber));
    }

    private void verifyPin(Wallet wallet, String transactionPin) {
        if (!pinHasher.matches(transactionPin, wallet.getPin())) {
            throw new InvalidPinException("Pin doesn't match");
        }
    }

    private WalletFundingResponse toReplayResponse(Transaction priorTransaction) {
        return new WalletFundingResponse(HttpServletResponse.SC_OK, "Wallet funded successfully", priorTransaction.getAmount(), priorTransaction.getTransactionReference());
    }

    private Optional<WalletFundingResponse> saveTransactionIdempotently(Transaction transaction, String requestRef) {
        try {
            transactionRepository.save(transaction);
            return Optional.empty();
        } catch (DataIntegrityViolationException dup) {
            Transaction priorTransaction = transactionRepository.findByRequestRef(requestRef)
                    .orElseThrow(() -> dup);
            return Optional.of(toReplayResponse(priorTransaction));
        }
    }

    private WalletFundingResponse executeFundingOperation(Supplier<WalletFundingResponse> operation) {
        try {
            return operation.get();
        } catch (EmptyFieldException | InvalidInputException | EntityNotFoundException | InvalidPinException | InsufficientFundsException | InsufficientBalanceException | BadRequestException ex) {
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new InternalServerErrorException("Transaction is being processed concurrently. Please retry.", ex);
        } catch (JpaSystemException | TransactionSystemException ex) {
            throw new InternalServerErrorException("Database transaction failed.", ex);
        } catch (Exception ex) {
            throw new InternalServerErrorException("Transfer failed. Please try again later.", ex);
        }
    }
}