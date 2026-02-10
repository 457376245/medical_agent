package com.medical.agent.api;

import com.medical.agent.application.ReportAnalysisService;
import com.medical.agent.application.service.RecordService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records")
public class RecordController {
  private final RecordService recordService;
  private final ReportAnalysisService reportAnalysisService;

  public RecordController(RecordService recordService, ReportAnalysisService reportAnalysisService) {
    this.recordService = recordService;
    this.reportAnalysisService = reportAnalysisService;
  }

  @GetMapping("/{recordId}")
  public ResponseEntity<Map<String, Object>> getRecord(@PathVariable("recordId") String recordId) {
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

    Map<String, Object> record;
    try {
      record = new HashMap<>(recordService.fetchRecord(recordUuid));
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "record not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId)));
    }
    record.put("defaultView", "PARSED_RESULT");
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", record));
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
    } catch (ReportAnalysisService.AnalysisNotReadyException error) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
          "code", "ANALYSIS_NOT_READY",
          "message", "analysis requires successful parse result with non-empty fields",
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

  @GetMapping("/{recordId}/trend")
  public ResponseEntity<Map<String, Object>> getRecordTrend(
      @PathVariable("recordId") String recordId,
      @RequestParam(name = "limit", required = false) Integer limit) {
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

    int normalizedLimit = limit == null ? 6 : Math.max(1, Math.min(limit, 6));
    Map<String, Object> trendData;
    try {
      trendData = recordService.fetchTrend(recordUuid, normalizedLimit);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "record not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId)));
    }

    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", trendData));
  }

  @DeleteMapping("/{recordId}")
  public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable("recordId") String recordId) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_RECORD_ID",
          "message", "recordId is invalid",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("recordId", recordId, "deleted", false)));
    }

    boolean deleted = recordService.deleteRecord(recordUuid);
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
      updated = recordService.updateSourceType(recordUuid, sourceType);
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
