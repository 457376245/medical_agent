package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileContextResponse;
import com.medical.agent.domain.dto.response.AgentDiseaseProfileSummary;
import com.medical.agent.domain.dto.response.AgentKeyFieldSummary;
import com.medical.agent.domain.dto.response.AgentRecordContextData;
import com.medical.agent.domain.dto.response.AgentRecordContextSummary;
import com.medical.agent.domain.dto.response.AgentTrendSnapshotSummary;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportAnalysisResult;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@Tag(name = "Agent 病例上下文服务", description = "为 Agent 提供疾病档案聚合上下文")
public class AgentDiseaseProfileContextService {
  private static final int RECENT_RECORD_LIMIT = 5;
  private static final int MAX_KEY_FIELDS = 8;
  private static final int MAX_TREND_SNAPSHOTS = 3;

  private final DiseaseProfileMapper diseaseProfileMapper;
  private final RecordMapper recordMapper;
  private final ParseJobMapper parseJobMapper;
  private final RecordService recordService;
  private final ReportAnalysisService reportAnalysisService;

  public AgentDiseaseProfileContextService(
      DiseaseProfileMapper diseaseProfileMapper,
      RecordMapper recordMapper,
      ParseJobMapper parseJobMapper,
      RecordService recordService,
      ReportAnalysisService reportAnalysisService) {
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.recordMapper = recordMapper;
    this.parseJobMapper = parseJobMapper;
    this.recordService = recordService;
    this.reportAnalysisService = reportAnalysisService;
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
    if (hasText(recordId)) {
      RecordEntity focusRecord = getRecordInProfileOrThrow(profileUuid, recordId);
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

      recordSummary = new AgentRecordContextData(trimToNull(detail.summary()), analysis, keyFields);
    }

    return new AgentDiseaseProfileContextResponse(
        profileSummary,
        selectedRecord,
        summarizeRecentRecords(profileRecords),
        recordSummary,
        trendSummary,
        partial ? "PARTIAL" : "READY",
        warnings);
  }

  private DiseaseProfileEntity getProfileOrThrow(UUID profileUuid) {
    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, profileUuid)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .last("limit 1"));
    if (profile == null) {
      throw new ContextException(404, "PROFILE_NOT_FOUND", "disease profile not found");
    }
    return profile;
  }

  private List<RecordEntity> listProfileRecords(UUID profileUuid) {
    return recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .eq(RecordEntity::getDiseaseProfileId, profileUuid)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt));
  }

  private RecordEntity getRecordInProfileOrThrow(UUID profileUuid, String recordId) {
    UUID recordUuid = parseUuidOrThrow(recordId, 400, "INVALID_RECORD_ID", "recordId is invalid");
    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordUuid)
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
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
      return trimToNull(analysisResult.content());
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
          trimToNull(readText(fieldNode, "unit")),
          trimToNull(readText(fieldNode, "referenceRange"))));
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

  private String trimToNull(String value) {
    return hasText(value) ? value.trim() : null;
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

