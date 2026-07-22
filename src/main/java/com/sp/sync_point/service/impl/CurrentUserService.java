package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.UserNotFoundException;
import com.sp.sync_point.model.User;
import com.sp.sync_point.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves "which local user is making this request" from the authenticated
 * JWT, and provides a small set of ownership checks that endpoints should
 * call before acting on a client-supplied identifier (phoneNumber, walletId,
 * etc.).
 * <p>
 * The link between the JWT and the local {@link User} record is the
 * Keycloak user ID: every Keycloak access token's {@code sub} claim is the
 * same Keycloak user ID that gets stored on {@code User.keycloakId} at
 * registration time (see {@code UserServiceImpl#createUser}). We resolve
 * through that ID rather than trusting a username-style claim, since
 * exactly what claims a given Keycloak realm includes in the access token
 * is configuration-dependent and shouldn't be relied on for identity.
 * <p>
 * This class intentionally does nothing on its own to enforce anything —
 * it has to be called explicitly at the top of each endpoint/service method
 * that accepts a caller-supplied identifier. See the SyncPoint backend
 * audit (C1) for why that call is currently missing everywhere.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Returns the local {@link User} that owns the current request's JWT.
     *
     * @throws AccessDeniedException if there is no authenticated JWT on the
     *                                current request (should not normally
     *                                happen behind Spring Security, but this
     *                                is defensive)
     * @throws UserNotFoundException if the JWT is valid but doesn't
     *                                correspond to any local user record
     *                                (e.g. the user was deleted locally but
     *                                the Keycloak account still exists)
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("No authenticated user found for this request");
        }
        return resolveUser(jwt);
    }

    public User resolveUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("No local account found for the authenticated user"));
    }

    /**
     * The phone number of the currently authenticated user — the identifier
     * used throughout the wallet/transaction domain.
     */
    public String getCurrentPhoneNumber() {
        return getCurrentUser().getPhoneNumber();
    }

    /**
     * Verifies that {@code phoneNumber} belongs to the currently
     * authenticated user, and rejects the request (HTTP 403) if it does
     * not.
     * <p>
     * Call this before using a client-supplied phoneNumber to look up or
     * mutate a wallet/transaction/session.
     */
    public void assertOwnsPhoneNumber(String phoneNumber) {
        if (!ownsPhoneNumber(phoneNumber)) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }

    /**
     * Non-throwing form of {@link #assertOwnsPhoneNumber(String)}, for
     * endpoints where the caller may legitimately be either of two
     * parties (e.g. "transactions between A and B", where the caller
     * could be A or B) and the check needs to be combined with an OR.
     */
    public boolean ownsPhoneNumber(String phoneNumber) {
        String currentPhoneNumber = getCurrentPhoneNumber();
        return currentPhoneNumber != null && currentPhoneNumber.equals(phoneNumber);
    }
}