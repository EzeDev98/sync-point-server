package com.sp.syncPoint.feature.passwordreset.web;

import com.sp.syncPoint.feature.passwordreset.dto.CompletePasswordResetRequest;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetRequestDto;
import com.sp.syncPoint.common.reset.dto.VerifyOtpRequest;
import com.sp.syncPoint.feature.passwordreset.dto.PasswordResetResponse;
import com.sp.syncPoint.common.reset.dto.VerifyOtpResponse;
import com.sp.syncPoint.feature.passwordreset.service.PasswordResetService;
import com.sp.syncPoint.common.util.RateLimiterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.sp.syncPoint.common.constants.GeneralConstants.MAX_RESET_PASSWORD_REQUEST;
import static com.sp.syncPoint.common.constants.GeneralConstants.RESET_PASSWORD_RATE_LIMIT_DURATION;

@RestController
@RequestMapping("${app.title}/password-reset")
@Tag(name = "Password Reset API", description = "Phone-number OTP based password reset flow")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;

    public PasswordResetController(PasswordResetService passwordResetService, RateLimiterService rateLimiterService) {
        this.passwordResetService = passwordResetService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponse> requestOtp(@RequestBody PasswordResetRequestDto request) {
        rateLimiterService.checkRateLimit("ratelimit:password-reset-otp:phone:" + request.getPhoneNumber(), MAX_RESET_PASSWORD_REQUEST, Duration.ofHours(RESET_PASSWORD_RATE_LIMIT_DURATION));
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