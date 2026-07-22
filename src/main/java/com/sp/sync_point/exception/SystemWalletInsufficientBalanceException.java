package com.sp.sync_point.exception;

public class SystemWalletInsufficientBalanceException extends RuntimeException {
    public SystemWalletInsufficientBalanceException(String message) {
        super(message);
    }
}