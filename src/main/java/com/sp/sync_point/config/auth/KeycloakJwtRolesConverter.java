package com.sp.sync_point.config.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class KeycloakJwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";

    private static final String CLAIM_ROLES = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        Map<String, Collection<String>> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);

        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();

        if (realmAccess != null && !realmAccess.isEmpty()) {
            Collection<String> realmRoles = realmAccess.get(CLAIM_ROLES);
            if (realmRoles != null && !realmRoles.isEmpty()) {
                realmRoles.forEach(r -> {
                    String role = r.toLowerCase(Locale.ROOT);
                    grantedAuthorities.add(new SimpleGrantedAuthority(role));
                });
            }
        }

        return grantedAuthorities;
    }
}