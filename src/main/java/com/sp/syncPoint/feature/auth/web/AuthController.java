package com.sp.syncPoint.feature.auth.web;


import com.sp.syncPoint.feature.auth.service.AuthService;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import com.sp.syncPoint.feature.user.service.impl.CurrentUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("${app.title}")
@Tag(name = "Auth API", description = "Resource for Internal Auth & Token validation and generation")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final SessionManagementService sessionManagementService;
    private final JwtDecoder jwtDecoder;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, SessionManagementService sessionManagementService,
                          JwtDecoder jwtDecoder, CurrentUserService currentUserService) {
        this.authService = authService;
        this.sessionManagementService = sessionManagementService;
        this.jwtDecoder = jwtDecoder;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/auth/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token is required"));
        }

        AccessTokenResponse tokenResponse = authService.refreshAccessToken(refreshToken);

        try {
            Jwt jwt = jwtDecoder.decode(tokenResponse.getToken());
            String ownPhoneNumber = currentUserService.resolveUser(jwt).getPhoneNumber();
            sessionManagementService.updateSessionOnRefresh(ownPhoneNumber, tokenResponse.getToken());
        } catch (Exception ex) {
            log.warn("Could not resolve local user for refreshed token; session TTL not reset", ex);
        }

        return ResponseEntity.ok(tokenResponse);
    }
}