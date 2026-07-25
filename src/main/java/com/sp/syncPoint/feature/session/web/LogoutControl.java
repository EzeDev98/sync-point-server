package com.sp.syncPoint.feature.session.web;

import com.sp.syncPoint.feature.session.dto.LogoutResponse;
import com.sp.syncPoint.feature.session.service.LogoutService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("${app.title}")
@Tag(name = "Transaction API", description = "Resource for managing transactions")
public class LogoutControl {

    private final LogoutService logoutService;

    @PostMapping("/user/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        LogoutResponse response = logoutService.logout(token);
        return ResponseEntity.ok(response);
    }
}
