package com.sp.sync_point.controller;

import com.sp.sync_point.dto.CompletePinResetRequest;
import com.sp.sync_point.dto.PinResetRequestDto;
import com.sp.sync_point.dto.VerifyPinOtpRequest;
import com.sp.sync_point.response.PinResetResponse;
import com.sp.sync_point.response.VerifyOtpResponse;
import com.sp.sync_point.service.PinResetService;
import com.sp.sync_point.service.impl.RateLimiterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.sp.sync_point.constants.GeneralConstants.MAX_RESET_PIN_REQUEST;
import static com.sp.sync_point.constants.GeneralConstants.RESET_PIN_RATE_LIMIT_DURATION;

/**
 * Unlike PasswordResetController, no permitAll() entries were added to
 * WebSecurityConfig for these endpoints — they fall under the default
 * .anyRequest().authenticated() rule, same as /wallet/set-pin and
 * /wallet/change-pin. Callers must supply a valid Bearer token.
 */
@RestController
@RequestMapping("${app.title}/pin-reset")
@Tag(name = "Pin Reset API", description = "Phone-number OTP based wallet PIN reset flow")
public class PinResetController {

    private final PinResetService pinResetService;
    private final RateLimiterService rateLimiterService;

    public PinResetController(PinResetService pinResetService, RateLimiterService rateLimiterService) {
        this.pinResetService = pinResetService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/request")
    public ResponseEntity<PinResetResponse> requestOtp(@RequestBody PinResetRequestDto request) {
        rateLimiterService.checkRateLimit("ratelimit:pin-reset-otp:phone:" + request.getPhoneNumber(), MAX_RESET_PIN_REQUEST, Duration.ofHours(RESET_PIN_RATE_LIMIT_DURATION));
        return ResponseEntity.ok(pinResetService.requestOtp(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@RequestBody VerifyPinOtpRequest request) {
        return ResponseEntity.ok(pinResetService.verifyOtp(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<PinResetResponse> completeReset(@RequestBody CompletePinResetRequest request) {
        return ResponseEntity.ok(pinResetService.completeReset(request));
    }
}