package com.medical.agent.api;

import com.medical.agent.application.service.ReportCategoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.NameRequest;
import com.medical.agent.domain.dto.response.ReportCategoryCreateResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryDeleteResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryListResponseData;
import com.medical.agent.domain.dto.response.ReportCategoryRefResponseData;
import com.medical.agent.domain.vo.ReportCategorySummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "报告分类", description = "报告分类管理接口")
public class ReportCategoryController {
  private final ReportCategoryService reportCategoryService;

  public ReportCategoryController(ReportCategoryService reportCategoryService) {
    this.reportCategoryService = reportCategoryService;
  }

  @GetMapping
  @Operation(summary = "查询报告分类列表", description = "返回当前用户下的报告分类")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_401\",\"data\":{\"categories\":[{\"id\":\"70f4026d-d53d-4f85-9ec1-7d2205da7c15\",\"name\":\"检验报告\",\"updatedAt\":\"2026-02-24T10:00:00\",\"recordCount\":12}]}}")))
  })
  public ApiResponse<ReportCategoryListResponseData> list() {
    List<ReportCategorySummary> categories = reportCategoryService.listCategories();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new ReportCategoryListResponseData(categories));
  }

  @PostMapping
  @Operation(
      summary = "创建报告分类",
      description = "按名称创建报告分类，存在同名则返回已有ID",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "报告分类创建参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"name\":\"检验报告\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "创建成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_402\",\"data\":{\"reportCategoryId\":\"70f4026d-d53d-4f85-9ec1-7d2205da7c15\",\"name\":\"检验报告\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "参数错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_REQUEST\",\"message\":\"Report category name is required\",\"requestId\":\"req_20260224_403\",\"data\":{\"reportCategoryId\":null,\"name\":\"\"}}")))
  })
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
  @Operation(summary = "删除报告分类", description = "删除指定分类，存在关联记录时返回冲突")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "删除成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"deleted\",\"requestId\":\"req_20260224_404\",\"data\":{\"reportCategoryId\":\"70f4026d-d53d-4f85-9ec1-7d2205da7c15\",\"deleted\":true,\"reason\":\"DELETED\",\"linkedRecordCount\":0}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "分类ID格式错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_CATEGORY_ID\",\"message\":\"reportCategoryId is invalid\",\"requestId\":\"req_20260224_405\",\"data\":{\"reportCategoryId\":\"abc\",\"deleted\":false}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "409",
          description = "存在关联记录",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"CONFLICT\",\"message\":\"report category has associated records\",\"requestId\":\"req_20260224_406\",\"data\":{\"reportCategoryId\":\"70f4026d-d53d-4f85-9ec1-7d2205da7c15\",\"deleted\":false,\"reason\":\"HAS_ASSOCIATED_RECORDS\",\"linkedRecordCount\":12}}")))
  })
  public ResponseEntity<ApiResponse<?>> delete(
      @Parameter(description = "报告分类ID（UUID）", example = "70f4026d-d53d-4f85-9ec1-7d2205da7c15")
      @PathVariable("reportCategoryId") String reportCategoryId) {
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
