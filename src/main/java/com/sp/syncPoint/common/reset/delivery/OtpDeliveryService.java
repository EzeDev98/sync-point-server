package com.sp.syncPoint.common.reset.delivery;

public interface OtpDeliveryService {
    void sendResetOtp(String phoneNumber, String otp);
}