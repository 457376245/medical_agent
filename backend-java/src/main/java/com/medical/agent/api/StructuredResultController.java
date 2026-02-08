package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
public class StructuredResultController {
  private final PersistenceService persistenceService;

  public StructuredResultController(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @PatchMapping("/{recordId}/structured-result")
  public Map<String, Object> patch(@PathVariable("recordId") String recordId, @RequestBody Map<String, Object> body) {
    int version = Integer.parseInt(String.valueOf(body.getOrDefault("version", 1)));
    String payload = String.valueOf(body.getOrDefault("payload", "{}"));
    Map<String, Object> result = persistenceService.patchStructuredResult(UUID.fromString(recordId), version, payload);
    return Map.of(
        "code", "OK",
        "message", "updated",
        "requestId", RequestIdUtil.newRequestId(),
        "data", result);
  }
}
