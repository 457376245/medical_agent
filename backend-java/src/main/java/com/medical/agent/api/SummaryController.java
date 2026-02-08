package com.medical.agent.api;

import com.medical.agent.application.GeneratedOutputService;
import com.medical.agent.application.PersistenceService;
import com.medical.agent.infrastructure.mq.GenerateRequestPublisher;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
public class SummaryController {
  private final GeneratedOutputService generatedOutputService;
  private final PersistenceService persistenceService;
  private final GenerateRequestPublisher generateRequestPublisher;

  public SummaryController(
      GeneratedOutputService generatedOutputService,
      PersistenceService persistenceService,
      GenerateRequestPublisher generateRequestPublisher) {
    this.generatedOutputService = generatedOutputService;
    this.persistenceService = persistenceService;
    this.generateRequestPublisher = generateRequestPublisher;
  }

  @PostMapping("/{recordId}/generate-summary")
  public Map<String, Object> generateSummary(@PathVariable("recordId") String recordId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Map<String, Object> task = persistenceService.createGenerateTask(UUID.fromString(recordId), "SUMMARY", idempotencyKey);
    generateRequestPublisher.publish(Map.of(
        "taskId", task.get("taskId"),
        "tenantId", task.get("tenantId"),
        "recordId", task.get("recordId"),
        "type", "SUMMARY",
        "traceId", task.get("traceId"),
        "schemaVersion", "v1",
        "idempotencyKey", idempotencyKey));
    generatedOutputService.createNextVersion(recordId, "SUMMARY", "Summary draft generated from record context.");
    return Map.of(
        "code", "OK",
        "message", "queued",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("taskId", task.get("taskId"), "status", "QUEUED"));
  }
}
