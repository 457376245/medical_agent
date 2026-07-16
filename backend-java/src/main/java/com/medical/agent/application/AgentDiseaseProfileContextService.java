package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileContextResponse;
import com.medical.agent.domain.dto.response.AgentContextEvidence;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileSummary;
import com.medical.agent.domain.dto.response.AgentKeyFieldSummary;
import com.medical.agent.domain.dto.response.AgentRecordContextData;
import com.medical.agent.domain.dto.response.AgentRecordContextSummary;
import com.medical.agent.domain.dto.response.AgentTrendSnapshotSummary;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportAnalysisResult;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.StructuredResultEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "Agent 病例上下文服务", description = "为 Agent 提供疾病档案聚合上下文")
public class AgentDiseaseProfileContextService {
  private static final int RECENT_RECORD_LIMIT = 5;
  private static final int MAX_KEY_FIELDS = 8;
  private static final int MAX_TREND_SNAPSHOTS = 3;
  private static final int PER_CATEGORY_RECORD_LIMIT = 3;
  private static final int MAX_PROFILE_RECORDS = 20;

  private final DiseaseProfileMapper diseaseProfileMapper;
  private final RecordMapper recordMapper;
  private final ParseJobMapper parseJobMapper;
  private final StructuredResultMapper structuredResultMapper;
  private final RecordService recordService;
  private final ReportAnalysisService reportAnalysisService;
  private final PatientCareService patientCareService;
  private final PatientMemoryService patientMemoryService;
  private final TenantContextProvider tenantContextProvider;
  private final ObjectMapper objectMapper;

  public AgentDiseaseProfileContextService(
      DiseaseProfileMapper diseaseProfileMapper,
      RecordMapper recordMapper,
      ParseJobMapper parseJobMapper,
      StructuredResultMapper structuredResultMapper,
      RecordService recordService,
      ReportAnalysisService reportAnalysisService,
      PatientCareService patientCareService,
      PatientMemoryService patientMemoryService,
      TenantContextProvider tenantContextProvider,
      ObjectMapper objectMapper) {
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.recordMapper = recordMapper;
    this.parseJobMapper = parseJobMapper;
    this.structuredResultMapper = structuredResultMapper;
    this.recordService = recordService;
    this.reportAnalysisService = reportAnalysisService;
    this.patientCareService = patientCareService;
    this.patientMemoryService = patientMemoryService;
    this.tenantContextProvider = tenantContextProvider;
    this.objectMapper = objectMapper;
  }

