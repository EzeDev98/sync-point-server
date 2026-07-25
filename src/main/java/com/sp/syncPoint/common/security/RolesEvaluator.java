package com.sp.syncPoint.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static com.sp.syncPoint.common.constants.GeneralConstants.ADMIN_ROLE;
import static com.sp.syncPoint.common.constants.GeneralConstants.OFFLINE_PAYMENT_APP_ACCESS_ROLE;

@Component("rolesEvaluator")
public class RolesEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RolesEvaluator.class);

    public boolean createTransaction(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(OFFLINE_PAYMENT_APP_ACCESS_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);
    }

    public boolean getTransaction(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(OFFLINE_PAYMENT_APP_ACCESS_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);
    }

    public boolean deleteTransactions(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(ADMIN_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);

    }

    public boolean getAllTransactions(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(ADMIN_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);

    }

    public boolean saveMirrorBalance(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(OFFLINE_PAYMENT_APP_ACCESS_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);

    }

    public boolean fundWallet(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(OFFLINE_PAYMENT_APP_ACCESS_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);

    }

    public boolean authenticatedUser(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(OFFLINE_PAYMENT_APP_ACCESS_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);

    }

    public boolean invalidateSession(Authentication authentication) {
        log.info("Authentication object type: {}", authentication.getClass().getName());
        log.info("Granted authorities for system endpoints: {}", authentication.getAuthorities());

        Set<String> requiredRoles = Set.of(ADMIN_ROLE);
        return authentication.getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .containsAll(requiredRoles);
    }
}