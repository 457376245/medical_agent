package com.medical.agent.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.domain.vo.UltrasoundFollowUpResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class UltrasoundFollowUpAnalyzerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UltrasoundFollowUpAnalyzer analyzer = new UltrasoundFollowUpAnalyzer();

  @Test
  void returnsSingleReportModeWhenNoHistory() throws Exception {
    UltrasoundFollowUpResult result = analyzer.analyze("current", List.of(
        snapshot("current", "甲状腺彩超", "2026-05-08", payload("超声提示", "甲状腺结节，建议随访复查"))));

    assertEquals("SINGLE_REPORT", result.mode());
    assertEquals("NO_HISTORY", result.changeStatus());
    assertEquals("RECHECK_SOON", result.actionLevel());
    assertEquals(1, result.currentEvidence().size());
  }

  @Test
  void comparesCurrentWithPreviousUltrasoundReport() throws Exception {
    UltrasoundFollowUpResult result = analyzer.analyze("current", List.of(
        snapshot("current", "甲状腺彩超", "2026-05-08", payload("超声提示", "甲状腺结节较前增大，TI-RADS 4类，建议进一步检查")),
        snapshot("previous", "甲状腺彩超", "2026-02-08", payload("超声提示", "甲状腺结节，TI-RADS 3类，建议随访"))));

    assertEquals("FOLLOW_UP", result.mode());
    assertEquals("WORSENED", result.changeStatus());
    assertEquals("SEEK_CARE_SOON", result.actionLevel());
    assertEquals(2, result.history().size());
    assertEquals("previous", result.previousEvidence().get(0).recordId());
  }

  @Test
  void returnsNullForNonUltrasoundReport() throws Exception {
    UltrasoundFollowUpResult result = analyzer.analyze("current", List.of(
        snapshot("current", "血常规", "2026-05-08", payload("白细胞", "5.2"))));

    assertNull(result);
  }

  private UltrasoundFollowUpAnalyzer.ReportSnapshot snapshot(
      String recordId,
      String title,
      String recordDate,
      JsonNode payload) {
    return new UltrasoundFollowUpAnalyzer.ReportSnapshot(recordId, title, recordDate, "IMAGING", payload);
  }

  private JsonNode payload(String name, String value) throws Exception {
    return objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "%s",
              "value": "%s",
              "confidence": 0.9,
              "evidence": {"sourceFile": "report.pdf", "page": 1, "snippet": "%s"}
            }
          ]
        }
        """.formatted(name, value, value));
  }
}
