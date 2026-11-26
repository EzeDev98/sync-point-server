package com.sp.syncPoint.feature.session.web;

import com.sp.syncPoint.common.exception.BadRequestException;
import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.session.dto.InvalidatedSessionResponse;
import com.sp.syncPoint.feature.session.dto.SessionInvalidateRequest;
import com.sp.syncPoint.feature.session.security.SessionInvalidateAuthorizer;
import com.sp.syncPoint.feature.session.service.SessionManagementService;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${app.title}")
@RequiredArgsConstructor
@Tag(name = "Admin Session API", description = "Server-to-server session invalidation, pushed by internal-auth")
public class AdminSessionController {

    private final SessionInvalidateAuthorizer sessionInvalidateAuthorizer;
    private final SessionManagementService sessionManagementService;
    private final UserRepository userRepository;

    @PostMapping("/admin/sessions/invalidate-by-username")
    public ResponseEntity<BaseResponse> invalidateByUsername(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret,
            @RequestBody(required = false) SessionInvalidateRequest request) {

        sessionInvalidateAuthorizer.authorize(providedSecret);

        String username = request != null ? request.getUsername() : null;
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username is required");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElse(null);
        if (user == null) {
            log.warn("[SessionInvalidate] No local user found for username {} - nothing to release", username);
            return ResponseEntity.ok(new BaseResponse(HttpStatus.OK.value(), "No local user found for username " + username));
        }

        InvalidatedSessionResponse result = sessionManagementService.invalidateUserSessionByAdmin(user.getPhoneNumber());
        log.info("[SessionInvalidate] Released local session for {} following internal-auth push", username);
        return ResponseEntity.ok(new BaseResponse(result.getStatus(), result.getMessage()));
    }
}
