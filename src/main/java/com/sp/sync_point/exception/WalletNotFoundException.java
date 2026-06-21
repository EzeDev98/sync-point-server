package com.sp.sync_point.exception;

public class WalletNotFoundException extends RuntimeException{
    public WalletNotFoundException(String message) {
        super(message);
    }
}
