package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.ReportAnalysisService;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.UpdateRecordSourceTypeRequest;
import com.medical.agent.domain.dto.response.RecordSourceTypeUpdateResponseData;
import com.medical.agent.domain.dto.response.RecordViewResponseData;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.StructuredResultData;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RecordControllerTest {

  @Mock
  private RecordService recordService;

  @Mock
  private ReportAnalysisService reportAnalysisService;

  private RecordController controller;

  @BeforeEach
  void setUp() {
    controller = new RecordController(recordService, reportAnalysisService);
  }

  @Test
  void getRecordReturnsSuccessWithDefaultView() {
    UUID recordId = UUID.randomUUID();
    StructuredResultData structured = new StructuredResultData("v1", 1, new ObjectMapper().createObjectNode());
    when(recordService.fetchRecord(recordId)).thenReturn(
        new RecordDetail(recordId.toString(), "test summary", "SUCCESS", structured, java.util.List.of()));

    ResponseEntity<ApiResponse<?>> response = controller.getRecord(recordId.toString());

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().code());
    RecordViewResponseData data = (RecordViewResponseData) response.getBody().data();
    assertEquals("PARSED_RESULT", data.defaultView());
    assertNotNull(response.getBody().requestId());
  }

  @Test
  void getRecordReturnsNotFoundWhenMissing() {
    UUID recordId = UUID.randomUUID();
    when(recordService.fetchRecord(recordId)).thenThrow(new IllegalArgumentException("record not found"));

    ResponseEntity<ApiResponse<?>> response = controller.getRecord(recordId.toString());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", response.getBody().code());
  }

  @Test
  void getRecordAnalysisReturnsBadRequestWhenRecordIdInvalid() {
    ResponseEntity<ApiResponse<?>> response = controller.getRecordAnalysis("invalid-id");

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_RECORD_ID", response.getBody().code());
  }

  @Test
  void getRecordTrendNormalizesLimitToSix() {
    UUID recordId = UUID.randomUUID();
    when(recordService.fetchTrend(recordId, 6)).thenReturn(
        new RecordTrendData(recordId.toString(), "LAB", "unknown", 6, List.of()));

    ResponseEntity<ApiResponse<?>> response = controller.getRecordTrend(recordId.toString(), 99);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(recordService).fetchTrend(recordId, 6);
  }

  @Test
  void deleteRecordReturnsNotFoundWhenRecordMissing() {
    UUID recordId = UUID.randomUUID();
    when(recordService.deleteRecord(recordId)).thenReturn(false);

    ResponseEntity<?> response = controller.deleteRecord(recordId.toString());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    @SuppressWarnings("unchecked")
    ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertEquals("NOT_FOUND", body.code());
  }

  @Test
  void updateRecordSourceTypeReturnsBadRequestWhenSourceTypeMissing() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<ApiResponse<RecordSourceTypeUpdateResponseData>> response = controller.updateRecordSourceType(
        recordId.toString(),
        new UpdateRecordSourceTypeRequest(" "));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_REQUEST", response.getBody().code());
  }

  @Test
  void updateRecordSourceTypeReturnsSuccessWhenUpdated() {
    UUID recordId = UUID.randomUUID();
    when(recordService.updateSourceType(eq(recordId), eq("LAB"))).thenReturn(
        new UpdateRecordSourceTypeResult(
            true,
            "LAB",
            "高血压-检验报告-2026-02-10",
            "2026-02-10",
            "高血压"));

    ResponseEntity<ApiResponse<RecordSourceTypeUpdateResponseData>> response = controller.updateRecordSourceType(
        recordId.toString(),
        new UpdateRecordSourceTypeRequest("LAB"));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().code());
    RecordSourceTypeUpdateResponseData data = response.getBody().data();
    assertTrue(data.updated());
    verify(recordService).updateSourceType(any(UUID.class), eq("LAB"));
  }
}
