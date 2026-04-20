package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.medical.agent.application.AgentDiseaseProfileContextService;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileContextResponse;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileSummary;
import com.medical.agent.domain.dto.response.AgentRecordContextData;
import com.medical.agent.domain.dto.response.AgentRecordContextSummary;
import com.medical.agent.domain.dto.response.AgentTrendSnapshotSummary;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AgentContextControllerTest {

  @Mock
  private AgentDiseaseProfileContextService contextService;

  private AgentContextController controller;

  @BeforeEach
  void setUp() {
    controller = new AgentContextController(contextService);
  }

  @Test
  void getDiseaseProfileContextReturnsAggregatedPayload() {
    AgentDiseaseProfileContextResponse payload = new AgentDiseaseProfileContextResponse(
        new AgentDiseaseProfileSummary("profile-1", "高血压", 2, "2026-03-01"),
        new AgentRecordContextSummary("record-1", "门诊检验", "2026-03-01", "LAB", "SUCCESS"),
        List.of(new AgentRecordContextSummary("record-1", "门诊检验", "2026-03-01", "LAB", "SUCCESS")),
        new AgentRecordContextData("summary", "analysis", List.of()),
        List.of(new AgentTrendSnapshotSummary("record-1", "2026-03-01", "门诊检验", "空腹血糖:6.5mmol/L")),
        new PatientCareProfileResponseData.BaselineSummary(List.of("高血压"), List.of("青霉素"), List.of("ALT长期偏高"), "按季度复查", List.of()),
        List.of(new PatientCareProfileResponseData.MedicationItem("缬沙坦", "80mg", "qd", "降压")),
        List.of("血压稳定 < 130/80"),
        List.of(new PatientCareFollowUpTaskListResponseData.TaskSummary("task-1", "两周后复查血压", "2026-03-15", "HIGH", "OPEN", null, "profile-1", null, "2026-03-01T09:00:00")),
        List.of(new PatientCareRiskOverviewResponseData.RiskSignal("watch", "血压波动", "建议持续监测", "如头晕加重请提前就医")),
        List.of(new PatientCareRiskOverviewResponseData.EvidenceItem("rule_engine", "高血压随访", "近期存在波动", "门诊检验", "medium", "RULE_CONCLUSION")),
        "READY",
        List.of());
    when(contextService.fetchProfileContext("profile-1", "record-1")).thenReturn(payload);

    ResponseEntity<?> response = controller.fetchProfileContext("profile-1", "record-1");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("READY", ((AgentDiseaseProfileContextResponse) response.getBody()).contextStatus());
  }

  @Test
  void getDiseaseProfileContextReturns400OnInvalidRecord() {
    when(contextService.fetchProfileContext("profile-1", "bad-record")).thenThrow(
        new AgentDiseaseProfileContextService.ContextException(400, "INVALID_RECORD_ID", "recordId is invalid"));

    ResponseEntity<?> response = controller.fetchProfileContext("profile-1", "bad-record");

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void getDiseaseProfileContextReturns404WhenProfileMissing() {
    when(contextService.fetchProfileContext("profile-missing", null)).thenThrow(
        new AgentDiseaseProfileContextService.ContextException(404, "PROFILE_NOT_FOUND", "disease profile not found"));

    ResponseEntity<?> response = controller.fetchProfileContext("profile-missing", null);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNotNull(response.getBody());
  }
}
