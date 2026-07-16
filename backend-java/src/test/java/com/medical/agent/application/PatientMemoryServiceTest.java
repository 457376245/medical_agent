package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.domain.dto.request.SubmitPatientMemoryEntriesRequest;
import com.medical.agent.domain.dto.request.UpdatePatientCareProfileRequest;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.PatientMemoryEntryEntity;
import com.medical.agent.infrastructure.persistence.mapper.PatientMemoryEntryMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientMemoryServiceTest {
  @Mock
  private PatientMemoryEntryMapper memoryMapper;

  @Mock
  private PatientCareService patientCareService;

  @Mock
  private TenantContextProvider tenantContextProvider;

  private PatientMemoryService service;

  @BeforeEach
  void setUp() {
    lenient().when(tenantContextProvider.currentTenantId()).thenReturn(ScopeConstants.DEFAULT_TENANT_ID);
    lenient().when(tenantContextProvider.currentUserId()).thenReturn(ScopeConstants.DEFAULT_USER_ID);
    lenient().when(tenantContextProvider.currentPatientId()).thenReturn(ScopeConstants.DEFAULT_PATIENT_ID);
    service = new PatientMemoryService(memoryMapper, patientCareService, tenantContextProvider, new ObjectMapper());
  }

  @Test
  void submitHighRiskMemoryKeepsProposedAndDoesNotMergeProfile() {
    SubmitPatientMemoryEntriesRequest request = new SubmitPatientMemoryEntriesRequest(
        "thread-1",
        "turn-1",
        null,
        null,
        List.of(new SubmitPatientMemoryEntriesRequest.EntryInput(
            "MEDICATION",
            "currentMedications",
            "阿司匹林",
            null,
            "用户说正在吃阿司匹林",
            0.82,
            "HIGH",
            null,
            null)));

    PatientMemoryEntryListResponseData response = service.submitAgentMemories(request);

    assertEquals(1, response.memories().size());
    assertEquals("PROPOSED", response.memories().get(0).status());
    verify(memoryMapper).insert(any(PatientMemoryEntryEntity.class));
    verify(patientCareService, never()).upsertProfile(any(UpdatePatientCareProfileRequest.class));
  }

  @Test
  void confirmMemoryMergesCareGoalIntoProfile() {
    UUID memoryId = UUID.randomUUID();
    PatientMemoryEntryEntity entity = new PatientMemoryEntryEntity();
    entity.setId(memoryId);
    entity.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    entity.setPatientId(ScopeConstants.DEFAULT_PATIENT_ID);
    entity.setUserId(ScopeConstants.DEFAULT_USER_ID);
    entity.setMemoryType("CARE_PROFILE");
    entity.setFieldPath("careGoals");
    entity.setValueText("三个月内稳定空腹血糖");
    entity.setRiskLevel("LOW");
    entity.setStatus("PROPOSED");
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    when(memoryMapper.selectOne(any())).thenReturn(entity);
    when(patientCareService.getProfile()).thenReturn(new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(List.of(), List.of(), List.of(), null, List.of()),
        List.of(),
        List.of("每周运动三次"),
        List.of(),
        List.of("家属协助记录血糖"),
        "2026-05-16T10:00:00"));

    service.confirmMemory(memoryId.toString());

    ArgumentCaptor<UpdatePatientCareProfileRequest> captor = ArgumentCaptor.forClass(UpdatePatientCareProfileRequest.class);
    verify(patientCareService).upsertProfile(captor.capture());
    assertEquals(List.of("每周运动三次", "三个月内稳定空腹血糖"), captor.getValue().careGoals());
    assertEquals(List.of("家属协助记录血糖"), captor.getValue().personalContext());
    verify(memoryMapper).updateById(entity);
  }

  @Test
  void confirmMemoryMergesPersonalContextIntoProfile() {
    UUID memoryId = UUID.randomUUID();
    PatientMemoryEntryEntity entity = new PatientMemoryEntryEntity();
    entity.setId(memoryId);
    entity.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    entity.setPatientId(ScopeConstants.DEFAULT_PATIENT_ID);
    entity.setUserId(ScopeConstants.DEFAULT_USER_ID);
    entity.setMemoryType("PERSONAL_CONTEXT");
    entity.setFieldPath("personalContext");
    entity.setValueText("偏好清单式回答");
    entity.setRiskLevel("LOW");
    entity.setStatus("PROPOSED");
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    when(memoryMapper.selectOne(any())).thenReturn(entity);
    when(patientCareService.getProfile()).thenReturn(new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(List.of(), List.of(), List.of(), null, List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of("家属协助记录血糖"),
        "2026-05-16T10:00:00"));

    service.confirmMemory(memoryId.toString());

    ArgumentCaptor<UpdatePatientCareProfileRequest> captor = ArgumentCaptor.forClass(UpdatePatientCareProfileRequest.class);
    verify(patientCareService).upsertProfile(captor.capture());
    assertEquals(List.of("家属协助记录血糖", "偏好清单式回答"), captor.getValue().personalContext());
  }

  @Test
  void modelCannotDowngradeMedicationRisk() {
    SubmitPatientMemoryEntriesRequest request = new SubmitPatientMemoryEntriesRequest(
        "thread-1", "turn-1", null, null,
        List.of(new SubmitPatientMemoryEntriesRequest.EntryInput(
            "MEDICATION", "currentMedications", "阿司匹林", null,
            "用户说正在吃阿司匹林", 0.99, "LOW", null, null)));

    service.submitAgentMemories(request);

    ArgumentCaptor<PatientMemoryEntryEntity> captor = ArgumentCaptor.forClass(PatientMemoryEntryEntity.class);
    verify(memoryMapper).insert(captor.capture());
    assertEquals("HIGH", captor.getValue().getRiskLevel());
    assertEquals("PROPOSED", captor.getValue().getStatus());
  }

  @Test
  void autoConfirmOnlyAllowsHighConfidencePersonalContextWithEvidence() {
    when(patientCareService.getProfile()).thenReturn(emptyProfile());
    SubmitPatientMemoryEntriesRequest accepted = new SubmitPatientMemoryEntriesRequest(
        "thread-1", "turn-1", null, null,
        List.of(new SubmitPatientMemoryEntriesRequest.EntryInput(
            "PERSONAL_CONTEXT", "personalContext", "偏好清单式回答", null,
            "用户明确说偏好清单", 0.9, "LOW", null, null)));

    PatientMemoryEntryListResponseData acceptedResult = service.submitAgentMemories(accepted);
    assertEquals("CONFIRMED", acceptedResult.memories().get(0).status());

    SubmitPatientMemoryEntriesRequest symptom = new SubmitPatientMemoryEntriesRequest(
        "thread-1", "turn-2", null, null,
        List.of(new SubmitPatientMemoryEntriesRequest.EntryInput(
            "SYMPTOM", "patientBaseline.recentSymptoms", "胸闷", null,
            "用户说胸闷", 0.99, "LOW", null, null)));
    PatientMemoryEntryListResponseData symptomResult = service.submitAgentMemories(symptom);
    assertEquals("PROPOSED", symptomResult.memories().get(0).status());
    assertEquals("MEDIUM", symptomResult.memories().get(0).riskLevel());
  }

  @Test
  void duplicateCandidateIsSuppressed() {
    when(memoryMapper.selectCount(any())).thenReturn(1L);
    SubmitPatientMemoryEntriesRequest request = new SubmitPatientMemoryEntriesRequest(
        "thread-1", "turn-1", null, null,
        List.of(new SubmitPatientMemoryEntriesRequest.EntryInput(
            "PERSONAL_CONTEXT", "personalContext", "偏好简短回答", null,
            "用户明确表达", 0.9, "LOW", null, null)));

    PatientMemoryEntryListResponseData result = service.submitAgentMemories(request);

    assertEquals(0, result.memories().size());
    verify(memoryMapper, never()).insert(any(PatientMemoryEntryEntity.class));
  }

  @Test
  void confirmingCorrectionSupersedesOldMemoryAndRemovesOldProfileValue() {
    UUID oldId = UUID.randomUUID();
    UUID newId = UUID.randomUUID();
    PatientMemoryEntryEntity replacement = memory(newId, "偏好详细解释", "PROPOSED");
    replacement.setSupersedesMemoryId(oldId);
    PatientMemoryEntryEntity old = memory(oldId, "偏好简短回答", "CONFIRMED");
    old.setIsCurrent(true);
    when(memoryMapper.selectOne(any())).thenReturn(replacement, old);
    when(patientCareService.getProfile()).thenReturn(new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(List.of(), List.of(), List.of(), null, List.of()),
        List.of(), List.of(), List.of(), List.of("偏好简短回答"), "2026-05-16T10:00:00"));

    service.confirmMemory(newId.toString());

    ArgumentCaptor<UpdatePatientCareProfileRequest> profile = ArgumentCaptor.forClass(UpdatePatientCareProfileRequest.class);
    verify(patientCareService).upsertProfile(profile.capture());
    assertEquals(List.of("偏好详细解释"), profile.getValue().personalContext());
    assertEquals("SUPERSEDED", old.getStatus());
    assertFalse(old.getIsCurrent());
    assertEquals(oldId, replacement.getSupersedesMemoryId());
  }

  private PatientCareProfileResponseData emptyProfile() {
    return new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(List.of(), List.of(), List.of(), null, List.of()),
        List.of(), List.of(), List.of(), List.of(), "2026-05-16T10:00:00");
  }

  private PatientMemoryEntryEntity memory(UUID id, String value, String status) {
    PatientMemoryEntryEntity entity = new PatientMemoryEntryEntity();
    entity.setId(id);
    entity.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    entity.setUserId(ScopeConstants.DEFAULT_USER_ID);
    entity.setPatientId(ScopeConstants.DEFAULT_PATIENT_ID);
    entity.setMemoryType("PERSONAL_CONTEXT");
    entity.setFieldPath("personalContext");
    entity.setValueText(value);
    entity.setEvidenceText("用户明确表达");
    entity.setConfidence(0.95);
    entity.setRiskLevel("LOW");
    entity.setStatus(status);
    entity.setIsCurrent(true);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    return entity;
  }
}
