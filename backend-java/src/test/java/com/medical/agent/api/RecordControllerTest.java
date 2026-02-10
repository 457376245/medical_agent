package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.ReportAnalysisService;
import com.medical.agent.application.service.RecordService;
import java.util.Map;
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
    when(recordService.fetchRecord(recordId)).thenReturn(Map.of(
        "recordId", recordId.toString(),
        "summary", "test summary",
        "parseStatus", "SUCCESS",
        "structuredResult", Map.of()));

    ResponseEntity<Map<String, Object>> response = controller.getRecord(recordId.toString());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().get("code"));
    assertEquals("PARSED_RESULT", data.get("defaultView"));
    assertNotNull(response.getBody().get("requestId"));
  }

  @Test
  void getRecordReturnsNotFoundWhenMissing() {
    UUID recordId = UUID.randomUUID();
    when(recordService.fetchRecord(recordId)).thenThrow(new IllegalArgumentException("record not found"));

    ResponseEntity<Map<String, Object>> response = controller.getRecord(recordId.toString());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", response.getBody().get("code"));
  }

  @Test
  void getRecordAnalysisReturnsBadRequestWhenRecordIdInvalid() {
    ResponseEntity<Map<String, Object>> response = controller.getRecordAnalysis("invalid-id");

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_RECORD_ID", response.getBody().get("code"));
  }

  @Test
  void getRecordTrendNormalizesLimitToSix() {
    UUID recordId = UUID.randomUUID();
    when(recordService.fetchTrend(recordId, 6)).thenReturn(Map.of("snapshots", java.util.List.of()));

    ResponseEntity<Map<String, Object>> response = controller.getRecordTrend(recordId.toString(), 99);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(recordService).fetchTrend(recordId, 6);
  }

  @Test
  void deleteRecordReturnsNotFoundWhenRecordMissing() {
    UUID recordId = UUID.randomUUID();
    when(recordService.deleteRecord(recordId)).thenReturn(false);

    ResponseEntity<Map<String, Object>> response = controller.deleteRecord(recordId.toString());

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("NOT_FOUND", response.getBody().get("code"));
  }

  @Test
  void updateRecordSourceTypeReturnsBadRequestWhenSourceTypeMissing() {
    UUID recordId = UUID.randomUUID();

    ResponseEntity<Map<String, Object>> response = controller.updateRecordSourceType(
        recordId.toString(),
        Map.of("sourceType", " "));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_REQUEST", response.getBody().get("code"));
  }

  @Test
  void updateRecordSourceTypeReturnsSuccessWhenUpdated() {
    UUID recordId = UUID.randomUUID();
    when(recordService.updateSourceType(eq(recordId), eq("LAB"))).thenReturn(Map.of(
        "updated", true,
        "sourceType", "LAB",
        "title", "高血压-检验报告-2026-02-10",
        "recordDate", "2026-02-10",
        "diseaseName", "高血压"));

    ResponseEntity<Map<String, Object>> response = controller.updateRecordSourceType(
        recordId.toString(),
        Map.of("sourceType", "LAB"));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("OK", response.getBody().get("code"));
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertTrue(Boolean.TRUE.equals(data.get("updated")));
    verify(recordService).updateSourceType(any(UUID.class), eq("LAB"));
  }
}
