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
  public ResponseEntity<ApiResponse<?>> getRecord(@PathVariable("recordId") String recordId) {
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
        "PARSED_RESULT");

    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data));
  }

  @GetMapping("/{recordId}/analysis")
  public ResponseEntity<ApiResponse<?>> getRecordAnalysis(@PathVariable("recordId") String recordId) {
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
  public ResponseEntity<ApiResponse<?>> getRecordTrend(
      @PathVariable("recordId") String recordId,
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
  public ResponseEntity<ApiResponse<RecordDeleteResponseData>> deleteRecord(@PathVariable("recordId") String recordId) {
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
  public ResponseEntity<ApiResponse<RecordSourceTypeUpdateResponseData>> updateRecordSourceType(
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
