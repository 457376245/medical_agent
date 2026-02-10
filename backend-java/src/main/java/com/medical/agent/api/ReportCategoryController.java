package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-categories")
public class ReportCategoryController {
  private final PersistenceService persistenceService;

  public ReportCategoryController(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @GetMapping
  public Map<String, Object> list() {
    List<Map<String, Object>> categories = persistenceService.listReportCategories();
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("categories", categories));
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    UUID categoryId;
    try {
      categoryId = persistenceService.createReportCategory(name);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_REQUEST",
          "message", error.getMessage(),
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("name", name)));
    }
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("reportCategoryId", categoryId.toString(), "name", name)));
  }

  @DeleteMapping("/{reportCategoryId}")
  public ResponseEntity<Map<String, Object>> delete(
      @PathVariable("reportCategoryId") String reportCategoryId,
      @RequestParam(value = "onlyIfEmpty", defaultValue = "true") boolean onlyIfEmpty) {
    UUID categoryId;
    try {
      categoryId = UUID.fromString(reportCategoryId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
          "code", "INVALID_CATEGORY_ID",
          "message", "reportCategoryId is invalid",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("reportCategoryId", reportCategoryId, "deleted", false)));
    }

    if (!persistenceService.reportCategoryExists(categoryId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "report category not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("reportCategoryId", reportCategoryId, "deleted", false)));
    }

    int linkedCount = persistenceService.countRecordsByReportCategory(categoryId);
    if (onlyIfEmpty && linkedCount > 0) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
          "code", "CONFLICT",
          "message", "report category has associated records",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of(
              "reportCategoryId", reportCategoryId,
              "deleted", false,
              "reason", "HAS_ASSOCIATED_RECORDS",
              "linkedRecordCount", linkedCount)));
    }

    boolean deleted = persistenceService.deleteReportCategoryIfEmpty(categoryId);
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
          "code", "DELETE_FAILED",
          "message", "failed to delete report category",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of(
              "reportCategoryId", reportCategoryId,
              "deleted", false,
              "reason", "DELETE_FAILED",
              "linkedRecordCount", linkedCount)));
    }
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "deleted",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of(
            "reportCategoryId", reportCategoryId,
            "deleted", true,
            "reason", "DELETED",
            "linkedRecordCount", 0)));
  }
}
