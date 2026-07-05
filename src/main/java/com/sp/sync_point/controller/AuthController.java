package com.sp.sync_point.controller;

import com.sp.sync_point.service.AuthService;
import com.sp.sync_point.service.SessionManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("${app.title}")
@Tag(name = "Auth API", description = "Resource for Internal Auth & Token validation and generation")
public class AuthController {

    private final AuthService authService;
    private final SessionManagementService sessionManagementService;

    public AuthController(AuthService authService, SessionManagementService sessionManagementService) {
        this.authService = authService;
        this.sessionManagementService = sessionManagementService;
    }

    @PostMapping("/auth/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        String phoneNumber = body.get("phoneNumber");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token is required"));
        }

        AccessTokenResponse tokenResponse = authService.refreshAccessToken(refreshToken);

        if (phoneNumber != null && !phoneNumber.isBlank()) {
            sessionManagementService.updateSessionOnRefresh(phoneNumber, tokenResponse.getToken());
        }

        return ResponseEntity.ok(tokenResponse);
    }
}