  @Operation(summary = "聚合 Agent 病例上下文", description = "返回疾病级摘要、可选报告摘要、趋势和告警")
  public AgentDiseaseProfileContextResponse fetchProfileContext(String profileId, String recordId) {
    UUID profileUuid = parseUuidOrThrow(profileId, 400, "INVALID_PROFILE_ID", "profileId is invalid");
    DiseaseProfileEntity profile = getProfileOrThrow(profileUuid);
    List<RecordEntity> profileRecords = listProfileRecords(profileUuid);

    AgentDiseaseProfileSummary profileSummary = new AgentDiseaseProfileSummary(
        profileUuid.toString(),
        trimOrDefault(profile.getName(), "未分类疾病"),
        profileRecords.size(),
        profileRecords.isEmpty() ? null : String.valueOf(profileRecords.get(0).getRecordDate()));

    List<String> warnings = new ArrayList<>();
    boolean partial = false;
    if (profileRecords.isEmpty() && !hasText(recordId)) {
      partial = true;
      warnings.add("该疾病档案暂无记录。");
    }

    AgentRecordContextSummary selectedRecord = null;
    AgentRecordContextData recordSummary = null;
    List<AgentTrendSnapshotSummary> trendSummary = List.of();
    PatientCareProfileResponseData careProfile = patientCareService.getProfile();
    PatientCareFollowUpTaskListResponseData followUpTasks = patientCareService.listFollowUpTasks("OPEN", 5);
    String riskRecordId = null;

    if (hasText(recordId)) {
      // 有明确的 recordId：聚焦单份报告
      RecordEntity focusRecord = getRecordInProfileOrThrow(profileUuid, recordId);
      riskRecordId = focusRecord.getId().toString();
      selectedRecord = toRecordSummary(focusRecord);

      RecordDetail detail = recordService.fetchRecord(focusRecord.getId());
      List<AgentKeyFieldSummary> keyFields = extractKeyFields(detail.structuredResult().payload());
      if (!"SUCCESS".equalsIgnoreCase(detail.parseStatus()) || keyFields.isEmpty()) {
        partial = true;
        warnings.add("报告解析尚未完成或结构化字段为空。");
      }

      String analysis = tryLoadAnalysis(focusRecord.getId(), warnings);
      if (analysis == null) {
        partial = true;
      }

      trendSummary = tryLoadTrendSummary(focusRecord.getId(), warnings);
      if (trendSummary.isEmpty()) {
        partial = true;
      }

      recordSummary = new AgentRecordContextData(
          TextUtils.trimToNull(detail.summary()),
          analysis,
          keyFields,
          detail.ultrasoundFollowUp());
    } else if (!profileRecords.isEmpty()) {
      // 无 recordId：聚合各分类下最近的报告数据
      List<AgentKeyFieldSummary> aggregatedKeyFields = aggregateKeyFieldsByCategory(profileRecords, warnings);
      trendSummary = aggregateTrendSummaryByCategory(profileRecords, warnings);

      if (aggregatedKeyFields.isEmpty()) {
        partial = true;
        warnings.add("所有报告均未完成解析或无结构化字段。");
      }
      if (trendSummary.isEmpty()) {
        partial = true;
        warnings.add("趋势摘要暂不可用。");
      }

      // 不设置 selectedRecord 和 analysis，因为用户没有聚焦特定报告
      recordSummary = new AgentRecordContextData(null, null, aggregatedKeyFields, null);
    }
    PatientCareRiskOverviewResponseData riskOverview = patientCareService.getRiskOverview(profileId, riskRecordId, careProfile);
    var pendingMemories = patientMemoryService.listPendingForAgent(profileId, riskRecordId, 5);
    var confirmedMemories = patientMemoryService.listCurrentForAgent(profileId, riskRecordId, 10);
    List<AgentContextEvidence> evidenceLedger = buildEvidenceLedger(
        profileSummary, selectedRecord, recordSummary, trendSummary, careProfile, riskOverview,
        confirmedMemories, pendingMemories);
    String contextRevision = contextRevision(java.util.Arrays.asList(
        profileSummary, selectedRecord, recordSummary, trendSummary, careProfile,
        followUpTasks, riskOverview, confirmedMemories, pendingMemories));

    return new AgentDiseaseProfileContextResponse(
        profileSummary,
        selectedRecord,
        summarizeRecentRecords(profileRecords),
        recordSummary,
        trendSummary,
        careProfile.patientBaseline(),
        careProfile.currentMedications(),
        careProfile.careGoals(),
        careProfile.personalContext(),
        followUpTasks.tasks(),
        riskOverview.signals(),
        riskOverview.evidenceRefs(),
        confirmedMemories,
        pendingMemories,
        partial ? "PARTIAL" : "READY",
        warnings,
        contextRevision,
        Instant.now().toString(),
        evidenceLedger);
  }

