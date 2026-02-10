package com.medical.agent.application.repository;

import java.util.Map;
import java.util.UUID;

public interface GeneratedOutputRepository {
  int createGeneratedOutput(UUID recordId, String type, String content);

  int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson);

  Map<String, Object> fetchLatestGeneratedOutput(UUID recordId, String type);
}
