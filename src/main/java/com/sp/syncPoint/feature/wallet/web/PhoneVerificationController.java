package com.sp.syncPoint.feature.wallet.web;

import com.sp.syncPoint.client.dto.response.ActivationResponse;
import com.sp.syncPoint.feature.wallet.dto.request.ResendPhoneVerificationRequest;
import com.sp.syncPoint.feature.wallet.dto.request.VerifyPhoneRequest;
import com.sp.syncPoint.feature.wallet.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.title}/phone-number")
@Tag(name = "Registration Verification API", description = "Resource for registration verifications using phone number")
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
        this.phoneVerificationService = phoneVerificationService;
    }

    @PostMapping("/verify")
    public ResponseEntity<ActivationResponse> verifyPhone(@RequestBody VerifyPhoneRequest request) {
        return ResponseEntity.ok(phoneVerificationService.verifyPhone(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ActivationResponse> resendPhoneVerification(@RequestBody ResendPhoneVerificationRequest request) {
        return ResponseEntity.ok(phoneVerificationService.resendPhoneVerification(request));
    }
}
