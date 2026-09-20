package com.sp.syncPoint.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sp.syncPoint.feature.session.repository.UserSessionRepository;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Rejects requests whose JWT is still cryptographically valid but whose
 * wallet session has been closed (admin "Logout all sessions", user logout,
 * or a newer login replacing it).
 * <p>
 * Without this, closing the user_sessions row only takes effect at the next
 * login attempt: the already-issued access token keeps working until it
 * expires on its own.
 * <p>
 * Only tokens that belong to a local wallet user are checked. A token whose
 * subject has no local User record (an admin or service token calling an
 * admin endpoint) passes through untouched, exactly as before.
 * <p>
 * Deliberately NOT a @Component: Spring Boot would also register it as a
 * plain servlet filter that runs before Spring Security, where there is no
 * authentication to inspect. It is added to the security chain explicitly in
 * WebSecurityConfig, right after BearerTokenAuthenticationFilter.
 */
@Slf4j
public class ActiveSessionFilter extends OncePerRequestFilter {

    /** Machine-readable code the client keys on to tear its local session down. */
    public static final String SESSION_REVOKED_CODE = "SESSION_REVOKED";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public ActiveSessionFilter(UserRepository userRepository, UserSessionRepository userSessionRepository) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            Optional<User> localUser = userRepository.findByInternalAuthId(jwt.getSubject());

            if (localUser.isPresent() && !userSessionRepository.existsByUserIdAndIsActiveTrue(localUser.get().getId())) {
                log.warn("Rejected {} {} - no active session for user id {}",
                        request.getMethod(), request.getRequestURI(), localUser.get().getId());
                reject(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), Map.of(
                "status", HttpStatus.UNAUTHORIZED.value(),
                "title", "Unauthorized",
                "detail", "Your session has ended. Please sign in again.",
                "code", SESSION_REVOKED_CODE
        ));
    }
}
