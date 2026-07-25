package com.sp.syncPoint.common.reset.delivery;

/**
 * Decoupled abstraction for delivering a password-reset OTP to a user's
 * phone number. The password-reset business logic depends only on this
 * interface and never on a specific provider (Termii, Twilio, Africa's
 * Talking, etc.), so the delivery mechanism can be swapped in without
 * touching PasswordResetServiceImpl.
 */
public interface OtpDeliveryService {

    /**
     * Send a password-reset OTP to the given phone number.
     *
     * @param phoneNumber the recipient's registered phone number
     * @param otp         the raw (unhashed) OTP to deliver
     */
    void sendPasswordResetOtp(String phoneNumber, String otp);
}