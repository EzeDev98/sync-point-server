package com.sp.syncPoint.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Reads internal-auth's flat "roles" JWT claim (a plain array of role-name
 * strings), replacing KeycloakJwtRolesConverter's realm_access.roles /
 * resource_access nesting now that Keycloak is gone.
 */
@Slf4j
@Component
public class InternalAuthJwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaim("roles");
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toSet());
    }
}