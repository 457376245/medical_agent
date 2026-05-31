package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
