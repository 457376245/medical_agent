package com.medical.agent.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
  private static final Set<String> IDEMPOTENT_PATHS = Set.of(
      "/api/ingestions/parse-jobs");

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("POST".equalsIgnoreCase(request.getMethod()) && requiresIdempotency(request.getRequestURI())) {
      String key = request.getHeader("Idempotency-Key");
      if (key == null || key.isBlank()) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return false;
      }
    }
    return true;
  }

  private boolean requiresIdempotency(String uri) {
    return IDEMPOTENT_PATHS.stream().anyMatch(uri::contains);
  }
}
