package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.infrastructure.persistence.entity.FollowUpTaskEntity;
import com.medical.agent.infrastructure.persistence.entity.PatientCareProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.SymptomLogEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.FollowUpTaskMapper;
import com.medical.agent.infrastructure.persistence.mapper.PatientCareProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.SymptomLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientCareServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PATIENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Mock
  private PatientCareProfileMapper patientCareProfileMapper;
  @Mock
  private FollowUpTaskMapper followUpTaskMapper;
  @Mock
  private SymptomLogMapper symptomLogMapper;
  @Mock
  private DiseaseProfileMapper diseaseProfileMapper;
  @Mock
  private RecordMapper recordMapper;
  @Mock
  private RecordService recordService;
  @Mock
  private TenantContextProvider tenantContextProvider;

  private PatientCareService service;

  @BeforeEach
  void setUp() {
    when(tenantContextProvider.currentTenantId()).thenReturn(TENANT_ID);
    when(tenantContextProvider.currentPatientId()).thenReturn(PATIENT_ID);
    when(followUpTaskMapper.selectList(any())).thenReturn(List.of());
    when(symptomLogMapper.selectList(any())).thenReturn(List.of());
    service = new PatientCareService(
        patientCareProfileMapper,
        followUpTaskMapper,
        symptomLogMapper,
        diseaseProfileMapper,
        recordMapper,
        recordService,
        tenantContextProvider,
        new ObjectMapper());
  }

  @Test
  void riskOverviewUsesAbnormalBaselineAndDoctorInstructionsAsCareMemoryEvidence() {
    PatientCareProfileEntity profile = baseProfile();
    profile.setAbnormalBaselineJson("[\"ALT长期轻度偏高\"]");
    profile.setDoctorInstructions("每3个月复查肝肾功能");
    when(patientCareProfileMapper.selectOne(any())).thenReturn(profile);

    PatientCareRiskOverviewResponseData response = service.getRiskOverview(null, null);

    assertEquals("watch", response.riskLevel());
    assertTrue(response.signals().stream().anyMatch(signal ->
        "既往异常基线".equals(signal.title()) && signal.detail().contains("ALT长期轻度偏高")));
    assertTrue(response.evidenceRefs().stream().anyMatch(evidence ->
        "医生交代事项".equals(evidence.title()) && evidence.detail().contains("每3个月复查")));
  }

  @Test
  void overdueFollowUpTaskRaisesWarningRisk() {
    when(patientCareProfileMapper.selectOne(any())).thenReturn(baseProfile());
    FollowUpTaskEntity task = new FollowUpTaskEntity();
    task.setId(UUID.randomUUID());
    task.setTitle("复查肝功能");
    task.setDueDate(LocalDate.now().minusDays(1));
    task.setStatus("OPEN");
    task.setPriority("HIGH");
    task.setNotes("携带最近一次报告");
    task.setCreatedAt(LocalDateTime.now().minusDays(3));
    when(followUpTaskMapper.selectList(any())).thenReturn(List.of(task));

    PatientCareRiskOverviewResponseData response = service.getRiskOverview(null, null);

    assertEquals("warning", response.riskLevel());
    assertTrue(response.signals().stream().anyMatch(signal -> "随访事项到期".equals(signal.title())));
    assertTrue(response.evidenceRefs().stream().anyMatch(evidence -> "follow_up_task".equals(evidence.type())));
  }

  @Test
  void alertSymptomRaisesAlertRisk() {
    when(patientCareProfileMapper.selectOne(any())).thenReturn(baseProfile());
    SymptomLogEntity symptom = new SymptomLogEntity();
    symptom.setId(UUID.randomUUID());
    symptom.setLabel("胸闷");
    symptom.setValue("持续加重");
    symptom.setAlertLevel("ALERT");
    symptom.setNotes("活动后明显");
    symptom.setRecordedAt(LocalDateTime.now());
    when(symptomLogMapper.selectList(any())).thenReturn(List.of(symptom));

    PatientCareRiskOverviewResponseData response = service.getRiskOverview(null, null);

    assertEquals("alert", response.riskLevel());
    assertTrue(response.signals().stream().anyMatch(signal -> "近期症状/体征提醒".equals(signal.title())));
    assertTrue(response.evidenceRefs().stream().anyMatch(evidence -> "symptom_log".equals(evidence.type())));
  }

  private PatientCareProfileEntity baseProfile() {
    PatientCareProfileEntity profile = new PatientCareProfileEntity();
    profile.setId(UUID.randomUUID());
    profile.setTenantId(TENANT_ID);
    profile.setUserId(USER_ID);
    profile.setPatientId(PATIENT_ID);
    profile.setDiagnosedConditionsJson("[]");
    profile.setCurrentMedicationsJson("[]");
    profile.setAllergiesJson("[]");
    profile.setAbnormalBaselineJson("[]");
    profile.setCareGoalsJson("[]");
    profile.setRedFlagNotesJson("[]");
    profile.setUpdatedAt(LocalDateTime.now());
    return profile;
  }
}
