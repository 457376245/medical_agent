package com.medical.agent.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    String userId = jwtUtil.extractUserId(jwt);
                    String tenantId = jwtUtil.extractTenantId(jwt);
                    if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        if (jwtUtil.isTokenValid(jwt)) {
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    List.of());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);

                            RequestScopeHolder.setUserId(UUID.fromString(userId));
                            if (tenantId != null) {
                                RequestScopeHolder.setTenantId(UUID.fromString(tenantId));
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // If token parsing fails, ignore and let the entry point handle the 401
                }
            }

            // Read X-Patient-Id header for patient scoping
            String patientIdHeader = request.getHeader("X-Patient-Id");
            if (patientIdHeader != null && !patientIdHeader.isBlank()) {
                try {
                    RequestScopeHolder.setPatientId(UUID.fromString(patientIdHeader));
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID format, ignore
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            RequestScopeHolder.clear();
        }
    }
}
