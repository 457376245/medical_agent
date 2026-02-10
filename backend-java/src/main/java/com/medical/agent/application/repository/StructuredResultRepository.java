package com.medical.agent.application.repository;

import java.util.Map;
import java.util.UUID;

public interface StructuredResultRepository {
  Map<String, Object> patchStructuredResult(UUID recordId, int revision, String payloadJson);

  Map<String, Object> fetchRecordAnalysisContext(UUID recordId);
}
