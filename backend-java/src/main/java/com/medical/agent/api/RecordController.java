package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {
  private final PersistenceService persistenceService;

  public RecordController(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @GetMapping("/{recordId}")
  public Map<String, Object> getRecord(@PathVariable("recordId") String recordId) {
    Map<String, Object> record = new HashMap<>(persistenceService.fetchRecord(UUID.fromString(recordId)));
    record.put("defaultView", "PARSED_RESULT");
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", record);
  }
}
