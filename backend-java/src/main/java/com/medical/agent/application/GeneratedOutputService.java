package com.medical.agent.application;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GeneratedOutputService {
  private final PersistenceService persistenceService;

  public GeneratedOutputService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public Map<String, Object> createNextVersion(String recordId, String type, String content) {
    int version = persistenceService.createGeneratedOutput(java.util.UUID.fromString(recordId), type, content);
    return Map.of("recordId", recordId, "type", type, "version", version, "content", content);
  }
}
