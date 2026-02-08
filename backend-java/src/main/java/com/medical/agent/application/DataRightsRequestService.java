package com.medical.agent.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DataRightsRequestService {
  private final PersistenceService persistenceService;

  public DataRightsRequestService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public Map<String, Object> createRequest(String recordId, String type) {
    UUID requestId = persistenceService.createDataRightsRequest(UUID.fromString(recordId), type);
    return Map.of(
        "requestId", requestId.toString(),
        "status", "REQUESTED");
  }

  public Map<String, Object> getStatus(String requestId) {
    Map<String, Object> row = persistenceService.getDataRightsRequest(UUID.fromString(requestId));
    return Map.of(
        "requestId", String.valueOf(row.get("id")),
        "status", String.valueOf(row.get("status")),
        "updatedAt", String.valueOf(row.get("updated_at")));
  }

  public Map<String, Object> exportDownload(String requestId) {
    Map<String, Object> row = persistenceService.getDataRightsRequest(UUID.fromString(requestId));
    if (!"COMPLETED".equals(String.valueOf(row.get("status")))) {
      return Map.of(
          "requestId", requestId,
          "downloadUrl", "",
          "expireAt", Instant.now().toString());
    }
    return Map.of(
        "requestId", requestId,
        "downloadUrl", String.valueOf(row.get("download_url")),
        "expireAt", String.valueOf(row.get("expire_at")));
  }
}
