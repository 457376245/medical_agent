package com.medical.agent.application.service;

import com.medical.agent.application.PersistenceService;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ParseJobService {
  private final PersistenceService persistenceService;
  private final ParseRequestPublisher parseRequestPublisher;

  public ParseJobService(PersistenceService persistenceService, ParseRequestPublisher parseRequestPublisher) {
    this.persistenceService = persistenceService;
    this.parseRequestPublisher = parseRequestPublisher;
  }

  public Map<String, Object> create(Map<String, Object> body, String idempotencyKey) {
    UUID recordId = UUID.fromString(String.valueOf(body.get("recordId")));
    UUID jobId = persistenceService.createOrReuseParseJob(recordId, idempotencyKey);
    @SuppressWarnings("unchecked")
    List<String> rawAssetIds = (List<String>) body.getOrDefault("assetIds", List.of());
    List<UUID> assetIds = rawAssetIds.stream().map(UUID::fromString).toList();
    persistenceService.bindParseJobAssets(jobId, assetIds);
    List<Map<String, Object>> assetRefs = persistenceService.listAssetRefs(assetIds);
    Map<String, String> context = persistenceService.parseJobContext(jobId);

    parseRequestPublisher.publish(Map.of(
        "jobId", jobId.toString(),
        "tenantId", context.get("tenantId"),
        "userId", context.get("userId"),
        "assetRefs", assetRefs,
        "traceId", UUID.randomUUID().toString().replace("-", ""),
        "schemaVersion", "v1",
        "idempotencyKey", idempotencyKey));

    return Map.of("jobId", jobId.toString(), "status", "QUEUED");
  }
}
