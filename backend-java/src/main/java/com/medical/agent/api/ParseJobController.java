package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parse-jobs")
public class ParseJobController {
  private final PersistenceService persistenceService;
  private final ParseRequestPublisher parseRequestPublisher;

  public ParseJobController(PersistenceService persistenceService, ParseRequestPublisher parseRequestPublisher) {
    this.persistenceService = persistenceService;
    this.parseRequestPublisher = parseRequestPublisher;
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> body,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    UUID recordId = UUID.fromString(String.valueOf(body.get("recordId")));
    UUID jobId = persistenceService.createOrReuseParseJob(recordId, idempotencyKey);
    @SuppressWarnings("unchecked")
    List<String> rawAssetIds = (List<String>) body.getOrDefault("assetIds", List.of());
    List<UUID> assetIds = rawAssetIds.stream().map(UUID::fromString).toList();
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

    return Map.of(
        "code", "OK",
        "message", "queued",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("jobId", jobId.toString(), "status", "QUEUED"));
  }

  @GetMapping("/{jobId}")
  public Map<String, Object> status(@PathVariable("jobId") String jobId) {
    Map<String, Object> data = persistenceService.getAndAdvanceParseJob(UUID.fromString(jobId));
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }
}
