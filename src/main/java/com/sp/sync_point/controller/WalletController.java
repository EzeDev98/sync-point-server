package com.sp.sync_point.controller;


import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.UserRequest;

import com.sp.sync_point.dto.VerifyPinRequest;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.LoginResponse;
import com.sp.sync_point.service.WalletService;
import com.sp.sync_point.service.impl.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;


import static com.sp.sync_point.constants.GeneralConstants.*;

@RestController
@RequestMapping("${app.title}")
@Tag(name = "Wallet API", description = "Resource for managing wallets")
public class WalletController {

    private final WalletService walletService;
    private final RateLimiterService rateLimiterService;

    public WalletController(WalletService walletService, RateLimiterService rateLimiterService) {
        this.walletService = walletService;
        this.rateLimiterService = rateLimiterService;
    }

    @Operation(method = "POST", summary = "Wallet login", description = "This endpoint allows a User to successfully login to their wallet. When the login, most of the user's details are sent to the client for storage and subsequent usage.")
    @PostMapping("/wallet/login")
    public ResponseEntity<LoginResponse> walletLogin(@RequestBody UserRequest request, HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("X-Forwarded-For");
        String clientIp = (header != null && !header.isBlank()) ? header.split(",")[0].trim() : httpRequest.getRemoteAddr();
        rateLimiterService.checkRateLimit("ratelimit:login:ip" + clientIp, MAX_LOGIN_REQUEST, Duration.ofMinutes(LOGIN_RATE_LIMIT_DURATION));
        LoginResponse response = walletService.walletLogin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "POST", summary = "set PIN", description = "This endpoint will allow users to set their transaction PIN. This is mainly for first timers")
    @PostMapping("/wallet/set-pin")
    public ResponseEntity<BaseResponse> setPin(@RequestBody SetPinRequest request) {
        BaseResponse response = walletService.setPin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "PUT", summary = "Change wallet pin", description = "This endpoint allows users to successfully change the their wallet pin")
    @PutMapping("/wallet/change-pin")
    public ResponseEntity<BaseResponse> changePin(@RequestBody PinRequest request) {
        BaseResponse response = walletService.changePin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "GET", summary = "Verify Pin", description = "This endpoint allows for the verification of wallet pin existence in the offline payment app database. It returns true if it exist and matches with the request sent by the client else it returns false")
    @GetMapping("/wallet/verify/pin")
    public ResponseEntity<Boolean> verifyPin(@RequestBody VerifyPinRequest request) {
        Boolean isVerified = walletService.verifyPin(request.getWalletId(), request.getUnverifiedPin());
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "GET", summary = "Verify wallet pin is set ", description = "This endpoint allows a user to verify if a user has set wallet pin in the offline payment app database. It returns true if he has already set a pin and false if not")
    @GetMapping("/wallet/has-pin")
    public ResponseEntity<Boolean> isWalletPinSet(@RequestParam String walletId) {
        Boolean isVerified = walletService.isWalletPinSet(walletId);
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "GET", summary = "Verify offline pay account exist", description = "This endpoint allows a user to verify offline pay account exist in the database. It returns true if the account exist and false if not")
    @GetMapping("/wallet/verify/account")
    public ResponseEntity<Boolean> doesAccountExist(@RequestParam String phoneNumber) {
        Boolean response = walletService.doesAccountExist(phoneNumber);
        return ResponseEntity.ok(response);
    }

}