  private List<AgentContextEvidence> buildEvidenceLedger(
      AgentDiseaseProfileSummary profile,
      AgentRecordContextSummary selectedRecord,
      AgentRecordContextData recordSummary,
      List<AgentTrendSnapshotSummary> trends,
      PatientCareProfileResponseData careProfile,
      PatientCareRiskOverviewResponseData risks,
      List<com.medical.agent.domain.dto.response.PatientMemoryEntryResponseData> confirmedMemories,
      List<com.medical.agent.domain.dto.response.PatientMemoryEntryResponseData> pendingMemories) {
    List<AgentContextEvidence> result = new ArrayList<>();
    String recordRef = selectedRecord == null ? profile.id() : selectedRecord.id();
    String observedAt = selectedRecord == null ? profile.latestRecordAt() : selectedRecord.recordDate();
    if (recordSummary != null) {
      for (AgentKeyFieldSummary field : recordSummary.keyFields()) {
        addEvidence(result, "REPORT_FIELD", field.name() + "=" + field.value(), "RECORD",
            recordRef, observedAt, observedAt, "VERIFIED");
      }
    }
    for (AgentTrendSnapshotSummary trend : trends) {
      addEvidence(result, "TREND", trend.summary(), "RECORD", trend.recordId(),
          trend.recordDate(), trend.recordDate(), "VERIFIED");
    }
    String careUpdatedAt = careProfile.updatedAt();
    for (String allergy : careProfile.patientBaseline().allergies()) {
      addEvidence(result, "ALLERGY", allergy, "CARE_PROFILE", "patient-care-profile",
          careUpdatedAt, careUpdatedAt, "CONFIRMED");
    }
    for (PatientCareProfileResponseData.MedicationItem medication : careProfile.currentMedications()) {
      addEvidence(result, "MEDICATION", medication.name(), "CARE_PROFILE", "patient-care-profile",
          careUpdatedAt, careUpdatedAt, "CONFIRMED");
    }
    for (PatientCareRiskOverviewResponseData.RiskSignal risk : risks.signals()) {
      addEvidence(result, "RED_FLAG", risk.title(), "RULE_ENGINE", "risk-overview",
          careUpdatedAt, careUpdatedAt, "VERIFIED");
    }
    for (var memory : confirmedMemories) {
      addEvidence(result, "MEMORY", memory.fieldPath() + ":" + memory.valueText(),
          memory.sourceType(), memory.id(), memory.validFrom(), memory.updatedAt(), "CONFIRMED");
    }
    for (var memory : pendingMemories) {
      addEvidence(result, "MEMORY", memory.fieldPath() + ":" + memory.valueText(),
          memory.sourceType(), memory.id(), memory.createdAt(), memory.updatedAt(), "PENDING");
    }
    return result;
  }

  private void addEvidence(
      List<AgentContextEvidence> target,
      String category,
      String summary,
      String sourceType,
      String sourceRef,
      String observedAt,
      String updatedAt,
      String verificationStatus) {
    if (!hasText(summary)) {
      return;
    }
    String seed = String.join("|", category, String.valueOf(sourceRef), summary);
    target.add(new AgentContextEvidence(
        "E-" + sha256(seed).substring(0, 12), category, summary, sourceType, sourceRef,
        observedAt, updatedAt, verificationStatus));
  }

  private String contextRevision(Object value) {
    try {
      return sha256(objectMapper.writeValueAsString(value));
    } catch (Exception error) {
      return sha256(String.valueOf(value));
    }
  }

