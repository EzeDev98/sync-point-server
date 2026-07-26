package com.sp.syncPoint.feature.wallet.web;


import com.sp.syncPoint.feature.wallet.dto.PinRequest;
import com.sp.syncPoint.feature.wallet.dto.SetPinRequest;

import com.sp.syncPoint.feature.auth.dto.UserRequest;

import com.sp.syncPoint.feature.wallet.dto.SigningKeyRequest;
import com.sp.syncPoint.feature.wallet.dto.VerifyPinRequest;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.auth.dto.LoginResponse;
import com.sp.syncPoint.feature.wallet.service.WalletAuthService;
import com.sp.syncPoint.feature.wallet.service.WalletPinService;

import com.sp.syncPoint.common.util.RateLimiterService;
import com.sp.syncPoint.feature.wallet.service.WalletSigningKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;


import static com.sp.syncPoint.common.constants.GeneralConstants.*;

@RestController
@RequestMapping("${app.title}")
@Tag(name = "Wallet API", description = "Resource for managing wallets")
public class WalletController {

    private final WalletAuthService walletAuthService;
    private final WalletPinService walletPinService;
    private final WalletSigningKeyService walletSigningKeyService;
    private final RateLimiterService rateLimiterService;

    public WalletController(WalletAuthService walletAuthService, WalletPinService walletPinService, WalletSigningKeyService walletSigningKeyService, RateLimiterService rateLimiterService) {
        this.walletAuthService = walletAuthService;
        this.walletPinService = walletPinService;
        this.walletSigningKeyService = walletSigningKeyService;
        this.rateLimiterService = rateLimiterService;
    }

    @Operation(method = "POST", summary = "Wallet login", description = "This endpoint allows a User to successfully login to their wallet. When the login, most of the user's details are sent to the client for storage and subsequent usage.")
    @PostMapping("/wallet/login")
    public ResponseEntity<LoginResponse> walletLogin(@RequestBody UserRequest request, HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("X-Forwarded-For");
        String clientIp = (header != null && !header.isBlank()) ? header.split(",")[0].trim() : httpRequest.getRemoteAddr();
        rateLimiterService.checkRateLimit("ratelimit:login:ip" + clientIp, MAX_LOGIN_REQUEST, Duration.ofMinutes(LOGIN_RATE_LIMIT_DURATION));
        LoginResponse response = walletAuthService.walletLogin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "POST", summary = "set PIN", description = "This endpoint will allow users to set their transaction PIN. This is mainly for first timers")
    @PostMapping("/wallet/set-pin")
    public ResponseEntity<BaseResponse> setPin(@RequestBody SetPinRequest request) {
        BaseResponse response = walletPinService.setPin(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @Operation(method = "PUT", summary = "Change wallet pin", description = "This endpoint allows users to successfully change the their wallet pin")
    @PutMapping("/wallet/change-pin")
    public ResponseEntity<BaseResponse> changePin(@RequestBody PinRequest request) {
        BaseResponse response = walletPinService.changePin(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @Operation(method = "POST", summary = "Verify Pin", description = "This endpoint allows for the verification of wallet pin existence in the offline payment app database. It returns true if it exist and matches with the request sent by the client else it returns false")
    @PostMapping("/wallet/verify/pin")
    public ResponseEntity<Boolean> verifyPin(@RequestBody VerifyPinRequest request) {
        Boolean isVerified = walletPinService.verifyPin(request.getWalletId(), request.getUnverifiedPin());
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "POST", summary = "Register offline signing key", description = "Registers this device's ECDSA P-256 public key (base64 SPKI) on the authenticated caller's wallet. The key is used to verify the signatures on the offline transfers this wallet sends, so a receiver can settle a received transfer without the sender being online.")
    @PostMapping("/wallet/signing-key")
    public ResponseEntity<BaseResponse> registerSigningKey(@RequestBody SigningKeyRequest request) {
        BaseResponse response = walletSigningKeyService.registerSigningKey(request.getSigningPublicKey());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @Operation(method = "GET", summary = "Verify wallet pin is set ", description = "This endpoint allows a user to verify if a user has set wallet pin in the offline payment app database. It returns true if he has already set a pin and false if not")
    @GetMapping("/wallet/has-pin")
    public ResponseEntity<Boolean> isWalletPinSet(@RequestParam String walletId) {
        Boolean isVerified = walletPinService.isWalletPinSet(walletId);
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "GET", summary = "Verify offline pay account exist", description = "This endpoint allows a user to verify offline pay account exist in the database. It returns true if the account exist and false if not")
    @GetMapping("/wallet/verify/account")
    public ResponseEntity<Boolean> doesAccountExist(@RequestParam String phoneNumber) {
        Boolean response = walletAuthService.doesAccountExist(phoneNumber);
        return ResponseEntity.ok(response);
    }

}