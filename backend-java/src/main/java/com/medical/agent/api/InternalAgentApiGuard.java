package com.medical.agent.api;

import jakarta.servlet.http.HttpServletRequest;
import com.medical.agent.infrastructure.security.RequestScopeHolder;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalAgentApiGuard {
  private final String apiKey;
  private final String apiKeyHeader;
  private final boolean securityEnabled;

  public InternalAgentApiGuard(
      @Value("${app.agent.internal-api-key:}") String apiKey,
      @Value("${app.agent.internal-api-key-header:X-Internal-Api-Key}") String apiKeyHeader,
      @Value("${app.security.enabled:true}") boolean securityEnabled) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.apiKeyHeader = apiKeyHeader == null || apiKeyHeader.isBlank()
        ? "X-Internal-Api-Key"
        : apiKeyHeader.trim();
    this.securityEnabled = securityEnabled;
  }

  public void verify(HttpServletRequest request) {
    if (apiKey.isBlank()) {
      if (securityEnabled) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "internal api key is not configured");
      }
      return;
    }
    String provided = request.getHeader(apiKeyHeader);
    if (!apiKey.equals(provided == null ? "" : provided.trim())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalid");
    }
  }

  public void verifyAndApplyScope(HttpServletRequest request) {
    verify(request);
    if (apiKey.isBlank() && !securityEnabled) {
      return;
    }
    try {
      RequestScopeHolder.setTenantId(requiredUuid(request, "X-Agent-Tenant-Id"));
      RequestScopeHolder.setUserId(requiredUuid(request, "X-Agent-User-Id"));
      RequestScopeHolder.setPatientId(requiredUuid(request, "X-Agent-Patient-Id"));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal agent scope invalid");
    }
  }

  private UUID requiredUuid(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " required");
    }
    return UUID.fromString(value.trim());
  }
}
