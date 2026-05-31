package com.medical.agent.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalAgentApiGuard {
  private final String apiKey;
  private final String apiKeyHeader;

  public InternalAgentApiGuard(
      @Value("${app.agent.internal-api-key:}") String apiKey,
      @Value("${app.agent.internal-api-key-header:X-Internal-Api-Key}") String apiKeyHeader) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.apiKeyHeader = apiKeyHeader == null || apiKeyHeader.isBlank()
        ? "X-Internal-Api-Key"
        : apiKeyHeader.trim();
  }

  public void verify(HttpServletRequest request) {
    if (apiKey.isBlank()) {
      return;
    }
    String provided = request.getHeader(apiKeyHeader);
    if (!apiKey.equals(provided == null ? "" : provided.trim())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalid");
    }
  }
}
