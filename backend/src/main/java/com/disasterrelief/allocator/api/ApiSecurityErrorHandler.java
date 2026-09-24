package com.disasterrelief.allocator.api;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
        private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "AUTHORIZATION_DENIED",
                "You are not authorized to access this resource");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), code, message, request.getRequestURI(), Map.of()));
    }
}