package com.medical.agent.application.repository;

import java.util.Map;
import java.util.UUID;

public interface DataRightsRepository {
  UUID createDataRightsRequest(UUID recordId, String requestType);

  Map<String, Object> getDataRightsRequest(UUID requestId);
}
