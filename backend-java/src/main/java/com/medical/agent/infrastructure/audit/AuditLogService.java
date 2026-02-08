package com.medical.agent.infrastructure.audit;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
  private static final Logger LOGGER = LoggerFactory.getLogger("AUDIT");

  public void logEvent(String action, String resourceType, String resourceId, String outcome, String requestId) {
    Map<String, Object> event = Map.of(
        "ts", Instant.now().toString(),
        "action", action,
        "resourceType", resourceType,
        "resourceId", resourceId,
        "outcome", outcome,
        "requestId", requestId);
    LOGGER.info("{}", event);
  }
}
