package com.sp.sync_point.controller;

import com.sp.sync_point.dto.CompletePasswordResetRequest;
import com.sp.sync_point.dto.PasswordResetRequestDto;
import com.sp.sync_point.dto.VerifyOtpRequest;
import com.sp.sync_point.response.PasswordResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.PasswordResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.title}/password-reset")
@Tag(name = "Password Reset API", description = "Phone-number OTP based password reset flow")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponse> requestOtp(@RequestBody PasswordResetRequestDto request) {
        return ResponseEntity.ok(passwordResetService.requestOtp(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(passwordResetService.verifyOtp(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<PasswordResetResponse> completeReset(@RequestBody CompletePasswordResetRequest request) {
        return ResponseEntity.ok(passwordResetService.completeReset(request));
    }
}