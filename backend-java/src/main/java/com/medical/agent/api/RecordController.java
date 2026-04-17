package com.medical.agent.api;

import com.medical.agent.application.ReportAnalysisService;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.UpdateRecordSourceTypeRequest;
import com.medical.agent.domain.dto.response.RecordDeleteResponseData;
import com.medical.agent.domain.dto.response.RecordRefResponseData;
import com.medical.agent.domain.dto.response.RecordSourceTypeUpdateResponseData;
import com.medical.agent.domain.dto.response.RecordViewResponseData;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportAnalysisResult;
import com.medical.agent.domain.vo.StructuredResultData;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "记录", description = "病历记录查询与管理接口")
public class RecordController {
  private final RecordService recordService;
  private final ReportAnalysisService reportAnalysisService;

  public RecordController(RecordService recordService, ReportAnalysisService reportAnalysisService) {
    this.recordService = recordService;
    this.reportAnalysisService = reportAnalysisService;
  }

  @GetMapping("/{recordId}")
  @Operation(summary = "查询记录详情", description = "按记录ID查询详情与结构化结果")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_501\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"summary\":\"血糖略高，建议复查\",\"parseStatus\":\"SUCCESS\",\"structuredResult\":{\"schemaVersion\":\"v1\",\"revision\":1,\"payload\":{\"fields\":[{\"name\":\"空腹血糖\",\"value\":\"6.3\",\"unit\":\"mmol/L\"}]}},\"defaultView\":\"PARSED_RESULT\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "记录ID格式错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_RECORD_ID\",\"message\":\"recordId is invalid\",\"requestId\":\"req_20260224_502\",\"data\":{\"recordId\":\"abc\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "404",
          description = "记录不存在",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"NOT_FOUND\",\"message\":\"record not found\",\"requestId\":\"req_20260224_503\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f28999\"}}")))
  })
  public ResponseEntity<ApiResponse<?>> getRecord(
      @Parameter(description = "记录ID（UUID）", example = "07abefef-a580-4b6a-b15f-fd54e8f282f4")
      @PathVariable("recordId") String recordId) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_RECORD_ID",
          "recordId is invalid",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    RecordDetail record;
    try {
      record = recordService.fetchRecord(recordUuid);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "record not found",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    StructuredResultData structured = record.structuredResult();
    RecordViewResponseData.StructuredResultView structuredView = new RecordViewResponseData.StructuredResultView(
        structured.schemaVersion(),
        structured.revision(),
        structured.payload());
    RecordViewResponseData data = new RecordViewResponseData(
        record.recordId(),
        record.summary(),
        record.parseStatus(),
        structuredView,
        record.combinationAnalysis(),
        "PARSED_RESULT");

    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data));
  }

  @GetMapping("/{recordId}/analysis")
  @Operation(summary = "查询记录分析", description = "返回记录分析，若无缓存会触发生成")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_504\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"content\":\"该报告提示血糖偏高，建议控制饮食并复查。\",\"cached\":true,\"version\":2}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "409",
          description = "解析结果尚未就绪",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"ANALYSIS_NOT_READY\",\"message\":\"analysis requires successful parse result with non-empty fields\",\"requestId\":\"req_20260224_505\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "502",
          description = "分析服务失败",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"ANALYSIS_PROVIDER_FAILED\",\"message\":\"report analysis generation failed\",\"requestId\":\"req_20260224_506\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\"}}")))
  })
  public ResponseEntity<ApiResponse<?>> getRecordAnalysis(
      @Parameter(description = "记录ID（UUID）", example = "07abefef-a580-4b6a-b15f-fd54e8f282f4")
      @PathVariable("recordId") String recordId) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_RECORD_ID",
          "recordId is invalid",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    ReportAnalysisResult analysis;
    try {
      analysis = reportAnalysisService.getOrGenerate(recordUuid);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "record not found",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    } catch (ReportAnalysisService.AnalysisNotReadyException error) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(
          "ANALYSIS_NOT_READY",
          "analysis requires successful parse result with non-empty fields",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    } catch (IllegalStateException error) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(
          "ANALYSIS_PROVIDER_FAILED",
          "report analysis generation failed",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), analysis));
  }

  @GetMapping("/{recordId}/trend")
  @Operation(summary = "查询记录趋势", description = "根据同病种与同来源记录生成时间趋势")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_507\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"sourceType\":\"LAB\",\"diseaseProfileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"limit\":3,\"snapshots\":[{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"recordDate\":\"2026-02-24\",\"title\":\"门诊检验\",\"sourceType\":\"LAB\",\"fields\":[{\"name\":\"空腹血糖\",\"value\":\"6.3\",\"unit\":\"mmol/L\",\"referenceRange\":\"3.9-6.1\"}]}]}}")))
  })
  public ResponseEntity<ApiResponse<?>> getRecordTrend(
      @Parameter(description = "记录ID（UUID）", example = "07abefef-a580-4b6a-b15f-fd54e8f282f4")
      @PathVariable("recordId") String recordId,
      @Parameter(description = "趋势点数量，范围 1~6，默认 6", example = "3")
      @RequestParam(name = "limit", required = false) Integer limit) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_RECORD_ID",
          "recordId is invalid",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    int normalizedLimit = limit == null ? 6 : Math.max(1, Math.min(limit, 6));
    RecordTrendData trendData;
    try {
      trendData = recordService.fetchTrend(recordUuid, normalizedLimit);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "record not found",
          RequestIdUtil.newRequestId(),
          new RecordRefResponseData(recordId)));
    }

    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), trendData));
  }

  @DeleteMapping("/{recordId}")
  @Operation(summary = "删除记录", description = "删除记录及其关联数据")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "删除成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"deleted\",\"requestId\":\"req_20260224_508\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"deleted\":true}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "404",
          description = "记录不存在",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"NOT_FOUND\",\"message\":\"record not found\",\"requestId\":\"req_20260224_509\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f28999\",\"deleted\":false}}")))
  })
  public ResponseEntity<ApiResponse<RecordDeleteResponseData>> deleteRecord(
      @Parameter(description = "记录ID（UUID）", example = "07abefef-a580-4b6a-b15f-fd54e8f282f4")
      @PathVariable("recordId") String recordId) {
    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_RECORD_ID",
          "recordId is invalid",
          RequestIdUtil.newRequestId(),
          new RecordDeleteResponseData(recordId, false)));
    }

    boolean deleted = recordService.deleteRecord(recordUuid);
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "record not found",
          RequestIdUtil.newRequestId(),
          new RecordDeleteResponseData(recordId, false)));
    }
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "deleted",
        RequestIdUtil.newRequestId(),
        new RecordDeleteResponseData(recordId, true)));
  }

  @PatchMapping("/{recordId}/source-type")
  @Operation(
      summary = "更新记录来源类型",
      description = "更新来源类型并自动重命名记录标题",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "来源类型更新参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"sourceType\":\"LAB\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "更新成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"updated\",\"requestId\":\"req_20260224_510\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"updated\":true,\"sourceType\":\"LAB\",\"title\":\"高血压-检验报告-2026-02-24\",\"recordDate\":\"2026-02-24\",\"diseaseName\":\"高血压\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "请求参数错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_SOURCE_TYPE\",\"message\":\"sourceType is required\",\"requestId\":\"req_20260224_511\",\"data\":{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"updated\":false,\"sourceType\":null,\"title\":null,\"recordDate\":null,\"diseaseName\":null}}")))
  })
  public ResponseEntity<ApiResponse<RecordSourceTypeUpdateResponseData>> updateRecordSourceType(
      @Parameter(description = "记录ID（UUID）", example = "07abefef-a580-4b6a-b15f-fd54e8f282f4")
      @PathVariable("recordId") String recordId,
      @RequestBody UpdateRecordSourceTypeRequest request) {
    String sourceType = request == null || request.sourceType() == null ? "" : request.sourceType().trim();
    if (sourceType.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_REQUEST",
          "sourceType is required",
          RequestIdUtil.newRequestId(),
          new RecordSourceTypeUpdateResponseData(recordId, false, null, null, null, null)));
    }

    UUID recordUuid;
    try {
      recordUuid = UUID.fromString(recordId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_RECORD_ID",
          "recordId is invalid",
          RequestIdUtil.newRequestId(),
          new RecordSourceTypeUpdateResponseData(recordId, false, null, null, null, null)));
    }

    UpdateRecordSourceTypeResult updated;
    try {
      updated = recordService.updateSourceType(recordUuid, sourceType);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_SOURCE_TYPE",
          error.getMessage(),
          RequestIdUtil.newRequestId(),
          new RecordSourceTypeUpdateResponseData(recordId, false, null, null, null, null)));
    }
    if (!updated.updated()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "record not found",
          RequestIdUtil.newRequestId(),
          new RecordSourceTypeUpdateResponseData(recordId, false, null, null, null, null)));
    }

    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "updated",
        RequestIdUtil.newRequestId(),
        new RecordSourceTypeUpdateResponseData(
            recordId,
            true,
            updated.sourceType(),
            updated.title(),
            updated.recordDate(),
            updated.diseaseName())));
  }
}
