package com.sp.syncPoint.common.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<Class<? extends RuntimeException>, HttpStatus> SIMPLE_STATUS_MAPPINGS = Map.ofEntries(
            Map.entry(AccountNotVerifiedException.class, HttpStatus.FORBIDDEN),
            Map.entry(IncorrectPinException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(InsufficientFundsException.class, HttpStatus.PAYMENT_REQUIRED),
            Map.entry(InvalidTransactionRequestException.class, HttpStatus.BAD_REQUEST),
            Map.entry(UserNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(AuthenticationException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(WalletNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(TransactionsNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(FailedToRetrieveTransactionBalanceException.class, HttpStatus.BAD_REQUEST),
            Map.entry(KeycloakAuthenticationException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(InvalidPasswordException.class, HttpStatus.BAD_REQUEST),
            Map.entry(InternalServerErrorException.class, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(EmptyFieldException.class, HttpStatus.BAD_REQUEST),
            Map.entry(InvalidTransactionLimitException.class, HttpStatus.BAD_REQUEST),
            Map.entry(CustomApiException.class, HttpStatus.BAD_REQUEST),
            Map.entry(TransactionPinNotSetException.class, HttpStatus.BAD_REQUEST),
            Map.entry(InvalidPinException.class, HttpStatus.BAD_REQUEST),
            Map.entry(EmptyRequestException.class, HttpStatus.BAD_REQUEST),
            Map.entry(WalletLockedException.class, HttpStatus.LOCKED),
            Map.entry(PinVerificationException.class, HttpStatus.BAD_REQUEST),
            Map.entry(PinReuseException.class, HttpStatus.BAD_REQUEST),
            Map.entry(PinChangeLockedException.class, HttpStatus.LOCKED),
            Map.entry(InvalidInputException.class, HttpStatus.BAD_REQUEST),
            Map.entry(ResourceNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(ServiceUnavailableException.class, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(PaymentFailedException.class, HttpStatus.BAD_REQUEST),
            Map.entry(AlreadyExistException.class, HttpStatus.CONFLICT),
            Map.entry(RegistrationException.class, HttpStatus.BAD_REQUEST),
            Map.entry(InternalAuthRegistrationFailedException.class, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(MaxAttemptsExceededException.class, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(UserAlreadyExistsException.class, HttpStatus.CONFLICT),
            Map.entry(LockAcquisitionException.class, HttpStatus.CONFLICT),
            Map.entry(RefreshTokenInvalidException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(ActiveSessionExistsException.class, HttpStatus.CONFLICT),
            Map.entry(OtpResendCooldownException.class, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(OtpInvalidException.class, HttpStatus.BAD_REQUEST),
            Map.entry(OtpExpiredException.class, HttpStatus.BAD_REQUEST),
            Map.entry(UnauthorizedException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(BadRequestException.class, HttpStatus.BAD_REQUEST),
            Map.entry(EntityNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(NotFoundException.class, HttpStatus.NOT_FOUND)
    );

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleMappedRuntimeException(RuntimeException ex) {
        HttpStatus status = SIMPLE_STATUS_MAPPINGS.get(ex.getClass());
        if (status == null) {
            return buildUnexpectedErrorResponse(ex);
        }
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }

    @ExceptionHandler(InternalAuthCallException.class)
    public ProblemDetail handleInternalAuthCallException(InternalAuthCallException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateTransactionCheckException.class)
    public ProblemDetail handleDuplicateTransactionCheckException(DuplicateTransactionCheckException ex){
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("Insufficient Balance");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access Denied");
        return problem;
    }

    @ExceptionHandler(SystemWalletInsufficientBalanceException.class)
    public ProblemDetail handleSystemWalletInsufficientBalance(SystemWalletInsufficientBalanceException ex) {
        log.error("System wallet insufficient balance: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "This operation is temporarily unavailable. Please try again shortly.");
        problem.setTitle("Service Temporarily Unavailable");
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessException(DataAccessException ex) {
        log.error("Database connectivity failure", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable. Please try again shortly.");
        problem.setTitle("Service Temporarily Unavailable");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        return buildUnexpectedErrorResponse(ex);
    }

    private ProblemDetail buildUnexpectedErrorResponse(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("[traceId={}] Unhandled exception", traceId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setProperty("traceId", traceId);
        return problem;
    }
}