  static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private DiseaseProfileEntity getProfileOrThrow(UUID profileUuid) {
    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, profileUuid)
        .eq(DiseaseProfileEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(DiseaseProfileEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (profile == null) {
      throw new ContextException(404, "PROFILE_NOT_FOUND", "disease profile not found");
    }
    return profile;
  }

  private List<RecordEntity> listProfileRecords(UUID profileUuid) {
    return recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(RecordEntity::getDiseaseProfileId, profileUuid)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt)
        .last("limit " + MAX_PROFILE_RECORDS));
  }

  private RecordEntity getRecordInProfileOrThrow(UUID profileUuid, String recordId) {
    UUID recordUuid = parseUuidOrThrow(recordId, 400, "INVALID_RECORD_ID", "recordId is invalid");
    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordUuid)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (record == null) {
      throw new ContextException(404, "RECORD_NOT_FOUND", "record not found");
    }
    if (!profileUuid.equals(record.getDiseaseProfileId())) {
      throw new ContextException(400, "RECORD_PROFILE_MISMATCH", "record does not belong to the requested disease profile");
    }
    return record;
  }

  private List<AgentRecordContextSummary> summarizeRecentRecords(List<RecordEntity> records) {
    if (records.isEmpty()) {
      return List.of();
    }
    List<AgentRecordContextSummary> result = new ArrayList<>();
    for (RecordEntity record : records.subList(0, Math.min(records.size(), RECENT_RECORD_LIMIT))) {
      result.add(toRecordSummary(record));
    }
    return result;
  }

  private AgentRecordContextSummary toRecordSummary(RecordEntity record) {
    return new AgentRecordContextSummary(
        record.getId().toString(),
        trimOrDefault(record.getTitle(), "未命名报告"),
        String.valueOf(record.getRecordDate()),
        String.valueOf(record.getSourceType()),
        resolveParseStatus(record.getId()));
  }

  private String tryLoadAnalysis(UUID recordId, List<String> warnings) {
    try {
      ReportAnalysisResult analysisResult = reportAnalysisService.getOrGenerate(recordId);
      return TextUtils.trimToNull(analysisResult.content());
    } catch (ReportAnalysisService.AnalysisNotReadyException ignored) {
      warnings.add("当前报告分析尚未就绪。");
      return null;
    } catch (IllegalStateException ignored) {
      warnings.add("当前报告分析暂不可用。");
      return null;
    }
  }

  private List<AgentTrendSnapshotSummary> tryLoadTrendSummary(UUID recordId, List<String> warnings) {
    try {
      return summarizeTrendSnapshots(recordService.fetchTrend(recordId, MAX_TREND_SNAPSHOTS));
    } catch (IllegalArgumentException ignored) {
      warnings.add("趋势摘要暂不可用。");
      return List.of();
    }
  }

  private String resolveParseStatus(UUID recordId) {
    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getRecordId, recordId)
        .orderByDesc(ParseJobEntity::getUpdatedAt)
        .orderByDesc(ParseJobEntity::getCreatedAt)
        .last("limit 1"));
    return jobs.isEmpty() ? "NOT_PARSED" : String.valueOf(jobs.get(0).getStatus());
  }

  private List<AgentKeyFieldSummary> extractKeyFields(JsonNode payload) {
    JsonNode fieldNodes = payload == null ? null : payload.path("fields");
    if (fieldNodes == null || !fieldNodes.isArray()) {
      return List.of();
    }
    List<AgentKeyFieldSummary> fields = new ArrayList<>();
    for (int i = 0; i < Math.min(fieldNodes.size(), MAX_KEY_FIELDS); i++) {
      JsonNode fieldNode = fieldNodes.get(i);
      String name = readText(fieldNode, "name");
      String value = readText(fieldNode, "value");
      if (name.isBlank() || value.isBlank()) {
        continue;
      }
      fields.add(new AgentKeyFieldSummary(
          name,
          value,
          TextUtils.trimToNull(readText(fieldNode, "unit")),
          TextUtils.trimToNull(readText(fieldNode, "referenceRange"))));
    }
    return fields;
  }

  private List<AgentTrendSnapshotSummary> summarizeTrendSnapshots(RecordTrendData trendData) {
    if (trendData == null || trendData.snapshots() == null || trendData.snapshots().isEmpty()) {
      return List.of();
    }
    List<AgentTrendSnapshotSummary> snapshots = new ArrayList<>();
    for (TrendSnapshot snapshot : trendData.snapshots().subList(0, Math.min(trendData.snapshots().size(), MAX_TREND_SNAPSHOTS))) {
      snapshots.add(new AgentTrendSnapshotSummary(
          snapshot.recordId(),
          snapshot.recordDate(),
          trimOrDefault(snapshot.title(), "未命名报告"),
          summarizeTrendFields(snapshot.fields())));
    }
    return snapshots;
  }

  private String summarizeTrendFields(List<TrendField> fields) {
    if (fields == null || fields.isEmpty()) {
      return "暂无关键字段";
    }
    StringJoiner joiner = new StringJoiner("；");
    for (TrendField field : fields.subList(0, Math.min(fields.size(), 3))) {
      if (field == null || !hasText(field.name()) || !hasText(field.value())) {
        continue;
      }
      joiner.add(field.name() + ":" + field.value() + (field.unit() == null ? "" : field.unit()));
    }
    String summary = joiner.toString();
    return summary.isBlank() ? "暂无关键字段" : summary;
  }

  private UUID parseUuidOrThrow(String value, int httpStatus, String code, String message) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException error) {
      throw new ContextException(httpStatus, code, message);
    }
  }

  private String readText(JsonNode node, String fieldName) {
    if (node == null) {
      return "";
    }
    JsonNode value = node.path(fieldName);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String trimOrDefault(String value, String fallback) {
    return hasText(value) ? value.trim() : fallback;
  }

  // ---------------------------------------------------------------------------
  // Aggregation methods for disease-level context (no specific record selected)
  // ---------------------------------------------------------------------------

  /**
   * Aggregate key fields from the most recent records per source type.
   * Returns up to MAX_KEY_FIELDS unique field names with their latest values.
   */
  private List<AgentKeyFieldSummary> aggregateKeyFieldsByCategory(
      List<RecordEntity> profileRecords, List<String> warnings) {
    // Group records by sourceType
    Map<String, List<RecordEntity>> recordsBySourceType = profileRecords.stream()
        .collect(Collectors.groupingBy(
            r -> r.getSourceType() == null ? "未分类" : r.getSourceType(),
            LinkedHashMap::new,
            Collectors.toList()));

    List<AgentKeyFieldSummary> aggregatedFields = new ArrayList<>();
    Map<String, Boolean> seenFieldNames = new LinkedHashMap<>();

    for (Map.Entry<String, List<RecordEntity>> entry : recordsBySourceType.entrySet()) {
      String sourceType = entry.getKey();
      List<RecordEntity> categoryRecords = entry.getValue();

      // Take up to PER_CATEGORY_RECORD_LIMIT most recent records per category
      List<RecordEntity> recentRecords = categoryRecords.stream()
          .sorted((a, b) -> {
            int dateCompare = (b.getRecordDate() == null ? LocalDate.MIN : b.getRecordDate())
                .compareTo(a.getRecordDate() == null ? LocalDate.MIN : a.getRecordDate());
            if (dateCompare != 0) return dateCompare;
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
          })
          .limit(PER_CATEGORY_RECORD_LIMIT)
          .toList();

      // Fetch structured results for these records
      List<UUID> recordIds = recentRecords.stream().map(RecordEntity::getId).toList();
      Map<UUID, JsonNode> payloadLookup = fetchLatestPayloads(recordIds);

      // Extract key fields, preferring most recent values for each field name
      for (RecordEntity record : recentRecords) {
        JsonNode payload = payloadLookup.get(record.getId());
        if (payload == null) continue;

        JsonNode fieldNodes = payload.path("fields");
        if (!fieldNodes.isArray()) continue;

        for (JsonNode fieldNode : fieldNodes) {
          String name = readText(fieldNode, "name");
          String value = readText(fieldNode, "value");
          if (name.isBlank() || value.isBlank()) continue;

          // Skip if we already have this field from a more recent record
          if (seenFieldNames.containsKey(name)) continue;
          seenFieldNames.put(name, true);

          aggregatedFields.add(new AgentKeyFieldSummary(
              name + " [" + sourceType + "]",
              value,
              TextUtils.trimToNull(readText(fieldNode, "unit")),
              TextUtils.trimToNull(readText(fieldNode, "referenceRange"))));

          if (aggregatedFields.size() >= MAX_KEY_FIELDS) break;
        }
        if (aggregatedFields.size() >= MAX_KEY_FIELDS) break;
      }
      if (aggregatedFields.size() >= MAX_KEY_FIELDS) break;
    }

    return aggregatedFields;
  }

  /**
   * Aggregate trend summary from the most recent records per source type.
   * Returns up to MAX_TREND_SNAPSHOTS per category, labeled by category.
   */
  private List<AgentTrendSnapshotSummary> aggregateTrendSummaryByCategory(
      List<RecordEntity> profileRecords, List<String> warnings) {
    // Group records by sourceType
    Map<String, List<RecordEntity>> recordsBySourceType = profileRecords.stream()
        .collect(Collectors.groupingBy(
            r -> r.getSourceType() == null ? "未分类" : r.getSourceType(),
            LinkedHashMap::new,
            Collectors.toList()));

    List<AgentTrendSnapshotSummary> aggregatedTrends = new ArrayList<>();

    for (Map.Entry<String, List<RecordEntity>> entry : recordsBySourceType.entrySet()) {
      String sourceType = entry.getKey();
      List<RecordEntity> categoryRecords = entry.getValue();

      // Take up to PER_CATEGORY_RECORD_LIMIT most recent records per category
      List<RecordEntity> recentRecords = categoryRecords.stream()
          .sorted((a, b) -> {
            int dateCompare = (b.getRecordDate() == null ? LocalDate.MIN : b.getRecordDate())
                .compareTo(a.getRecordDate() == null ? LocalDate.MIN : a.getRecordDate());
            if (dateCompare != 0) return dateCompare;
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
          })
          .limit(PER_CATEGORY_RECORD_LIMIT)
          .toList();

      // Fetch structured results for these records
      List<UUID> recordIds = recentRecords.stream().map(RecordEntity::getId).toList();
      Map<UUID, JsonNode> payloadLookup = fetchLatestPayloads(recordIds);

      // Build trend snapshots for this category
      for (RecordEntity record : recentRecords) {
        JsonNode payload = payloadLookup.get(record.getId());
        String fieldSummary = summarizePayloadFields(payload);

        aggregatedTrends.add(new AgentTrendSnapshotSummary(
            record.getId().toString(),
            String.valueOf(record.getRecordDate()),
            trimOrDefault(record.getTitle(), "未命名报告") + " [" + sourceType + "]",
            fieldSummary));

        if (aggregatedTrends.size() >= MAX_TREND_SNAPSHOTS * 3) break;
      }
      if (aggregatedTrends.size() >= MAX_TREND_SNAPSHOTS * 3) break;
    }

    return aggregatedTrends;
  }

  /**
   * Fetch the latest structured result payloads for given record IDs.
   */
  private Map<UUID, JsonNode> fetchLatestPayloads(List<UUID> recordIds) {
    if (recordIds.isEmpty()) return Map.of();

    List<StructuredResultEntity> results = structuredResultMapper.selectList(
        new LambdaQueryWrapper<StructuredResultEntity>()
            .select(StructuredResultEntity::getRecordId,
                StructuredResultEntity::getPayloadJson,
                StructuredResultEntity::getRevision)
            .in(StructuredResultEntity::getRecordId, recordIds)
            .orderByDesc(StructuredResultEntity::getRevision));

    Map<UUID, JsonNode> payloadLookup = new LinkedHashMap<>();
    for (StructuredResultEntity result : results) {
      payloadLookup.putIfAbsent(result.getRecordId(), parsePayloadJson(result.getPayloadJson()));
    }
    return payloadLookup;
  }

  /**
   * Parse payload JSON into a JsonNode, returning empty object on failure.
   */
  private JsonNode parsePayloadJson(String payloadJson) {
    try {
      JsonNode parsed = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
      return parsed.isObject() ? parsed : objectMapper.createObjectNode();
    } catch (Exception ignored) {
      return objectMapper.createObjectNode();
    }
  }

  /**
   * Summarize fields from a payload into a compact string.
   */
  private String summarizePayloadFields(JsonNode payload) {
    if (payload == null) return "暂无关键字段";

    JsonNode fieldNodes = payload.path("fields");
    if (!fieldNodes.isArray() || fieldNodes.isEmpty()) return "暂无关键字段";

    StringJoiner joiner = new StringJoiner("；");
    int count = 0;
    for (JsonNode fieldNode : fieldNodes) {
      if (count >= 3) break;
      String name = readText(fieldNode, "name");
      String value = readText(fieldNode, "value");
      String unit = readText(fieldNode, "unit");
      if (name.isBlank() || value.isBlank()) continue;
      joiner.add(name + ":" + value + (unit.isBlank() ? "" : unit));
      count++;
    }
    String summary = joiner.toString();
    return summary.isBlank() ? "暂无关键字段" : summary;
  }

  public static final class ContextException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    public ContextException(int httpStatus, String code, String message) {
      super(message);
      this.httpStatus = httpStatus;
      this.code = code;
    }

    public int httpStatus() {
      return httpStatus;
    }

    public String code() {
      return code;
    }
  }
}

