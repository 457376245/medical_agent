package com.medical.agent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.PatientCareService;
import com.medical.agent.application.PatientMemoryService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.CreateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.UpdateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.UpdatePatientCareProfileRequest;
import com.medical.agent.domain.dto.response.PatientCareEvidenceResponseData;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.dto.response.PatientCareSymptomLogListResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryResponseData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientCareControllerTest {

  @Mock
  private PatientCareService patientCareService;

  @Mock
  private PatientMemoryService patientMemoryService;

  private PatientCareController controller;

  @BeforeEach
  void setUp() {
    controller = new PatientCareController(patientCareService, patientMemoryService);
  }

  @Test
  void getProfileReturnsCurrentCareProfile() {
    PatientCareProfileResponseData payload = new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(
            List.of("2型糖尿病"),
            List.of("青霉素"),
            List.of("空腹血糖长期偏高"),
            "每3个月复查",
            List.of()),
        List.of(new PatientCareProfileResponseData.MedicationItem("二甲双胍", "0.5g", "bid", "控糖")),
        List.of("3个月内控制空腹血糖"),
        List.of("胸痛持续需立即就医"),
        List.of("家属协助记录血糖"),
        "2026-04-19T10:00:00");
    when(patientCareService.getProfile()).thenReturn(payload);

    ApiResponse<PatientCareProfileResponseData> response = controller.getProfile();

    assertEquals("OK", response.code());
    assertNotNull(response.requestId());
    assertEquals("2型糖尿病", response.data().patientBaseline().diagnosedConditions().get(0));
  }

  @Test
  void updateProfileDelegatesToService() {
    UpdatePatientCareProfileRequest request = new UpdatePatientCareProfileRequest(
        List.of("高血压"),
        List.of(new UpdatePatientCareProfileRequest.MedicationItemInput("缬沙坦", "80mg", "qd", "降压")),
        List.of("青霉素"),
        List.of("ALT长期偏高"),
        "按季度复查",
        List.of("控制血压"),
        List.of("胸闷加重立即就医"),
        List.of("偏好清单式回答"));
    when(patientCareService.upsertProfile(request)).thenReturn(new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(List.of("高血压"), List.of(), List.of(), "按季度复查", List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "2026-04-19T10:00:00"));

    ApiResponse<PatientCareProfileResponseData> response = controller.updateProfile(request);

    assertEquals("updated", response.message());
    verify(patientCareService).upsertProfile(request);
  }

  @Test
  void createFollowUpTaskReturnsCreatedTask() {
    CreateFollowUpTaskRequest request = new CreateFollowUpTaskRequest("两周后复查", "2026-05-01", "HIGH", "携带血压记录", "profile-1", "record-1");
    when(patientCareService.createFollowUpTask(request)).thenReturn(
        new PatientCareFollowUpTaskListResponseData.TaskSummary(
            "task-1", "两周后复查", "2026-05-01", "HIGH", "OPEN", "携带血压记录", "profile-1", "record-1", "2026-04-19T10:00:00"));

    ApiResponse<PatientCareFollowUpTaskListResponseData.TaskSummary> response = controller.createFollowUpTask(request);

    assertEquals("created", response.message());
    assertEquals("task-1", response.data().id());
  }

  @Test
  void updateFollowUpTaskDelegatesToService() {
    UpdateFollowUpTaskRequest request = new UpdateFollowUpTaskRequest(null, null, null, "DONE", null);
    when(patientCareService.updateFollowUpTask("task-1", request)).thenReturn(
        new PatientCareFollowUpTaskListResponseData.TaskSummary(
            "task-1", "两周后复查", "2026-05-01", "HIGH", "DONE", null, "profile-1", "record-1", "2026-04-19T10:00:00"));

    ApiResponse<PatientCareFollowUpTaskListResponseData.TaskSummary> response = controller.updateFollowUpTask("task-1", request);

    assertEquals("DONE", response.data().status());
    verify(patientCareService).updateFollowUpTask("task-1", request);
  }

  @Test
  void listSymptomsReturnsRecentLogs() {
    when(patientCareService.listSymptoms(4, "profile-1")).thenReturn(new PatientCareSymptomLogListResponseData(
        List.of(new PatientCareSymptomLogListResponseData.SymptomLogItem(
            "log-1", "空腹血糖", "7.2", "mmol/L", "WARNING", "晨起测量", "2026-04-19T09:00:00", "profile-1"))));

    ApiResponse<PatientCareSymptomLogListResponseData> response = controller.listSymptoms(4, "profile-1");

    assertEquals(1, response.data().logs().size());
    assertEquals("空腹血糖", response.data().logs().get(0).label());
  }

  @Test
  void getRiskOverviewReturnsSignalsAndEvidence() {
    when(patientCareService.getRiskOverview("profile-1", "record-1")).thenReturn(
        new PatientCareRiskOverviewResponseData(
            "warning",
            "建议尽快复查",
            List.of(new PatientCareRiskOverviewResponseData.RiskSignal("warning", "趋势监测提醒", "ALT 连续异常", "建议加快复查")),
            List.of(new PatientCareRiskOverviewResponseData.EvidenceItem("trend_monitor", "ALT", "连续异常", "两次检验", "high", "TREND_INFERENCE"))));

    ApiResponse<PatientCareRiskOverviewResponseData> response = controller.getRiskOverview("profile-1", "record-1");

    assertEquals("warning", response.data().riskLevel());
    assertEquals(1, response.data().evidenceRefs().size());
  }

  @Test
  void getEvidenceReturnsEvidenceItems() {
    when(patientCareService.getEvidenceRefs("profile-1", "record-1")).thenReturn(
        new PatientCareEvidenceResponseData(
            List.of(new PatientCareRiskOverviewResponseData.EvidenceItem("rule_engine", "肝功能联动", "规则触发", "门诊检验", "high", "RULE_CONCLUSION"))));

    ApiResponse<PatientCareEvidenceResponseData> response = controller.getEvidence("profile-1", "record-1");

    assertEquals(1, response.data().evidenceRefs().size());
    assertEquals("rule_engine", response.data().evidenceRefs().get(0).type());
  }

  @Test
  void listMemoriesReturnsPendingProfileUpdates() {
    PatientMemoryEntryResponseData memory = new PatientMemoryEntryResponseData(
        "memory-1", "MEDICATION", "currentMedications", "二甲双胍", null,
        "用户说一直服用二甲双胍", "CONVERSATION", null, 0.9, "HIGH", "PROPOSED",
        "profile-1", null, "thread-1", "turn-1", null,
        null, null, null, true,
        null, "2026-05-16T10:00:00", "2026-05-16T10:00:00");
    when(patientMemoryService.listMemories("PROPOSED", 10)).thenReturn(new PatientMemoryEntryListResponseData(List.of(memory)));

    ApiResponse<PatientMemoryEntryListResponseData> response = controller.listMemories("PROPOSED", 10);

    assertEquals(1, response.data().memories().size());
    assertEquals("currentMedications", response.data().memories().get(0).fieldPath());
  }

  @Test
  void confirmMemoryDelegatesToMemoryService() {
    PatientMemoryEntryResponseData memory = new PatientMemoryEntryResponseData(
        "memory-1", "CARE_PROFILE", "careGoals", "每周运动三次", null,
        "用户确认目标", "CONVERSATION", null, 0.8, "LOW", "CONFIRMED",
        null, null, "thread-1", "turn-1", null,
        null, "2026-05-16T10:00:00", null, true,
        "2026-05-16T10:00:00", "2026-05-16T10:00:00", "2026-05-16T10:00:00");
    when(patientMemoryService.confirmMemory("memory-1")).thenReturn(memory);

    ApiResponse<PatientMemoryEntryResponseData> response = controller.confirmMemory("memory-1");

    assertEquals("CONFIRMED", response.data().status());
    verify(patientMemoryService).confirmMemory("memory-1");
  }
}
