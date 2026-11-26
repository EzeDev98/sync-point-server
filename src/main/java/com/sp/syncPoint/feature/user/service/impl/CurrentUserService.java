package com.sp.syncPoint.feature.user.service.impl;

import com.sp.syncPoint.common.exception.UserNotFoundException;
import com.sp.syncPoint.feature.user.model.User;
import com.sp.syncPoint.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("No authenticated user found for this request");
        }
        return resolveUser(jwt);
    }

    public User resolveUser(Jwt jwt) {
        String internalAuthId = jwt.getSubject();
        return userRepository.findByInternalAuthId(internalAuthId)
                .orElseThrow(() -> new UserNotFoundException("No local account found for the authenticated user"));
    }

    public String getCurrentPhoneNumber() {
        return getCurrentUser().getPhoneNumber();
    }

    public void assertOwnsPhoneNumber(String phoneNumber) {
        if (!ownsPhoneNumber(phoneNumber)) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }

    public boolean ownsPhoneNumber(String phoneNumber) {
        String currentPhoneNumber = getCurrentPhoneNumber();
        return currentPhoneNumber != null && currentPhoneNumber.equals(phoneNumber);
    }
}