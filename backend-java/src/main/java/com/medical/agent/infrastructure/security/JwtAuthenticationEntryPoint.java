package com.medical.agent.infrastructure.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.api.RequestIdUtil;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.EmptyData;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        ApiResponse<EmptyData> apiResponse = new ApiResponse<>(
                "UNAUTHORIZED",
                "Full authentication is required to access this resource",
                RequestIdUtil.newRequestId(),
                new EmptyData());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
