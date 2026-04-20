package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.request.CreateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.CreateSymptomLogRequest;
import com.medical.agent.domain.dto.request.UpdateFollowUpTaskRequest;
import com.medical.agent.domain.dto.request.UpdatePatientCareProfileRequest;
import com.medical.agent.domain.dto.response.PatientCareEvidenceResponseData;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.dto.response.PatientCareSymptomLogListResponseData;
import com.medical.agent.domain.exception.BusinessException;
import com.medical.agent.domain.exception.ResourceNotFoundException;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.FollowUpTaskEntity;
import com.medical.agent.infrastructure.persistence.entity.PatientCareProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.SymptomLogEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.FollowUpTaskMapper;
import com.medical.agent.infrastructure.persistence.mapper.PatientCareProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.SymptomLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientCareService {
  private static final int DEFAULT_TASK_LIMIT = 8;
  private static final int DEFAULT_SYMPTOM_LIMIT = 6;
  private static final int MAX_RISK_ITEMS = 4;

  private final PatientCareProfileMapper patientCareProfileMapper;
  private final FollowUpTaskMapper followUpTaskMapper;
  private final SymptomLogMapper symptomLogMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final RecordMapper recordMapper;
  private final RecordService recordService;
  private final TenantContextProvider tenantContextProvider;
  private final ObjectMapper objectMapper;

  public PatientCareService(
      PatientCareProfileMapper patientCareProfileMapper,
      FollowUpTaskMapper followUpTaskMapper,
      SymptomLogMapper symptomLogMapper,
      DiseaseProfileMapper diseaseProfileMapper,
      RecordMapper recordMapper,
      RecordService recordService,
      TenantContextProvider tenantContextProvider,
      ObjectMapper objectMapper) {
    this.patientCareProfileMapper = patientCareProfileMapper;
    this.followUpTaskMapper = followUpTaskMapper;
    this.symptomLogMapper = symptomLogMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.recordMapper = recordMapper;
    this.recordService = recordService;
    this.tenantContextProvider = tenantContextProvider;
    this.objectMapper = objectMapper;
  }

  public PatientCareProfileResponseData getProfile() {
    PatientCareProfileEntity entity = currentCareProfile();
    return toProfileResponse(entity, listRecentSymptomItems(DEFAULT_SYMPTOM_LIMIT));
  }

  @Transactional
  public PatientCareProfileResponseData upsertProfile(UpdatePatientCareProfileRequest request) {
    PatientCareProfileEntity entity = currentCareProfile();
    boolean isNew = entity == null;
    LocalDateTime now = LocalDateTime.now();
    if (isNew) {
      entity = new PatientCareProfileEntity();
      entity.setId(UUID.randomUUID());
      entity.setTenantId(tenantContextProvider.currentTenantId());
      entity.setUserId(tenantContextProvider.currentUserId());
      entity.setPatientId(tenantContextProvider.currentPatientId());
      entity.setCreatedAt(now);
    }

    entity.setDiagnosedConditionsJson(writeJson(cleanStringList(request == null ? null : request.diagnosedConditions())));
    entity.setCurrentMedicationsJson(writeJson(cleanMedicationInputs(request == null ? null : request.currentMedications())));
    entity.setAllergiesJson(writeJson(cleanStringList(request == null ? null : request.allergies())));
    entity.setAbnormalBaselineJson(writeJson(cleanStringList(request == null ? null : request.abnormalBaseline())));
    entity.setDoctorInstructions(TextUtils.trimToNull(request == null ? null : request.doctorInstructions()));
    entity.setCareGoalsJson(writeJson(cleanStringList(request == null ? null : request.careGoals())));
    entity.setRedFlagNotesJson(writeJson(cleanStringList(request == null ? null : request.redFlagNotes())));
    entity.setUpdatedAt(now);

    if (isNew) {
      patientCareProfileMapper.insert(entity);
    } else {
      patientCareProfileMapper.updateById(entity);
    }
    return toProfileResponse(entity, listRecentSymptomItems(DEFAULT_SYMPTOM_LIMIT));
  }

  public PatientCareFollowUpTaskListResponseData listFollowUpTasks(String status, Integer limit) {
    String normalizedStatus = normalizeTaskStatusFilter(status);
    int normalizedLimit = clampLimit(limit, DEFAULT_TASK_LIMIT, 20);

    LambdaQueryWrapper<FollowUpTaskEntity> query = new LambdaQueryWrapper<FollowUpTaskEntity>()
        .eq(FollowUpTaskEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(FollowUpTaskEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByAsc(FollowUpTaskEntity::getStatus)
        .orderByAsc(FollowUpTaskEntity::getDueDate)
        .orderByDesc(FollowUpTaskEntity::getCreatedAt)
        .last("limit " + normalizedLimit);
    if (normalizedStatus != null) {
      query.eq(FollowUpTaskEntity::getStatus, normalizedStatus);
    }

    List<PatientCareFollowUpTaskListResponseData.TaskSummary> tasks = followUpTaskMapper.selectList(query).stream()
        .map(this::toTaskSummary)
        .toList();
    return new PatientCareFollowUpTaskListResponseData(tasks);
  }

  @Transactional
  public PatientCareFollowUpTaskListResponseData.TaskSummary createFollowUpTask(CreateFollowUpTaskRequest request) {
    String title = TextUtils.trimToNull(request == null ? null : request.title());
    if (title == null) {
      throw new BusinessException("INVALID_TASK_TITLE", "task title is required");
    }

    FollowUpTaskEntity entity = new FollowUpTaskEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantContextProvider.currentTenantId());
    entity.setUserId(tenantContextProvider.currentUserId());
    entity.setPatientId(tenantContextProvider.currentPatientId());
    entity.setDiseaseProfileId(resolveOptionalProfileId(request == null ? null : request.diseaseProfileId()));
    entity.setRecordId(resolveOptionalRecordId(request == null ? null : request.recordId()));
    entity.setTitle(title);
    entity.setDueDate(parseOptionalDate(request == null ? null : request.dueDate()));
    entity.setPriority(normalizePriority(request == null ? null : request.priority()));
    entity.setStatus("OPEN");
    entity.setSource("MANUAL");
    entity.setNotes(TextUtils.trimToNull(request == null ? null : request.notes()));
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    followUpTaskMapper.insert(entity);
    return toTaskSummary(entity);
  }

  @Transactional
  public PatientCareFollowUpTaskListResponseData.TaskSummary updateFollowUpTask(String taskId, UpdateFollowUpTaskRequest request) {
    UUID taskUuid = parseUuid(taskId, "INVALID_TASK_ID", "taskId is invalid");
    FollowUpTaskEntity entity = followUpTaskMapper.selectOne(new LambdaQueryWrapper<FollowUpTaskEntity>()
        .eq(FollowUpTaskEntity::getId, taskUuid)
        .eq(FollowUpTaskEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(FollowUpTaskEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (entity == null) {
      throw new ResourceNotFoundException("TASK_NOT_FOUND", "follow-up task not found");
    }

    if (request != null) {
      if (TextUtils.trimToNull(request.title()) != null) {
        entity.setTitle(TextUtils.trimToNull(request.title()));
      }
      if (request.dueDate() != null) {
        entity.setDueDate(parseOptionalDate(request.dueDate()));
      }
      if (request.priority() != null) {
        entity.setPriority(normalizePriority(request.priority()));
      }
      if (request.status() != null) {
        entity.setStatus(normalizeTaskStatus(request.status()));
      }
      if (request.notes() != null) {
        entity.setNotes(TextUtils.trimToNull(request.notes()));
      }
    }
    entity.setUpdatedAt(LocalDateTime.now());
    followUpTaskMapper.updateById(entity);
    return toTaskSummary(entity);
  }

  public PatientCareSymptomLogListResponseData listSymptoms(Integer limit) {
    return new PatientCareSymptomLogListResponseData(listRecentSymptomLogs(clampLimit(limit, DEFAULT_SYMPTOM_LIMIT, 20)));
  }

  @Transactional
  public PatientCareSymptomLogListResponseData.SymptomLogItem createSymptomLog(CreateSymptomLogRequest request) {
    String label = TextUtils.trimToNull(request == null ? null : request.label());
    if (label == null) {
      throw new BusinessException("INVALID_SYMPTOM_LABEL", "symptom label is required");
    }

    SymptomLogEntity entity = new SymptomLogEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantContextProvider.currentTenantId());
    entity.setUserId(tenantContextProvider.currentUserId());
    entity.setPatientId(tenantContextProvider.currentPatientId());
    entity.setDiseaseProfileId(resolveOptionalProfileId(request == null ? null : request.diseaseProfileId()));
    entity.setLabel(label);
    entity.setValue(TextUtils.trimToNull(request == null ? null : request.value()));
    entity.setUnit(TextUtils.trimToNull(request == null ? null : request.unit()));
    entity.setAlertLevel(normalizeAlertLevel(request == null ? null : request.alertLevel()));
    entity.setSource("MANUAL");
    entity.setNotes(TextUtils.trimToNull(request == null ? null : request.notes()));
    entity.setRecordedAt(parseOptionalDateTime(request == null ? null : request.recordedAt(), LocalDateTime.now()));
    entity.setCreatedAt(LocalDateTime.now());
    symptomLogMapper.insert(entity);
    return toSymptomItem(entity);
  }

  public PatientCareRiskOverviewResponseData getRiskOverview(String profileId, String recordId) {
    return getRiskOverview(profileId, recordId, null);
  }

  public PatientCareRiskOverviewResponseData getRiskOverview(
      String profileId, String recordId, PatientCareProfileResponseData preloadedProfile) {
    RiskComputation risk = computeRisk(profileId, recordId, preloadedProfile);
    return new PatientCareRiskOverviewResponseData(
        risk.riskLevel,
        risk.summary,
        risk.signals,
        risk.evidenceRefs);
  }

  public PatientCareEvidenceResponseData getEvidenceRefs(String profileId, String recordId) {
    return new PatientCareEvidenceResponseData(computeRisk(profileId, recordId, null).evidenceRefs);
  }

  private RiskComputation computeRisk(
      String profileId, String recordId, PatientCareProfileResponseData preloadedProfile) {
    List<PatientCareRiskOverviewResponseData.RiskSignal> signals = new ArrayList<>();
    List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs = new ArrayList<>();

    PatientCareProfileResponseData profile = preloadedProfile != null ? preloadedProfile : getProfile();
    RecordEntity focusRecord = resolveContextRecord(profileId, recordId);

    for (String note : profile.redFlagNotes()) {
      if (signals.size() >= MAX_RISK_ITEMS) {
        break;
      }
      signals.add(new PatientCareRiskOverviewResponseData.RiskSignal(
          "watch",
          "长期红旗提醒",
          note,
          "如出现相关信号，请尽快联系医生或线下就医。"));
      evidenceRefs.add(new PatientCareRiskOverviewResponseData.EvidenceItem(
          "patient_memory",
          "患者长期提醒",
          note,
          "慢病长期画像",
          "medium",
          "CARE_MEMORY"));
    }

    if (focusRecord != null) {
      appendRecordRiskSignals(focusRecord, signals, evidenceRefs);
    }
    appendTaskRiskSignals(signals, evidenceRefs);
    appendSymptomRiskSignals(signals, evidenceRefs);

    String riskLevel = deriveRiskLevel(signals);
    String summary = switch (riskLevel) {
      case "alert" -> "存在需要优先处理的红旗信号，建议尽快就医或加快复诊。";
      case "warning" -> "当前存在需关注的随访风险，建议尽快完成复查或与医生确认。";
      case "watch" -> "当前以持续观察和按计划随访为主。";
      default -> "当前未发现明显高优先级风险，可按既定计划随访。";
    };

    return new RiskComputation(riskLevel, summary, trimSignals(signals), trimEvidence(evidenceRefs));
  }

  private void appendTaskRiskSignals(
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals,
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {
    LocalDate today = LocalDate.now();
    List<FollowUpTaskEntity> tasks = followUpTaskMapper.selectList(new LambdaQueryWrapper<FollowUpTaskEntity>()
        .eq(FollowUpTaskEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(FollowUpTaskEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(FollowUpTaskEntity::getStatus, "OPEN")
        .orderByAsc(FollowUpTaskEntity::getDueDate)
        .last("limit 3"));
    for (FollowUpTaskEntity task : tasks) {
      if (task.getDueDate() == null || task.getDueDate().isAfter(today)) {
        continue;
      }
      signals.add(new PatientCareRiskOverviewResponseData.RiskSignal(
          task.getDueDate().isBefore(today) ? "warning" : "watch",
          "随访事项到期",
          task.getTitle() + " 已到期或应于今天处理。",
          "建议尽快完成该事项或更新计划。"));
      evidenceRefs.add(new PatientCareRiskOverviewResponseData.EvidenceItem(
          "follow_up_task",
          task.getTitle(),
          task.getNotes() == null ? "随访任务来自慢病行动清单。" : task.getNotes(),
          task.getDueDate() == null ? "随访任务" : "截止日期 " + task.getDueDate(),
          "high",
          "CARE_PLAN"));
    }
  }

  private void appendSymptomRiskSignals(
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals,
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {
    List<SymptomLogEntity> logs = symptomLogMapper.selectList(new LambdaQueryWrapper<SymptomLogEntity>()
        .eq(SymptomLogEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(SymptomLogEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByDesc(SymptomLogEntity::getRecordedAt)
        .last("limit 3"));
    for (SymptomLogEntity log : logs) {
      String severity = normalizeSeverity(log.getAlertLevel());
      if ("info".equals(severity)) {
        continue;
      }
      signals.add(new PatientCareRiskOverviewResponseData.RiskSignal(
          severity,
          "近期症状/体征提醒",
          symptomSummary(log),
          "建议结合症状持续时间和伴随表现，必要时联系医生。"));
      evidenceRefs.add(new PatientCareRiskOverviewResponseData.EvidenceItem(
          "symptom_log",
          log.getLabel(),
          symptomSummary(log),
          log.getRecordedAt() == null ? "症状记录" : String.valueOf(log.getRecordedAt()),
          "medium",
          "PATIENT_REPORTED"));
    }
  }

  private void appendRecordRiskSignals(
      RecordEntity focusRecord,
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals,
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {
    RecordDetail detail;
    try {
      detail = recordService.fetchRecord(focusRecord.getId());
    } catch (IllegalArgumentException error) {
      return;
    }

    for (RecordDetail.CombinationAnalysisItem item : detail.combinationAnalysis()) {
      String severity = normalizeSeverity(item.severity());
      if ("info".equals(severity)) {
        continue;
      }
      signals.add(new PatientCareRiskOverviewResponseData.RiskSignal(
          severity,
          item.name(),
          item.summary(),
          TextUtils.trimToNull(item.suggestion()) != null ? item.suggestion() : "建议结合医生意见安排复查。"));
      evidenceRefs.add(new PatientCareRiskOverviewResponseData.EvidenceItem(
          "rule_engine",
          item.name(),
          item.detail() == null ? item.summary() : item.detail(),
          focusRecord.getTitle() == null ? "当前报告" : focusRecord.getTitle(),
          "high",
          "RULE_CONCLUSION"));
    }

    RecordTrendData trendData;
    try {
      trendData = recordService.fetchTrend(focusRecord.getId(), 3);
    } catch (IllegalArgumentException error) {
      return;
    }
    appendTrendSignals(trendData, signals, evidenceRefs);
  }

  private void appendTrendSignals(
      RecordTrendData trendData,
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals,
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {
    List<TrendSnapshot> snapshots = trendData == null || trendData.snapshots() == null ? List.of() : trendData.snapshots();
    if (snapshots.size() < 2) {
      return;
    }
    TrendSnapshot previous = snapshots.get(Math.max(0, snapshots.size() - 2));
    TrendSnapshot current = snapshots.get(snapshots.size() - 1);
    Map<String, TrendField> previousFields = previous.fields().stream()
        .filter(field -> TextUtils.trimToNull(field.name()) != null)
        .collect(java.util.stream.Collectors.toMap(
            field -> field.name().trim(),
            field -> field,
            (left, right) -> left));

    for (TrendField currentField : current.fields()) {
      TrendField previousField = previousFields.get(TextUtils.trimToNull(currentField.name()));
      if (previousField == null) {
        continue;
      }
      boolean repeatedAbnormal = isAbnormal(currentField.resultState()) && isAbnormal(previousField.resultState());
      boolean worsening = isWorsening(previousField, currentField);
      if (!repeatedAbnormal && !worsening) {
        continue;
      }
      String detail = currentField.name() + " 连续异常，最近值 " + currentField.value()
          + nullSafeUnit(currentField.unit())
          + "，上一份为 " + previousField.value() + nullSafeUnit(previousField.unit()) + "。";
      signals.add(new PatientCareRiskOverviewResponseData.RiskSignal(
          worsening ? "warning" : "watch",
          "趋势监测提醒",
          detail,
          "建议结合既往病史安排更快复查或门诊随访。"));
      evidenceRefs.add(new PatientCareRiskOverviewResponseData.EvidenceItem(
          "trend_monitor",
          currentField.name(),
          detail,
          previous.recordDate() + " -> " + current.recordDate(),
          worsening ? "high" : "medium",
          "TREND_INFERENCE"));
      if (signals.size() >= MAX_RISK_ITEMS) {
        break;
      }
    }
  }

  private RecordEntity resolveContextRecord(String profileId, String recordId) {
    if (TextUtils.trimToNull(recordId) != null) {
      UUID recordUuid = parseUuid(recordId, "INVALID_RECORD_ID", "recordId is invalid");
      RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
          .eq(RecordEntity::getId, recordUuid)
          .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
          .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
          .last("limit 1"));
      if (record == null) {
        throw new ResourceNotFoundException("RECORD_NOT_FOUND", "record not found");
      }
      return record;
    }
    if (TextUtils.trimToNull(profileId) == null) {
      return null;
    }
    UUID profileUuid = parseUuid(profileId, "INVALID_PROFILE_ID", "profileId is invalid");
    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, profileUuid)
        .eq(DiseaseProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(DiseaseProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (profile == null) {
      throw new ResourceNotFoundException("PROFILE_NOT_FOUND", "disease profile not found");
    }
    return recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(RecordEntity::getDiseaseProfileId, profileUuid)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .last("limit 1"));
  }

  private PatientCareProfileResponseData toProfileResponse(
      PatientCareProfileEntity entity,
      List<PatientCareProfileResponseData.RecentSymptomItem> recentSymptoms) {
    List<String> diagnosedConditions = readStringList(entity == null ? null : entity.getDiagnosedConditionsJson());
    List<String> allergies = readStringList(entity == null ? null : entity.getAllergiesJson());
    List<String> abnormalBaseline = readStringList(entity == null ? null : entity.getAbnormalBaselineJson());
    List<PatientCareProfileResponseData.MedicationItem> medications = readMedicationItems(entity == null ? null : entity.getCurrentMedicationsJson());
    List<String> careGoals = readStringList(entity == null ? null : entity.getCareGoalsJson());
    List<String> redFlagNotes = readStringList(entity == null ? null : entity.getRedFlagNotesJson());
    return new PatientCareProfileResponseData(
        new PatientCareProfileResponseData.BaselineSummary(
            diagnosedConditions,
            allergies,
            abnormalBaseline,
            TextUtils.trimToNull(entity == null ? null : entity.getDoctorInstructions()),
            recentSymptoms),
        medications,
        careGoals,
        redFlagNotes,
        entity == null || entity.getUpdatedAt() == null ? null : String.valueOf(entity.getUpdatedAt()));
  }

  private PatientCareProfileEntity currentCareProfile() {
    return patientCareProfileMapper.selectOne(new LambdaQueryWrapper<PatientCareProfileEntity>()
        .eq(PatientCareProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(PatientCareProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
  }

  private List<PatientCareProfileResponseData.RecentSymptomItem> listRecentSymptomItems(int limit) {
    return symptomLogMapper.selectList(new LambdaQueryWrapper<SymptomLogEntity>()
        .eq(SymptomLogEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(SymptomLogEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByDesc(SymptomLogEntity::getRecordedAt)
        .last("limit " + limit)).stream()
            .map(this::toRecentSymptomItem)
            .toList();
  }

  private List<PatientCareSymptomLogListResponseData.SymptomLogItem> listRecentSymptomLogs(int limit) {
    return symptomLogMapper.selectList(new LambdaQueryWrapper<SymptomLogEntity>()
        .eq(SymptomLogEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(SymptomLogEntity::getPatientId, tenantContextProvider.currentPatientId())
        .orderByDesc(SymptomLogEntity::getRecordedAt)
        .last("limit " + limit)).stream()
            .map(this::toSymptomItem)
            .toList();
  }

  private PatientCareFollowUpTaskListResponseData.TaskSummary toTaskSummary(FollowUpTaskEntity entity) {
    return new PatientCareFollowUpTaskListResponseData.TaskSummary(
        entity.getId().toString(),
        entity.getTitle(),
        entity.getDueDate() == null ? null : entity.getDueDate().toString(),
        TextUtils.trimToNull(entity.getPriority()),
        TextUtils.trimToNull(entity.getStatus()),
        TextUtils.trimToNull(entity.getNotes()),
        entity.getDiseaseProfileId() == null ? null : entity.getDiseaseProfileId().toString(),
        entity.getRecordId() == null ? null : entity.getRecordId().toString(),
        entity.getCreatedAt() == null ? null : String.valueOf(entity.getCreatedAt()));
  }

  private PatientCareProfileResponseData.RecentSymptomItem toRecentSymptomItem(SymptomLogEntity entity) {
    return new PatientCareProfileResponseData.RecentSymptomItem(
        entity.getId().toString(),
        entity.getLabel(),
        TextUtils.trimToNull(entity.getValue()),
        TextUtils.trimToNull(entity.getUnit()),
        TextUtils.trimToNull(entity.getAlertLevel()),
        TextUtils.trimToNull(entity.getNotes()),
        entity.getRecordedAt() == null ? null : String.valueOf(entity.getRecordedAt()));
  }

  private PatientCareSymptomLogListResponseData.SymptomLogItem toSymptomItem(SymptomLogEntity entity) {
    return new PatientCareSymptomLogListResponseData.SymptomLogItem(
        entity.getId().toString(),
        entity.getLabel(),
        TextUtils.trimToNull(entity.getValue()),
        TextUtils.trimToNull(entity.getUnit()),
        TextUtils.trimToNull(entity.getAlertLevel()),
        TextUtils.trimToNull(entity.getNotes()),
        entity.getRecordedAt() == null ? null : String.valueOf(entity.getRecordedAt()),
        entity.getDiseaseProfileId() == null ? null : entity.getDiseaseProfileId().toString());
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (Exception error) {
      throw new BusinessException("PATIENT_CARE_SERIALIZE_FAILED", "failed to serialize patient care data", error);
    }
  }

  private List<String> readStringList(String raw) {
    if (TextUtils.trimToNull(raw) == null) {
      return List.of();
    }
    try {
      List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
      return cleanStringList(parsed);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private List<PatientCareProfileResponseData.MedicationItem> readMedicationItems(String raw) {
    if (TextUtils.trimToNull(raw) == null) {
      return List.of();
    }
    try {
      List<PatientCareProfileResponseData.MedicationItem> parsed = objectMapper.readValue(
          raw,
          new TypeReference<List<PatientCareProfileResponseData.MedicationItem>>() {});
      return parsed.stream()
          .map(item -> new PatientCareProfileResponseData.MedicationItem(
              TextUtils.trimToNull(item.name()),
              TextUtils.trimToNull(item.dosage()),
              TextUtils.trimToNull(item.frequency()),
              TextUtils.trimToNull(item.purpose())))
          .filter(item -> TextUtils.trimToNull(item.name()) != null)
          .toList();
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private List<PatientCareProfileResponseData.MedicationItem> cleanMedicationInputs(
      List<UpdatePatientCareProfileRequest.MedicationItemInput> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }
    List<PatientCareProfileResponseData.MedicationItem> items = new ArrayList<>();
    for (UpdatePatientCareProfileRequest.MedicationItemInput input : inputs) {
      if (input == null || TextUtils.trimToNull(input.name()) == null) {
        continue;
      }
      items.add(new PatientCareProfileResponseData.MedicationItem(
          TextUtils.trimToNull(input.name()),
          TextUtils.trimToNull(input.dosage()),
          TextUtils.trimToNull(input.frequency()),
          TextUtils.trimToNull(input.purpose())));
    }
    return items;
  }

  private List<String> cleanStringList(List<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> cleaned = new LinkedHashSet<>();
    for (String raw : rawValues) {
      String normalized = TextUtils.trimToNull(raw);
      if (normalized != null) {
        cleaned.add(normalized);
      }
    }
    return List.copyOf(cleaned);
  }

  private UUID resolveOptionalProfileId(String rawProfileId) {
    String profileId = TextUtils.trimToNull(rawProfileId);
    if (profileId == null) {
      return null;
    }
    UUID profileUuid = parseUuid(profileId, "INVALID_PROFILE_ID", "profileId is invalid");
    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, profileUuid)
        .eq(DiseaseProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(DiseaseProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (profile == null) {
      throw new ResourceNotFoundException("PROFILE_NOT_FOUND", "disease profile not found");
    }
    return profileUuid;
  }

  private UUID resolveOptionalRecordId(String rawRecordId) {
    String recordId = TextUtils.trimToNull(rawRecordId);
    if (recordId == null) {
      return null;
    }
    UUID recordUuid = parseUuid(recordId, "INVALID_RECORD_ID", "recordId is invalid");
    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordUuid)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (record == null) {
      throw new ResourceNotFoundException("RECORD_NOT_FOUND", "record not found");
    }
    return recordUuid;
  }

  private UUID parseUuid(String rawValue, String code, String message) {
    try {
      return UUID.fromString(rawValue);
    } catch (Exception error) {
      throw new BusinessException(code, message);
    }
  }

  private LocalDate parseOptionalDate(String rawDate) {
    String value = TextUtils.trimToNull(rawDate);
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (Exception error) {
      throw new BusinessException("INVALID_DATE", "date must use YYYY-MM-DD");
    }
  }

  private LocalDateTime parseOptionalDateTime(String rawDateTime, LocalDateTime fallback) {
    String value = TextUtils.trimToNull(rawDateTime);
    if (value == null) {
      return fallback;
    }
    try {
      return OffsetDateTime.parse(value).toLocalDateTime();
    } catch (Exception ignored) {
      try {
        return LocalDateTime.parse(value);
      } catch (Exception error) {
        throw new BusinessException("INVALID_RECORDED_AT", "recordedAt must be ISO datetime");
      }
    }
  }

  private int clampLimit(Integer rawLimit, int fallback, int max) {
    int value = rawLimit == null ? fallback : rawLimit;
    return Math.max(1, Math.min(value, max));
  }

  private String normalizePriority(String rawPriority) {
    String priority = TextUtils.trimToNull(rawPriority);
    if (priority == null) {
      return "MEDIUM";
    }
    return switch (priority.trim().toUpperCase(Locale.ROOT)) {
      case "LOW", "MEDIUM", "HIGH" -> priority.trim().toUpperCase(Locale.ROOT);
      default -> throw new BusinessException("INVALID_PRIORITY", "priority must be LOW, MEDIUM, or HIGH");
    };
  }

  private String normalizeTaskStatus(String rawStatus) {
    String status = TextUtils.trimToNull(rawStatus);
    if (status == null) {
      return "OPEN";
    }
    return switch (status.trim().toUpperCase(Locale.ROOT)) {
      case "OPEN", "DONE", "CANCELED" -> status.trim().toUpperCase(Locale.ROOT);
      default -> throw new BusinessException("INVALID_TASK_STATUS", "status must be OPEN, DONE, or CANCELED");
    };
  }

  private String normalizeTaskStatusFilter(String rawStatus) {
    if (TextUtils.trimToNull(rawStatus) == null) {
      return null;
    }
    return normalizeTaskStatus(rawStatus);
  }

  private String normalizeAlertLevel(String rawAlertLevel) {
    String alertLevel = TextUtils.trimToNull(rawAlertLevel);
    if (alertLevel == null) {
      return "NORMAL";
    }
    return switch (alertLevel.trim().toUpperCase(Locale.ROOT)) {
      case "NORMAL", "WATCH", "WARNING", "ALERT" -> alertLevel.trim().toUpperCase(Locale.ROOT);
      default -> throw new BusinessException("INVALID_ALERT_LEVEL", "alertLevel must be NORMAL, WATCH, WARNING, or ALERT");
    };
  }

  private String deriveRiskLevel(List<PatientCareRiskOverviewResponseData.RiskSignal> signals) {
    int maxRank = signals.stream().mapToInt(signal -> switch (normalizeSeverity(signal.severity())) {
      case "alert" -> 3;
      case "warning" -> 2;
      case "watch" -> 1;
      default -> 0;
    }).max().orElse(0);
    return switch (maxRank) {
      case 3 -> "alert";
      case 2 -> "warning";
      case 1 -> "watch";
      default -> "routine";
    };
  }

  private String normalizeSeverity(String rawSeverity) {
    String severity = TextUtils.trimToNull(rawSeverity);
    if (severity == null) {
      return "info";
    }
    return switch (severity.toLowerCase(Locale.ROOT)) {
      case "alert", "high" -> "alert";
      case "warning", "warn" -> "warning";
      case "watch" -> "watch";
      default -> "info";
    };
  }

  private boolean isAbnormal(String resultState) {
    String normalized = TextUtils.trimToNull(resultState);
    if (normalized == null) {
      return false;
    }
    return switch (normalized.toLowerCase(Locale.ROOT)) {
      case "high", "low", "threshold" -> true;
      default -> false;
    };
  }

  private boolean isWorsening(TrendField previous, TrendField current) {
    if (previous.numericValue() == null || current.numericValue() == null) {
      return false;
    }
    String currentState = TextUtils.trimToNull(current.resultState());
    if ("high".equalsIgnoreCase(currentState)) {
      return current.numericValue() > previous.numericValue();
    }
    if ("low".equalsIgnoreCase(currentState)) {
      return current.numericValue() < previous.numericValue();
    }
    if ("threshold".equalsIgnoreCase(currentState) && current.referenceLowerBound() != null) {
      return current.numericValue() > previous.numericValue();
    }
    return false;
  }

  private String symptomSummary(SymptomLogEntity log) {
    String base = log.getLabel();
    if (TextUtils.trimToNull(log.getValue()) != null) {
      base = base + "：" + log.getValue() + nullSafeUnit(log.getUnit());
    }
    if (TextUtils.trimToNull(log.getNotes()) != null) {
      base = base + "（" + log.getNotes() + "）";
    }
    return base;
  }

  private String nullSafeUnit(String unit) {
    return TextUtils.trimToNull(unit) == null ? "" : unit;
  }

  private List<PatientCareRiskOverviewResponseData.RiskSignal> trimSignals(
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals) {
    if (signals.size() <= MAX_RISK_ITEMS) {
      return List.copyOf(signals);
    }
    return List.copyOf(signals.subList(0, MAX_RISK_ITEMS));
  }

  private List<PatientCareRiskOverviewResponseData.EvidenceItem> trimEvidence(
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {
    if (evidenceRefs.size() <= MAX_RISK_ITEMS + 2) {
      return List.copyOf(evidenceRefs);
    }
    return List.copyOf(evidenceRefs.subList(0, MAX_RISK_ITEMS + 2));
  }

  private record RiskComputation(
      String riskLevel,
      String summary,
      List<PatientCareRiskOverviewResponseData.RiskSignal> signals,
      List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {}
}
