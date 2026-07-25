package com.sp.syncPoint.common.exception;

public class SystemWalletInsufficientBalanceException extends RuntimeException {
    public SystemWalletInsufficientBalanceException(String message) {
        super(message);
    }
}