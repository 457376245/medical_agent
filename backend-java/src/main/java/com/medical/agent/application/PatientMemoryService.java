package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.domain.dto.request.CreateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.CreateSymptomLogRequest;
import com.medical.agent.domain.dto.request.SubmitPatientMemoryEntriesRequest;
import com.medical.agent.domain.dto.request.UpdatePatientCareProfileRequest;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryListResponseData;
import com.medical.agent.domain.dto.response.PatientMemoryEntryResponseData;
import com.medical.agent.domain.exception.BusinessException;
import com.medical.agent.domain.exception.ResourceNotFoundException;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.infrastructure.persistence.entity.PatientMemoryEntryEntity;
import com.medical.agent.infrastructure.persistence.mapper.PatientMemoryEntryMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientMemoryService {
  private static final int DEFAULT_LIMIT = 20;
  private static final Set<String> SUPPORTED_FIELD_PATHS = Set.of(
      "patientBaseline.diagnosedConditions",
      "patientBaseline.allergies",
      "patientBaseline.abnormalBaseline",
      "patientBaseline.doctorInstructions",
      "patientBaseline.recentSymptoms",
      "currentMedications",
      "careGoals",
      "redFlagNotes",
      "personalContext",
      "followUpTasks");

  private final PatientMemoryEntryMapper memoryMapper;
  private final PatientCareService patientCareService;
  private final TenantContextProvider tenantContextProvider;
  private final ObjectMapper objectMapper;
  @Value("${app.agent.memory-auto-confirm-confidence:0.85}")
  private double autoConfirmConfidence = 0.85;

  public PatientMemoryService(
      PatientMemoryEntryMapper memoryMapper,
      PatientCareService patientCareService,
      TenantContextProvider tenantContextProvider,
      ObjectMapper objectMapper) {
    this.memoryMapper = memoryMapper;
    this.patientCareService = patientCareService;
    this.tenantContextProvider = tenantContextProvider;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PatientMemoryEntryListResponseData submitAgentMemories(SubmitPatientMemoryEntriesRequest request) {
    if (request == null || request.entries() == null || request.entries().isEmpty()) {
      return new PatientMemoryEntryListResponseData(List.of());
    }
    List<PatientMemoryEntryResponseData> saved = new ArrayList<>();
    UUID diseaseProfileId = parseOptionalUuid(request.diseaseProfileId(), "INVALID_PROFILE_ID", "diseaseProfileId is invalid");
    UUID recordId = parseOptionalUuid(request.recordId(), "INVALID_RECORD_ID", "recordId is invalid");
    for (SubmitPatientMemoryEntriesRequest.EntryInput input : request.entries()) {
      PatientMemoryEntryEntity entity = buildEntity(request, input, diseaseProfileId, recordId);
      if (entity == null) {
        continue;
      }
      if (isDuplicate(entity)) {
        continue;
      }
      if (shouldAutoConfirm(entity)) {
        confirmEntity(entity);
      }
      memoryMapper.insert(entity);
      saved.add(toResponse(entity));
    }
    return new PatientMemoryEntryListResponseData(saved);
  }

  public PatientMemoryEntryListResponseData listMemories(String status, Integer limit) {
    String normalizedStatus = normalizeStatusFilter(status);
    LambdaQueryWrapper<PatientMemoryEntryEntity> query = scopedQuery()
        .orderByDesc(PatientMemoryEntryEntity::getUpdatedAt)
        .last("limit " + clampLimit(limit));
    if (normalizedStatus != null) {
      query.eq(PatientMemoryEntryEntity::getStatus, normalizedStatus);
    }
    return new PatientMemoryEntryListResponseData(memoryMapper.selectList(query).stream()
        .map(this::toResponse)
        .toList());
  }

  public List<PatientMemoryEntryResponseData> listPendingForAgent(String profileId, String recordId, int limit) {
    UUID profileUuid = parseOptionalUuid(profileId, "INVALID_PROFILE_ID", "profileId is invalid");
    UUID recordUuid = parseOptionalUuid(recordId, "INVALID_RECORD_ID", "recordId is invalid");
    LambdaQueryWrapper<PatientMemoryEntryEntity> query = scopedQuery()
        .eq(PatientMemoryEntryEntity::getStatus, "PROPOSED")
        .eq(PatientMemoryEntryEntity::getIsCurrent, true)
        .orderByDesc(PatientMemoryEntryEntity::getUpdatedAt)
        .last("limit " + Math.max(1, Math.min(limit, 10)));
    if (profileUuid != null) {
      query.eq(PatientMemoryEntryEntity::getDiseaseProfileId, profileUuid);
    }
    if (recordUuid != null) {
      query.eq(PatientMemoryEntryEntity::getRecordId, recordUuid);
    }
    return memoryMapper.selectList(query).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public PatientMemoryEntryResponseData confirmMemory(String memoryId) {
    PatientMemoryEntryEntity entity = getScopedMemory(memoryId);
    if ("REJECTED".equals(entity.getStatus())) {
      throw new BusinessException("MEMORY_ALREADY_REJECTED", "patient memory has already been rejected");
    }
    if (!"CONFIRMED".equals(entity.getStatus())) {
      confirmEntity(entity);
      memoryMapper.updateById(entity);
    }
    return toResponse(entity);
  }

  @Transactional
  public PatientMemoryEntryResponseData rejectMemory(String memoryId, String reason) {
    PatientMemoryEntryEntity entity = getScopedMemory(memoryId);
    if ("CONFIRMED".equals(entity.getStatus())) {
      throw new BusinessException("MEMORY_ALREADY_CONFIRMED", "confirmed patient memory cannot be rejected");
    }
    entity.setStatus("REJECTED");
    entity.setIsCurrent(false);
    entity.setValidTo(LocalDateTime.now());
    entity.setRejectionReason(TextUtils.trimToNull(reason));
    entity.setUpdatedAt(LocalDateTime.now());
    memoryMapper.updateById(entity);
    return toResponse(entity);
  }

  private PatientMemoryEntryEntity buildEntity(
      SubmitPatientMemoryEntriesRequest request,
      SubmitPatientMemoryEntriesRequest.EntryInput input,
      UUID diseaseProfileId,
      UUID recordId) {
    if (input == null) {
      return null;
    }
    String fieldPath = TextUtils.trimToNull(input.fieldPath());
    String valueText = TextUtils.trimToNull(input.valueText());
    if (fieldPath == null || !SUPPORTED_FIELD_PATHS.contains(fieldPath)) {
      return null;
    }
    String valueJson = writeValueJson(input.value());
    if (valueText == null && valueJson == null) {
      return null;
    }
    LocalDateTime now = LocalDateTime.now();
    PatientMemoryEntryEntity entity = new PatientMemoryEntryEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantContextProvider.currentTenantId());
    entity.setUserId(tenantContextProvider.currentUserId());
    entity.setPatientId(tenantContextProvider.currentPatientId());
    entity.setDiseaseProfileId(diseaseProfileId);
    entity.setRecordId(recordId);
    entity.setConversationThreadId(TextUtils.trimToNull(request.conversationThreadId()));
    entity.setTurnId(TextUtils.trimToNull(request.turnId()));
    entity.setMemoryType(normalizeMemoryType(input.memoryType(), fieldPath));
    entity.setFieldPath(fieldPath);
    entity.setValueText(valueText);
    entity.setValueJson(valueJson);
    entity.setEvidenceText(TextUtils.trimToNull(input.evidenceText()));
    entity.setSourceType("CONVERSATION");
    entity.setSourceRef(TextUtils.trimToNull(input.sourceRef()));
    entity.setConfidence(normalizeConfidence(input.confidence()));
    entity.setRiskLevel(normalizeRisk(input.riskLevel(), fieldPath));
    entity.setStatus("PROPOSED");
    entity.setSupersedesMemoryId(parseOptionalUuid(
        input.supersedesMemoryId(), "INVALID_SUPERSEDED_MEMORY_ID", "supersedesMemoryId is invalid"));
    entity.setIsCurrent(true);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  public List<PatientMemoryEntryResponseData> listCurrentForAgent(String profileId, String recordId, int limit) {
    UUID profileUuid = parseOptionalUuid(profileId, "INVALID_PROFILE_ID", "profileId is invalid");
    UUID recordUuid = parseOptionalUuid(recordId, "INVALID_RECORD_ID", "recordId is invalid");
    LambdaQueryWrapper<PatientMemoryEntryEntity> query = scopedQuery()
        .eq(PatientMemoryEntryEntity::getStatus, "CONFIRMED")
        .eq(PatientMemoryEntryEntity::getIsCurrent, true)
        .orderByDesc(PatientMemoryEntryEntity::getUpdatedAt)
        .last("limit " + Math.max(1, Math.min(limit, 20)));
    if (profileUuid != null) {
      query.eq(PatientMemoryEntryEntity::getDiseaseProfileId, profileUuid);
    }
    if (recordUuid != null) {
      query.eq(PatientMemoryEntryEntity::getRecordId, recordUuid);
    }
    return memoryMapper.selectList(query).stream().map(this::toResponse).toList();
  }

  private void confirmEntity(PatientMemoryEntryEntity entity) {
    LocalDateTime now = LocalDateTime.now();
    PatientMemoryEntryEntity superseded = resolveSuperseded(entity);
    if (superseded != null) {
      superseded.setStatus("SUPERSEDED");
      superseded.setIsCurrent(false);
      superseded.setValidTo(now);
      superseded.setUpdatedAt(now);
      memoryMapper.updateById(superseded);
    }
    applyConfirmedMemory(entity, superseded);
    entity.setStatus("CONFIRMED");
    entity.setConfirmedAt(now);
    entity.setValidFrom(now);
    entity.setValidTo(null);
    entity.setIsCurrent(true);
    entity.setUpdatedAt(now);
  }

  private PatientMemoryEntryEntity resolveSuperseded(PatientMemoryEntryEntity entity) {
    if (entity.getSupersedesMemoryId() == null) {
      return null;
    }
    PatientMemoryEntryEntity existing = getScopedMemory(entity.getSupersedesMemoryId().toString());
    if (!"CONFIRMED".equals(existing.getStatus())
        || Boolean.FALSE.equals(existing.getIsCurrent())
        || !entity.getFieldPath().equals(existing.getFieldPath())) {
      throw new BusinessException("INVALID_MEMORY_SUPERSEDE", "only a current confirmed memory in the same field can be superseded");
    }
    return existing;
  }

  private boolean isDuplicate(PatientMemoryEntryEntity entity) {
    LambdaQueryWrapper<PatientMemoryEntryEntity> query = scopedQuery()
        .eq(PatientMemoryEntryEntity::getFieldPath, entity.getFieldPath())
        .eq(PatientMemoryEntryEntity::getIsCurrent, true)
        .in(PatientMemoryEntryEntity::getStatus, List.of("PROPOSED", "CONFIRMED"))
        .last("limit 1");
    if (entity.getValueText() == null) {
      query.isNull(PatientMemoryEntryEntity::getValueText);
    } else {
      query.eq(PatientMemoryEntryEntity::getValueText, entity.getValueText());
    }
    if (entity.getValueJson() == null) {
      query.isNull(PatientMemoryEntryEntity::getValueJson);
    } else {
      query.eq(PatientMemoryEntryEntity::getValueJson, entity.getValueJson());
    }
    return memoryMapper.selectCount(query) > 0;
  }

  private void applyConfirmedMemory(PatientMemoryEntryEntity entity, PatientMemoryEntryEntity superseded) {
    String fieldPath = entity.getFieldPath();
    if ("patientBaseline.recentSymptoms".equals(fieldPath)) {
      createSymptomFromMemory(entity);
      return;
    }
    if ("followUpTasks".equals(fieldPath)) {
      createTaskFromMemory(entity);
      return;
    }
    mergeCareProfileMemory(entity, superseded);
  }

  private void mergeCareProfileMemory(PatientMemoryEntryEntity entity, PatientMemoryEntryEntity superseded) {
    PatientCareProfileResponseData profile = patientCareService.getProfile();
    PatientCareProfileResponseData.BaselineSummary baseline = profile.patientBaseline();
    List<String> diagnosed = new ArrayList<>(baseline.diagnosedConditions());
    List<String> allergies = new ArrayList<>(baseline.allergies());
    List<String> abnormal = new ArrayList<>(baseline.abnormalBaseline());
    List<String> goals = new ArrayList<>(profile.careGoals());
    List<String> redFlags = new ArrayList<>(profile.redFlagNotes());
    List<String> personalContext = new ArrayList<>(profile.personalContext());
    List<UpdatePatientCareProfileRequest.MedicationItemInput> medications = profile.currentMedications().stream()
        .map(item -> new UpdatePatientCareProfileRequest.MedicationItemInput(
            item.name(), item.dosage(), item.frequency(), item.purpose()))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    String doctorInstructions = baseline.doctorInstructions();
    String value = memoryValueText(entity);
    String oldValue = superseded == null ? null : memoryValueText(superseded);

    if (oldValue != null) {
      removeValue(diagnosed, oldValue);
      removeValue(allergies, oldValue);
      removeValue(abnormal, oldValue);
      removeValue(goals, oldValue);
      removeValue(redFlags, oldValue);
      removeValue(personalContext, oldValue);
      medications.removeIf(item -> item.name() != null && item.name().equalsIgnoreCase(oldValue));
      if (doctorInstructions != null && doctorInstructions.equalsIgnoreCase(oldValue)) {
        doctorInstructions = null;
      }
    }

    switch (entity.getFieldPath()) {
      case "patientBaseline.diagnosedConditions" -> addUnique(diagnosed, value);
      case "patientBaseline.allergies" -> addUnique(allergies, value);
      case "patientBaseline.abnormalBaseline" -> addUnique(abnormal, value);
      case "patientBaseline.doctorInstructions" -> doctorInstructions = appendText(doctorInstructions, value);
      case "careGoals" -> addUnique(goals, value);
      case "redFlagNotes" -> addUnique(redFlags, value);
      case "personalContext" -> addUnique(personalContext, value);
      case "currentMedications" -> addMedication(medications, entity);
      default -> {
        return;
      }
    }

    patientCareService.upsertProfile(new UpdatePatientCareProfileRequest(
        diagnosed,
        medications,
        allergies,
        abnormal,
        doctorInstructions,
        goals,
        redFlags,
        personalContext));
  }

  private void createSymptomFromMemory(PatientMemoryEntryEntity entity) {
    JsonNode value = readValueJson(entity);
    String label = readNodeText(value, "label");
    if (label == null) {
      label = memoryValueText(entity);
    }
    if (label == null) {
      return;
    }
    patientCareService.createSymptomLog(new CreateSymptomLogRequest(
        label,
        readNodeText(value, "value"),
        readNodeText(value, "unit"),
        defaultText(readNodeText(value, "alertLevel"), "NORMAL"),
        defaultText(readNodeText(value, "notes"), entity.getEvidenceText()),
        readNodeText(value, "recordedAt"),
        entity.getDiseaseProfileId() == null ? null : entity.getDiseaseProfileId().toString()));
  }

  private void createTaskFromMemory(PatientMemoryEntryEntity entity) {
    JsonNode value = readValueJson(entity);
    String title = readNodeText(value, "title");
    if (title == null) {
      title = memoryValueText(entity);
    }
    if (title == null) {
      return;
    }
    patientCareService.createFollowUpTask(new CreateFollowUpTaskRequest(
        title,
        readNodeText(value, "dueDate"),
        defaultText(readNodeText(value, "priority"), "MEDIUM"),
        defaultText(readNodeText(value, "notes"), entity.getEvidenceText()),
        entity.getDiseaseProfileId() == null ? null : entity.getDiseaseProfileId().toString(),
        entity.getRecordId() == null ? null : entity.getRecordId().toString()));
  }

  private void addMedication(List<UpdatePatientCareProfileRequest.MedicationItemInput> medications, PatientMemoryEntryEntity entity) {
    JsonNode value = readValueJson(entity);
    String name = readNodeText(value, "name");
    if (name == null) {
      name = memoryValueText(entity);
    }
    if (name == null) {
      return;
    }
    String normalizedName = name.trim().toLowerCase(Locale.ROOT);
    boolean exists = medications.stream()
        .anyMatch(item -> item.name() != null && item.name().trim().toLowerCase(Locale.ROOT).equals(normalizedName));
    if (!exists) {
      medications.add(new UpdatePatientCareProfileRequest.MedicationItemInput(
          name,
          readNodeText(value, "dosage"),
          readNodeText(value, "frequency"),
          readNodeText(value, "purpose")));
    }
  }

  private PatientMemoryEntryEntity getScopedMemory(String memoryId) {
    UUID uuid = parseRequiredUuid(memoryId, "INVALID_MEMORY_ID", "memoryId is invalid");
    PatientMemoryEntryEntity entity = memoryMapper.selectOne(scopedQuery()
        .eq(PatientMemoryEntryEntity::getId, uuid)
        .last("limit 1"));
    if (entity == null) {
      throw new ResourceNotFoundException("MEMORY_NOT_FOUND", "patient memory not found");
    }
    return entity;
  }

  private LambdaQueryWrapper<PatientMemoryEntryEntity> scopedQuery() {
    return new LambdaQueryWrapper<PatientMemoryEntryEntity>()
        .eq(PatientMemoryEntryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(PatientMemoryEntryEntity::getPatientId, tenantContextProvider.currentPatientId());
  }

  private boolean shouldAutoConfirm(PatientMemoryEntryEntity entity) {
    if (!"LOW".equals(entity.getRiskLevel())
        || entity.getConfidence() == null
        || entity.getConfidence() < autoConfirmConfidence
        || TextUtils.trimToNull(entity.getEvidenceText()) == null) {
      return false;
    }
    return "personalContext".equals(entity.getFieldPath());
  }

  private PatientMemoryEntryResponseData toResponse(PatientMemoryEntryEntity entity) {
    return new PatientMemoryEntryResponseData(
        entity.getId().toString(),
        entity.getMemoryType(),
        entity.getFieldPath(),
        entity.getValueText(),
        entity.getValueJson(),
        entity.getEvidenceText(),
        entity.getSourceType(),
        entity.getSourceRef(),
        entity.getConfidence(),
        entity.getRiskLevel(),
        entity.getStatus(),
        entity.getDiseaseProfileId() == null ? null : entity.getDiseaseProfileId().toString(),
        entity.getRecordId() == null ? null : entity.getRecordId().toString(),
        entity.getConversationThreadId(),
        entity.getTurnId(),
        entity.getRejectionReason(),
        entity.getSupersedesMemoryId() == null ? null : entity.getSupersedesMemoryId().toString(),
        entity.getValidFrom() == null ? null : String.valueOf(entity.getValidFrom()),
        entity.getValidTo() == null ? null : String.valueOf(entity.getValidTo()),
        entity.getIsCurrent(),
        entity.getConfirmedAt() == null ? null : String.valueOf(entity.getConfirmedAt()),
        entity.getCreatedAt() == null ? null : String.valueOf(entity.getCreatedAt()),
        entity.getUpdatedAt() == null ? null : String.valueOf(entity.getUpdatedAt()));
  }

  private String writeValueJson(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception error) {
      return null;
    }
  }

  private JsonNode readValueJson(PatientMemoryEntryEntity entity) {
    try {
      return objectMapper.readTree(entity.getValueJson() == null ? "{}" : entity.getValueJson());
    } catch (Exception ignored) {
      return objectMapper.createObjectNode();
    }
  }

  private String readNodeText(JsonNode node, String fieldName) {
    if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
      return null;
    }
    return TextUtils.trimToNull(node.get(fieldName).asText());
  }

  private String memoryValueText(PatientMemoryEntryEntity entity) {
    String text = TextUtils.trimToNull(entity.getValueText());
    if (text != null) {
      return text;
    }
    JsonNode value = readValueJson(entity);
    return readNodeText(value, "text");
  }

  private void addUnique(List<String> values, String value) {
    String text = TextUtils.trimToNull(value);
    if (text == null) {
      return;
    }
    String normalized = text.toLowerCase(Locale.ROOT);
    boolean exists = values.stream()
        .anyMatch(item -> item != null && item.trim().toLowerCase(Locale.ROOT).equals(normalized));
    if (!exists) {
      values.add(text);
    }
  }

  private String appendText(String existing, String addition) {
    String value = TextUtils.trimToNull(addition);
    if (value == null) {
      return TextUtils.trimToNull(existing);
    }
    String current = TextUtils.trimToNull(existing);
    if (current == null) {
      return value;
    }
    if (current.contains(value)) {
      return current;
    }
    return current + "；" + value;
  }

  private String normalizeMemoryType(String rawType, String fieldPath) {
    String type = TextUtils.trimToNull(rawType);
    if (type != null) {
      return type.trim().toUpperCase(Locale.ROOT);
    }
    if (fieldPath.startsWith("patientBaseline")) {
      return "BASELINE";
    }
    if ("currentMedications".equals(fieldPath)) {
      return "MEDICATION";
    }
    if ("followUpTasks".equals(fieldPath)) {
      return "FOLLOW_UP";
    }
    return "CARE_PROFILE";
  }

  private String normalizeRisk(String rawRisk, String fieldPath) {
    String floor = switch (fieldPath) {
      case "patientBaseline.diagnosedConditions", "patientBaseline.allergies", "currentMedications",
           "patientBaseline.doctorInstructions", "redFlagNotes" -> "HIGH";
      case "followUpTasks", "patientBaseline.abnormalBaseline", "patientBaseline.recentSymptoms",
           "careGoals" -> "MEDIUM";
      default -> "LOW";
    };
    String risk = TextUtils.trimToNull(rawRisk);
    if (risk != null) {
      String upper = risk.trim().toUpperCase(Locale.ROOT);
      if (List.of("LOW", "MEDIUM", "HIGH").contains(upper)) {
        return riskRank(upper) < riskRank(floor) ? floor : upper;
      }
    }
    return floor;
  }

  private void removeValue(List<String> values, String value) {
    values.removeIf(item -> item != null && item.equalsIgnoreCase(value));
  }

  private int riskRank(String value) {
    return "HIGH".equals(value) ? 3 : "MEDIUM".equals(value) ? 2 : 1;
  }

  private String normalizeStatusFilter(String rawStatus) {
    String status = TextUtils.trimToNull(rawStatus);
    if (status == null) {
      return null;
    }
    String upper = status.toUpperCase(Locale.ROOT);
    if (!List.of("PROPOSED", "CONFIRMED", "REJECTED", "SUPERSEDED").contains(upper)) {
      throw new BusinessException("INVALID_MEMORY_STATUS", "invalid memory status");
    }
    return upper;
  }

  private Double normalizeConfidence(Double value) {
    if (value == null) {
      return null;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  private int clampLimit(Integer limit) {
    int value = limit == null ? DEFAULT_LIMIT : limit;
    return Math.max(1, Math.min(value, 100));
  }

  private UUID parseOptionalUuid(String rawValue, String code, String message) {
    String value = TextUtils.trimToNull(rawValue);
    return value == null ? null : parseRequiredUuid(value, code, message);
  }

  private UUID parseRequiredUuid(String rawValue, String code, String message) {
    try {
      return UUID.fromString(rawValue);
    } catch (Exception error) {
      throw new BusinessException(code, message);
    }
  }

  private String defaultText(String value, String fallback) {
    String text = TextUtils.trimToNull(value);
    return text == null ? TextUtils.trimToNull(fallback) : text;
  }
}
