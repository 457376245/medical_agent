package com.medical.agent.api;

import com.medical.agent.application.service.ReportCategoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.NameRequest;
import com.medical.agent.domain.dto.response.ReportCategoryCreateResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryDeleteResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryListResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryRefResponseData;
import com.medical.agent.domain.vo.ReportCategorySummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-categories")
public class ReportCategoryController {
  private final ReportCategoryService reportCategoryService;

  public ReportCategoryController(ReportCategoryService reportCategoryService) {
    this.reportCategoryService = reportCategoryService;
  }

  @GetMapping
  public ApiResponse<ReportCategoryListResponseData> list() {
    List<ReportCategorySummary> categories = reportCategoryService.listCategories();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new ReportCategoryListResponseData(categories));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(@RequestBody NameRequest request) {
    String name = request == null || request.name() == null ? "" : request.name().trim();
    UUID categoryId;
    try {
      categoryId = reportCategoryService.createCategory(name);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_REQUEST",
          error.getMessage(),
          RequestIdUtil.newRequestId(),
          new ReportCategoryCreateResponseData(null, name)));
    }
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new ReportCategoryCreateResponseData(categoryId.toString(), name)));
  }

  @DeleteMapping("/{reportCategoryId}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable("reportCategoryId") String reportCategoryId) {
    UUID categoryId;
    try {
      categoryId = UUID.fromString(reportCategoryId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_CATEGORY_ID",
          "reportCategoryId is invalid",
          RequestIdUtil.newRequestId(),
          new ReportCategoryRefResponseData(reportCategoryId, false)));
    }

    if (!reportCategoryService.categoryExists(categoryId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "report category not found",
          RequestIdUtil.newRequestId(),
          new ReportCategoryRefResponseData(reportCategoryId, false)));
    }

    int linkedCount = reportCategoryService.countRecords(categoryId);
    if (linkedCount > 0) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(
          "CONFLICT",
          "report category has associated records",
          RequestIdUtil.newRequestId(),
          new ReportCategoryDeleteResponseData(
              reportCategoryId,
              false,
              "HAS_ASSOCIATED_RECORDS",
              linkedCount)));
    }

    boolean deleted = reportCategoryService.deleteCategoryIfEmpty(categoryId);
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
          "DELETE_FAILED",
          "failed to delete report category",
          RequestIdUtil.newRequestId(),
          new ReportCategoryDeleteResponseData(
              reportCategoryId,
              false,
              "DELETE_FAILED",
              linkedCount)));
    }
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "deleted",
        RequestIdUtil.newRequestId(),
        new ReportCategoryDeleteResponseData(
            reportCategoryId,
            true,
            "DELETED",
            0)));
  }
}
