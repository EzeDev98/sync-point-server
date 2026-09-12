package com.sp.syncPoint.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final InternalAuthJwtRolesConverter jwtAuthenticationConverter;

    private final CustomAccessDenied customAccessDenied;

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Value("${internal-auth.issuer-url}")
    private String tokenIssuerUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.POST,"/api/v1/register-user").permitAll()

                        .requestMatchers(HttpMethod.POST,"/api/v1/phone-number/verify").permitAll()

                        .requestMatchers(HttpMethod.POST,"/api/v1/phone-number/resend-verification").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/wallet/login").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh-token").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/wallet/session/active").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/request").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/verify-otp").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/complete").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/keep-alive").permitAll()
                        // Spring Boot forwards any unhandled 404/500 to /error, and that
                        // forward goes through this filter chain as an ERROR dispatch. If
                        // /error isn't permitted it falls to anyRequest().authenticated(),
                        // gets denied, and — since no formLogin/httpBasic is configured,
                        // making Http403ForbiddenEntryPoint the default — comes back as a
                        // 403. The real 404 or 500 is never seen. Every "mysterious 403 on
                        // a path I definitely permitted" in a stateless API traces back to
                        // this line being missing.
                        .requestMatchers("/error").permitAll()
                        // Render's health check hits this path unauthenticated. Only
                        // /actuator/health itself is exposed — no other /actuator/**
                        // endpoint is permitted, so nothing else in Actuator (env,
                        // beans, etc.) is reachable without a token.
                        .requestMatchers("/actuator/health").permitAll()

                        // Danger-zone data wipe (test environments). Opened here so it can be
                        // called without a Keycloak login; it is guarded instead by a required
                        // X-Reset-Secret plus a confirmation phrase, and is inert unless
                        // app.reset.secret is configured. See AdminResetController.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/reset/wipe").permitAll()

                        .requestMatchers("/public/**").permitAll()

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/configuration/**",
                                "/webjars/**",
                                "/api-docs/**"
                        ).permitAll()

                        .requestMatchers(maliciousProbesMatcher()).denyAll()

                        .anyRequest().authenticated())

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDenied))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    private RequestMatcher maliciousProbesMatcher() {
        List<String> blockedExtensions = List.of(".php", ".asp", ".aspx", ".env", ".cgi", ".sh", ".bat");
        return request -> {
            String path = request.getRequestURI().toLowerCase();
            return blockedExtensions.stream().anyMatch(path::endsWith);
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of(
                "https://sync-point-client.onrender.com",
                "https://*.onrender.com",
                "http://localhost:*"
        ));

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(List.of(
                "Content-Disposition",
                "X-Total-Count"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtAuthenticationConverter);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(tokenIssuerUrl);
    }

    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }
}