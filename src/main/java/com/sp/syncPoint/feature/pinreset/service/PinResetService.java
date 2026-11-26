package com.sp.syncPoint.feature.pinreset.service;


import com.sp.syncPoint.common.reset.dto.VerifyOtpRequest;
import com.sp.syncPoint.common.reset.dto.VerifyOtpResponse;
import com.sp.syncPoint.feature.pinreset.dto.CompletePinResetRequest;
import com.sp.syncPoint.feature.pinreset.dto.PinResetRequestDto;
import com.sp.syncPoint.feature.pinreset.dto.PinResetResponse;

public interface PinResetService {

    PinResetResponse requestOtp(PinResetRequestDto request);

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    PinResetResponse completeReset(CompletePinResetRequest request);
}