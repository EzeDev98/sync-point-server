
package com.sp.syncPoint.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sp.syncPoint.common.response.BaseResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        // TEMP DEBUG: logs the real reason (bad signature / wrong issuer /
        // expired / malformed header) instead of a bare 401. Remove once
        // the internal-auth cutover is verified working.
        log.warn("401 on {} {} — {}: {}", request.getMethod(), request.getRequestURI(),
                authException.getClass().getSimpleName(), authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        BaseResponse errorResponse = new BaseResponse();
        errorResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED);
        errorResponse.setDescription("UNAUTHORIZED");

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getWriter(), errorResponse);
    }
}
