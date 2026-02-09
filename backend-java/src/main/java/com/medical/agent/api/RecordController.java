package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import com.medical.agent.application.ReportAnalysisService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {
  private final PersistenceService persistenceService;
  private final ReportAnalysisService reportAnalysisService;

  public RecordController(PersistenceService persistenceService, ReportAnalysisService reportAnalysisService) {
    this.persistenceService = persistenceService;
    this.reportAnalysisService = reportAnalysisService;
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

  @GetMapping("/{recordId}/analysis")
  public ResponseEntity<Map<String, Object>> getRecordAnalysis(@PathVariable("recordId") String recordId) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_RECORD_ID",
          "message", "recordId is invalid",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId)));
    }

    Map<String, Object> analysis;
    try {
      analysis = reportAnalysisService.getOrGenerate(recordUuid);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "record not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId)));
    } catch (IllegalStateException error) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
          "code", "ANALYSIS_PROVIDER_FAILED",
          "message", "report analysis generation failed",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId)));
    }

    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", analysis));
  }

  @DeleteMapping("/{recordId}")
  public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable("recordId") String recordId) {
    boolean deleted = persistenceService.deleteRecord(UUID.fromString(recordId));
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "record not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "deleted", false)));
    }
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "deleted",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("recordId", recordId, "deleted", true)));
  }

  @PatchMapping("/{recordId}/source-type")
  public ResponseEntity<Map<String, Object>> updateRecordSourceType(
      @PathVariable("recordId") String recordId,
      @RequestBody Map<String, Object> body) {
    String sourceType = String.valueOf(body.getOrDefault("sourceType", "")).trim();
    if (sourceType.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_REQUEST",
          "message", "sourceType is required",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "updated", false)));
    }

    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_RECORD_ID",
          "message", "recordId is invalid",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "updated", false)));
    }

    Map<String, Object> updated;
    try {
      updated = persistenceService.updateRecordSourceType(recordUuid, sourceType);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_SOURCE_TYPE",
          "message", error.getMessage(),
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "updated", false)));
    }
    if (!Boolean.TRUE.equals(updated.get("updated"))) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "record not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "updated", false)));
    }
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "updated",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of(
            "recordId", recordId,
            "updated", true,
            "sourceType", updated.get("sourceType"),
            "title", updated.get("title"),
            "recordDate", updated.get("recordDate"),
            "diseaseName", updated.get("diseaseName"))));
  }
}
