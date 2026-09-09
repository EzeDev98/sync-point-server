package com.sp.syncPoint.feature.wallet.service;

import com.sp.syncPoint.client.dto.response.ActivationResponse;
import com.sp.syncPoint.feature.wallet.dto.request.ResendPhoneVerificationRequest;
import com.sp.syncPoint.feature.wallet.dto.request.VerifyPhoneRequest;

public interface PhoneVerificationService {
    ActivationResponse verifyPhone(VerifyPhoneRequest request);
    ActivationResponse resendPhoneVerification(ResendPhoneVerificationRequest request);
}
