package com.sp.sync_point.controller;

import com.sp.sync_point.response.InvalidatedSessionResponse;
import com.sp.sync_point.service.SessionManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}")
@Tag(name = "User Session Api", description = "Resource for managing users sessions")
public class UserSessionController {

    private final SessionManagementService sessionService;

    @Operation(method = "POST", summary = "Invalidate session", description = "This an admin endpoint for invalidating a user's session in case they mistakenly got locked out of the app. This will be on very rare occasion")
    @PostMapping("/session/invalidate")
    public ResponseEntity<InvalidatedSessionResponse> invalidateSession(@RequestParam String phoneNumber) {
        InvalidatedSessionResponse response = sessionService.invalidateUserSessionByAdmin(phoneNumber);
        return ResponseEntity.ok(response);
    }
}
