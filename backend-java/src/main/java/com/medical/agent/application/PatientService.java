package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.domain.dto.request.PatientCreateRequest;
import com.medical.agent.domain.dto.request.PatientUpdateRequest;
import com.medical.agent.domain.dto.response.PatientCreateResponseData;
import com.medical.agent.domain.dto.response.PatientListResponseData;
import com.medical.agent.domain.dto.response.PatientListResponseData.PatientSummary;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.PatientEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.PatientMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
  private final PatientMapper patientMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final TenantContextProvider tenantContextProvider;

  public PatientService(PatientMapper patientMapper, DiseaseProfileMapper diseaseProfileMapper,
      TenantContextProvider tenantContextProvider) {
    this.patientMapper = patientMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.tenantContextProvider = tenantContextProvider;
  }

  public PatientListResponseData list() {
    UUID tenantId = tenantContextProvider.currentTenantId();
    UUID userId = tenantContextProvider.currentUserId();

    List<PatientEntity> patients = patientMapper.selectList(
        new LambdaQueryWrapper<PatientEntity>()
            .eq(PatientEntity::getTenantId, tenantId)
            .eq(PatientEntity::getUserId, userId)
            .orderByDesc(PatientEntity::getIsDefault)
            .orderByAsc(PatientEntity::getCreatedAt));

    List<PatientSummary> summaries = patients.stream()
        .map(p -> new PatientSummary(
            p.getId().toString(),
            p.getName(),
            p.getRelationship(),
            p.getGender(),
            p.getBirthDate() != null ? p.getBirthDate().toString() : null,
            p.getNotes(),
            Boolean.TRUE.equals(p.getIsDefault())))
        .toList();

    return new PatientListResponseData(summaries);
  }

  public PatientCreateResponseData create(PatientCreateRequest request) {
    UUID tenantId = tenantContextProvider.currentTenantId();
    UUID userId = tenantContextProvider.currentUserId();

    PatientEntity existing = patientMapper.selectOne(
        new LambdaQueryWrapper<PatientEntity>()
            .eq(PatientEntity::getTenantId, tenantId)
            .eq(PatientEntity::getUserId, userId)
            .eq(PatientEntity::getName, request.name()));
    if (existing != null) {
      throw new IllegalArgumentException("该名称已存在");
    }

    LocalDateTime now = LocalDateTime.now();
    PatientEntity patient = new PatientEntity();
    patient.setId(UUID.randomUUID());
    patient.setTenantId(tenantId);
    patient.setUserId(userId);
    patient.setName(request.name());
    patient.setRelationship(request.relationship() != null ? request.relationship() : "其他");
    patient.setGender(request.gender());
    if (request.birthDate() != null && !request.birthDate().isBlank()) {
      patient.setBirthDate(LocalDate.parse(request.birthDate()));
    }
    patient.setNotes(request.notes());
    patient.setIsDefault(false);
    patient.setCreatedAt(now);
    patient.setUpdatedAt(now);
    patientMapper.insert(patient);

    return new PatientCreateResponseData(
        patient.getId().toString(),
        patient.getName(),
        patient.getRelationship());
  }

  public void update(UUID patientId, PatientUpdateRequest request) {
    PatientEntity patient = patientMapper.selectById(patientId);
    if (patient == null) {
      throw new IllegalArgumentException("病人不存在");
    }

    if (request.name() != null) {
      patient.setName(request.name());
    }
    if (request.relationship() != null) {
      patient.setRelationship(request.relationship());
    }
    if (request.gender() != null) {
      patient.setGender(request.gender());
    }
    if (request.birthDate() != null) {
      patient.setBirthDate(request.birthDate().isBlank() ? null : LocalDate.parse(request.birthDate()));
    }
    if (request.notes() != null) {
      patient.setNotes(request.notes());
    }
    patient.setUpdatedAt(LocalDateTime.now());
    patientMapper.updateById(patient);
  }

  public void delete(UUID patientId) {
    PatientEntity patient = patientMapper.selectById(patientId);
    if (patient == null) {
      throw new IllegalArgumentException("病人不存在");
    }
    if (Boolean.TRUE.equals(patient.getIsDefault())) {
      throw new IllegalArgumentException("不能删除默认病人");
    }

    long profileCount = diseaseProfileMapper.selectCount(
        new LambdaQueryWrapper<DiseaseProfileEntity>()
            .eq(DiseaseProfileEntity::getPatientId, patientId));
    if (profileCount > 0) {
      throw new IllegalArgumentException("该病人下还有疾病档案，无法删除");
    }

    patientMapper.deleteById(patientId);
  }
}
