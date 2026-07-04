package com.sp.sync_point.controller;


import com.sp.sync_point.dto.PinRequest;
import com.sp.sync_point.dto.SetPinRequest;
import com.sp.sync_point.dto.UserRequest;
import com.sp.sync_point.exception.KeycloakAuthenticationException;
import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.response.LoginResponse;
import com.sp.sync_point.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("${app.title}")
@Tag(name = "Wallet API", description = "Resource for managing wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(method = "POST", summary = "Wallet login", description = "This endpoint allows a User to successfully login to their wallet. When the login, most of the user's details are sent to the client for storage and subsequent usage.")
    @PostMapping("/wallet/login")
    public ResponseEntity<LoginResponse> walletLogin(@RequestBody UserRequest request) {
        LoginResponse response = walletService.walletLogin(request);
        return ResponseEntity.ok(response);
    }

    @Operation(method = "PUT", summary = "set PIN", description = "This endpoint will allow users to set their transaction PIN. This is mainly for first timers")
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

    @Operation(method = "POST", summary = "Verify Pin", description = "This endpoint allows for the verification of wallet pin existence in the offline payment app database. It returns true if it exist and matches with the request sent by the client else it returns false")
    @GetMapping("/wallet/verify/pin")
    public ResponseEntity<Boolean> verifyPin(@RequestParam String walletId, String unverifiedPin) {
        Boolean isVerified = walletService.verifyPin(walletId, unverifiedPin);
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "POST", summary = "Verify wallet pin is set ", description = "This endpoint allows a user to verify if a user has set wallet pin in the offline payment app database. It returns true if he has already set a pin and false if not")
    @GetMapping("/wallet/has-pin")
    public ResponseEntity<Boolean> isWalletPinSet(@RequestParam String walletId) {
        Boolean isVerified = walletService.isWalletPinSet(walletId);
        return ResponseEntity.ok(isVerified);
    }

    @Operation(method = "POST", summary = "Verify offline pay account exist", description = "This endpoint allows a user to verify offline pay account exist in the database. It returns true if the account exist and false if not")
    @GetMapping("/wallet/verify/account")
    public ResponseEntity<Boolean> doesAccountExist(@RequestParam String phoneNumber) {
        Boolean response = walletService.doesAccountExist(phoneNumber);
        return ResponseEntity.ok(response);
    }


}
