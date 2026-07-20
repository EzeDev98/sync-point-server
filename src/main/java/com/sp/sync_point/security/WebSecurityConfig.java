package com.sp.sync_point.security;

import com.sp.sync_point.config.auth.CustomAccessDenied;
import com.sp.sync_point.config.auth.CustomAuthenticationEntryPoint;
import com.sp.sync_point.config.auth.KeycloakJwtRolesConverter;
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

    private final KeycloakJwtRolesConverter jwtAuthenticationConverter;

    private final CustomAccessDenied customAccessDenied;

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Value("${keycloak.issuer-url}")
    private String tokenIssuerUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.POST,"/api/v1/register-user").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/wallet/login").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh-token").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/wallet/session/active").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/request").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/verify-otp").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/password-reset/complete").permitAll()

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