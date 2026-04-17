package com.medical.agent.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.RecordAnalysisContext;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.StructuredResultData;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.ReportCategoryEntity;
import com.medical.agent.infrastructure.persistence.entity.StructuredResultEntity;
import com.medical.agent.infrastructure.persistence.mapper.AssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.DataRightsRequestMapper;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobAssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.ReportCategoryMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Service
@Tag(name = "记录服务", description = "负责记录与资产的核心编排，包括创建、查询、趋势分析、更新与级联删除")
public class RecordService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RecordService.class);
  private static final int MAX_REPORT_CATEGORY_NAME_LENGTH = 64;

  private final RecordMapper recordMapper;
  private final AssetMapper assetMapper;
  private final ParseJobMapper parseJobMapper;
  private final ParseJobAssetMapper parseJobAssetMapper;
  private final StructuredResultMapper structuredResultMapper;
  private final GeneratedOutputMapper generatedOutputMapper;
  private final DataRightsRequestMapper dataRightsRequestMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final ReportCategoryMapper reportCategoryMapper;
  private final ObjectMapper objectMapper;
  private final TenantContextProvider tenantContextProvider;
  private final StructuredFieldInterpreter structuredFieldInterpreter;

  public RecordService(
      RecordMapper recordMapper,
      AssetMapper assetMapper,
      ParseJobMapper parseJobMapper,
      ParseJobAssetMapper parseJobAssetMapper,
      StructuredResultMapper structuredResultMapper,
      GeneratedOutputMapper generatedOutputMapper,
      DataRightsRequestMapper dataRightsRequestMapper,
      DiseaseProfileMapper diseaseProfileMapper,
      ReportCategoryMapper reportCategoryMapper,
      ObjectMapper objectMapper,
      TenantContextProvider tenantContextProvider) {
    this.recordMapper = recordMapper;
    this.assetMapper = assetMapper;
    this.parseJobMapper = parseJobMapper;
    this.parseJobAssetMapper = parseJobAssetMapper;
    this.structuredResultMapper = structuredResultMapper;
    this.generatedOutputMapper = generatedOutputMapper;
    this.dataRightsRequestMapper = dataRightsRequestMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.reportCategoryMapper = reportCategoryMapper;
    this.objectMapper = objectMapper;
    this.tenantContextProvider = tenantContextProvider;
    this.structuredFieldInterpreter = new StructuredFieldInterpreter(objectMapper);
  }

  @Operation(summary = "按ID确保记录存在", description = "在最小参数场景下确保记录存在，不存在时按默认值创建")
  public UUID ensureRecord(UUID recordId) {
    return ensureRecord(recordId, null, null, null, null);
  }

  @Operation(summary = "按档案和日期确保记录存在", description = "在给定档案与日期条件下创建或补全记录基础信息")
  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title) {
    return ensureRecord(recordId, diseaseProfileId, reportDate, title, null);
  }

  @Operation(summary = "按完整元数据确保记录存在", description = "根据病种、日期、标题和来源类型幂等创建或更新记录主数据")
  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title,
      String sourceType) {
    UUID finalRecordId = recordId == null ? UUID.randomUUID() : recordId;
    LocalDate finalReportDate = reportDate == null ? LocalDate.now() : reportDate;
    String finalTitle = title == null || title.isBlank() ? "Imported record" : title;
    String normalizedSourceType = normalizeReportCategoryName(sourceType);
    if (normalizedSourceType != null) {
      ensureReportCategoryByName(normalizedSourceType);
    }

    RecordEntity existing = recordMapper.selectById(finalRecordId);
    if (existing == null) {
      RecordEntity toCreate = new RecordEntity();
      toCreate.setId(finalRecordId);
      toCreate.setTenantId(tenantContextProvider.currentTenantId());
      toCreate.setUserId(tenantContextProvider.currentUserId());
      toCreate.setPatientId(tenantContextProvider.currentPatientId());
      toCreate.setDiseaseProfileId(diseaseProfileId);
      toCreate.setRecordDate(finalReportDate);
      toCreate.setTitle(finalTitle);
      toCreate.setSourceType(normalizedSourceType);
      toCreate.setCreatedAt(LocalDateTime.now());
      toCreate.setUpdatedAt(LocalDateTime.now());
      recordMapper.insert(toCreate);
      return finalRecordId;
    }

    if (normalizedSourceType != null) {
      ensureReportCategoryByName(normalizedSourceType);
    }

    LambdaUpdateWrapper<RecordEntity> update = new LambdaUpdateWrapper<RecordEntity>()
        .eq(RecordEntity::getId, finalRecordId)
        .set(RecordEntity::getUpdatedAt, LocalDateTime.now());
    if (diseaseProfileId != null) {
      update.set(RecordEntity::getDiseaseProfileId, diseaseProfileId);
    }
    if (reportDate != null) {
      update.set(RecordEntity::getRecordDate, reportDate);
    }
    if (title != null && !title.isBlank()) {
      update.set(RecordEntity::getTitle, title);
    }
    if (normalizedSourceType != null) {
      update.set(RecordEntity::getSourceType, normalizedSourceType);
    }
    recordMapper.update(null, update);
    return finalRecordId;
  }

  @Operation(summary = "创建资产并绑定记录", description = "登记上传资产元信息并自动关联到记录，返回新资产ID")
  public UUID createAsset(
      String objectKey,
      String checksum,
      UUID recordId,
      String fileType,
      long fileSize,
      UUID diseaseProfileId,
      LocalDate reportDate,
      String title,
      String sourceType) {
    UUID assetId = UUID.randomUUID();
    AssetEntity asset = new AssetEntity();
    asset.setId(assetId);
    asset.setTenantId(tenantContextProvider.currentTenantId());
    asset.setRecordId(ensureRecord(recordId, diseaseProfileId, reportDate, title, sourceType));
    asset.setObjectKey(objectKey);
    asset.setFileType(fileType);
    asset.setFileSize(fileSize);
    asset.setChecksum(checksum);
    asset.setCreatedAt(LocalDateTime.now());
    assetMapper.insert(asset);
    return assetId;
  }

  @Operation(summary = "按ID列表查询资产引用", description = "按输入顺序返回资产引用信息，若资产不存在则抛出异常")
  public List<AssetRef> listAssetRefs(List<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }
    List<AssetEntity> assets = assetMapper.selectList(
        new LambdaQueryWrapper<AssetEntity>().in(AssetEntity::getId, assetIds));
    Map<UUID, AssetEntity> byId = new HashMap<>();
    for (AssetEntity asset : assets) {
      byId.put(asset.getId(), asset);
    }

    List<AssetRef> refs = new ArrayList<>();
    for (UUID assetId : assetIds) {
      AssetEntity asset = byId.get(assetId);
      if (asset == null) {
        throw new IllegalArgumentException("asset not found: " + assetId);
      }
      refs.add(new AssetRef(
          String.valueOf(asset.getId()),
          String.valueOf(asset.getObjectKey()),
          String.valueOf(asset.getFileType())));
    }
    return refs;
  }

  @Operation(summary = "获取记录详情", description = "查询记录、最新解析状态与结构化结果，组装为详情视图")
  public RecordDetail fetchRecord(UUID recordId) {
    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (record == null) {
      throw new IllegalArgumentException("record not found");
    }

    String summary = querySummary(recordId);
    String parseStatus = queryLatestParseStatus(recordId);
    StructuredResultData structuredResult = queryLatestStructuredResult(recordId);
    return new RecordDetail(recordId.toString(), summary, parseStatus, structuredResult);
  }

  @Operation(summary = "获取单条记录趋势数据", description = "围绕当前记录构建同病种同来源的时间窗口趋势快照")
  public RecordTrendData fetchTrend(UUID recordId, int limit) {
    RecordEntity currentRecord = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (currentRecord == null) {
      throw new IllegalArgumentException("record not found");
    }

    LambdaQueryWrapper<RecordEntity> scopedQuery = new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .eq(RecordEntity::getSourceType, currentRecord.getSourceType())
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt);
    if (currentRecord.getDiseaseProfileId() == null) {
      scopedQuery.isNull(RecordEntity::getDiseaseProfileId);
    } else {
      scopedQuery.eq(RecordEntity::getDiseaseProfileId, currentRecord.getDiseaseProfileId());
    }

    List<RecordEntity> scopedRecords = recordMapper.selectList(scopedQuery);
    int anchorIndex = -1;
    for (int i = 0; i < scopedRecords.size(); i++) {
      if (recordId.equals(scopedRecords.get(i).getId())) {
        anchorIndex = i;
        break;
      }
    }
    if (anchorIndex < 0) {
      throw new IllegalArgumentException("record not found in scoped records");
    }

    int normalizedLimit = Math.max(1, limit);
    int endExclusive = Math.min(scopedRecords.size(), anchorIndex + normalizedLimit);
    List<RecordEntity> window = new ArrayList<>(scopedRecords.subList(anchorIndex, endExclusive));
    Collections.reverse(window);

    List<UUID> windowRecordIds = window.stream().map(RecordEntity::getId).toList();
    Map<UUID, JsonNode> payloadLookup = new HashMap<>();
    if (!windowRecordIds.isEmpty()) {
      List<StructuredResultEntity> allResults = structuredResultMapper
          .selectList(new LambdaQueryWrapper<StructuredResultEntity>()
              .select(StructuredResultEntity::getRecordId, StructuredResultEntity::getPayloadJson,
                  StructuredResultEntity::getRevision)
              .in(StructuredResultEntity::getRecordId, windowRecordIds)
              .orderByDesc(StructuredResultEntity::getRevision));

      // Build map where only the latest revision is kept (since it's ordered by desc)
      for (StructuredResultEntity res : allResults) {
        payloadLookup.putIfAbsent(res.getRecordId(), parsePayload(res.getPayloadJson()));
      }
    }

    List<TrendSnapshot> snapshots = new ArrayList<>();
    for (RecordEntity row : window) {
      JsonNode payload = payloadLookup.getOrDefault(row.getId(), objectMapper.createObjectNode().putArray("fields"));
      snapshots.add(new TrendSnapshot(
          row.getId().toString(),
          String.valueOf(row.getRecordDate()),
          row.getTitle() == null ? "未命名报告" : row.getTitle(),
          String.valueOf(row.getSourceType()),
          extractTrendFields(payload)));
    }

    return new RecordTrendData(
        recordId.toString(),
        currentRecord.getSourceType(),
        currentRecord.getDiseaseProfileId() == null ? "unknown" : currentRecord.getDiseaseProfileId().toString(),
        normalizedLimit,
        snapshots);
  }

  @Transactional
  @Operation(summary = "删除记录及关联资源", description = "删除记录及其关联的解析任务、结构化结果、生成内容和资产映射")
  public boolean deleteRecord(UUID recordId) {
    dataRightsRequestMapper
        .delete(new LambdaQueryWrapper<com.medical.agent.infrastructure.persistence.entity.DataRightsRequestEntity>()
            .eq(com.medical.agent.infrastructure.persistence.entity.DataRightsRequestEntity::getRecordId, recordId));
    structuredResultMapper.delete(new LambdaQueryWrapper<StructuredResultEntity>()
        .eq(StructuredResultEntity::getRecordId, recordId));
    generatedOutputMapper.delete(new LambdaQueryWrapper<GeneratedOutputEntity>()
        .eq(GeneratedOutputEntity::getRecordId, recordId));

    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .select(ParseJobEntity::getId)
        .eq(ParseJobEntity::getRecordId, recordId));
    if (!jobs.isEmpty()) {
      List<UUID> jobIds = jobs.stream().map(ParseJobEntity::getId).toList();
      parseJobAssetMapper
          .delete(new LambdaQueryWrapper<com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity>()
              .in(com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity::getJobId, jobIds));
    }

    parseJobMapper.delete(new LambdaQueryWrapper<ParseJobEntity>().eq(ParseJobEntity::getRecordId, recordId));
    assetMapper.delete(new LambdaQueryWrapper<AssetEntity>().eq(AssetEntity::getRecordId, recordId));
    return recordMapper.deleteById(recordId) > 0;
  }

  @Operation(summary = "更新记录来源类型", description = "更新记录来源分类并按业务规则重建记录标题")
  public UpdateRecordSourceTypeResult updateSourceType(UUID recordId, String sourceType) {
    String normalizedSourceType = normalizeReportCategoryName(sourceType);
    if (normalizedSourceType == null) {
      throw new IllegalArgumentException("sourceType is required");
    }
    ensureReportCategoryByName(normalizedSourceType);

    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (record == null) {
      return new UpdateRecordSourceTypeResult(false, null, null, null, null);
    }

    String recordDate = String.valueOf(record.getRecordDate() == null ? LocalDate.now() : record.getRecordDate());
    String diseaseName = "未分类疾病";
    if (record.getDiseaseProfileId() != null) {
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(record.getDiseaseProfileId());
      if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
        diseaseName = profile.getName();
      }
    }

    String nextTitle = diseaseName + "-" + sourceTypeLabel(normalizedSourceType) + "-" + recordDate;
    int updated = recordMapper.update(null, new LambdaUpdateWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .set(RecordEntity::getSourceType, normalizedSourceType)
        .set(RecordEntity::getTitle, nextTitle)
        .set(RecordEntity::getUpdatedAt, LocalDateTime.now()));

    if (updated <= 0) {
      return new UpdateRecordSourceTypeResult(false, null, null, null, null);
    }
    return new UpdateRecordSourceTypeResult(true, normalizedSourceType, nextTitle, recordDate, diseaseName);
  }

  @Operation(summary = "按疾病档案查询资产对象键", description = "批量查询指定疾病档案下所有记录关联的对象存储键")
  public List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId) {
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .select(RecordEntity::getId)
        .eq(RecordEntity::getDiseaseProfileId, diseaseProfileId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId()));
    if (records.isEmpty()) {
      return List.of();
    }
    List<UUID> recordIds = records.stream().map(RecordEntity::getId).toList();
    List<AssetEntity> assets = assetMapper.selectList(new LambdaQueryWrapper<AssetEntity>()
        .select(AssetEntity::getObjectKey)
        .in(AssetEntity::getRecordId, recordIds));
    return assets.stream().map(AssetEntity::getObjectKey).filter(v -> v != null && !v.isBlank()).toList();
  }

  @Operation(summary = "获取记录分析上下文", description = "汇总记录、病种与结构化字段，构建分析引擎调用所需上下文")
  public Optional<RecordAnalysisContext> fetchRecordAnalysisContext(UUID recordId) {
    RecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .eq(RecordEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(RecordEntity::getPatientId, tenantContextProvider.currentPatientId())
        .last("limit 1"));
    if (record == null) {
      return Optional.empty();
    }

    String diseaseName = "未分类疾病";
    if (record.getDiseaseProfileId() != null) {
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(record.getDiseaseProfileId());
      if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
        diseaseName = profile.getName();
      }
    }

    String parseStatus = queryLatestParseStatus(recordId);
    List<StructuredResultEntity> rows = structuredResultMapper
        .selectList(new LambdaQueryWrapper<StructuredResultEntity>()
            .eq(StructuredResultEntity::getRecordId, recordId)
            .orderByDesc(StructuredResultEntity::getRevision)
            .last("limit 1"));
    StructuredResultData structured;
    if (rows.isEmpty()) {
      structured = new StructuredResultData("v1", 0, objectMapper.createObjectNode().putArray("fields"));
    } else {
      StructuredResultEntity latest = rows.get(0);
      structured = new StructuredResultData(
          latest.getSchemaVersion(),
          latest.getRevision() == null ? 0 : latest.getRevision(),
          parsePayloadOrEmptyFields(latest.getPayloadJson()));
    }

    return Optional.of(new RecordAnalysisContext(
        String.valueOf(record.getId()),
        record.getTitle() == null ? "未命名报告" : record.getTitle(),
        String.valueOf(record.getRecordDate()),
        String.valueOf(record.getSourceType()),
        diseaseName,
        parseStatus,
        structured));
  }

  @Transactional
  @Operation(summary = "应用自动分类", description = "MQ消费者调用，根据LLM分类结果更新记录的sourceType和标题")
  public void applyAutoClassification(UUID recordId, String sourceType) {
    String normalized = normalizeReportCategoryName(sourceType);
    if (normalized == null) {
      return;
    }

    RecordEntity record = recordMapper.selectById(recordId);
    if (record == null || record.getSourceType() != null) {
      return;
    }

    ensureReportCategoryByNameForRecord(normalized, record);

    String diseaseName = "未分类疾病";
    if (record.getDiseaseProfileId() != null) {
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(record.getDiseaseProfileId());
      if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
        diseaseName = profile.getName();
      }
    }
    String recordDate = String.valueOf(
        record.getRecordDate() == null ? LocalDate.now() : record.getRecordDate());
    String nextTitle = diseaseName + "-" + normalized + "-" + recordDate;

    recordMapper.update(null, new LambdaUpdateWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .isNull(RecordEntity::getSourceType)
        .set(RecordEntity::getSourceType, normalized)
        .set(RecordEntity::getTitle, nextTitle)
        .set(RecordEntity::getUpdatedAt, LocalDateTime.now()));
  }

  @Transactional
  @Operation(summary = "应用报告日期", description = "MQ消费者调用，根据LLM提取的报告日期更新记录")
  public void applyReportDate(UUID recordId, String reportDateStr) {
    if (reportDateStr == null || reportDateStr.isBlank()) {
      return;
    }

    LocalDate extractedDate;
    try {
      extractedDate = LocalDate.parse(reportDateStr);
    } catch (Exception ex) {
      LOGGER.warn("Failed to parse report date: {}", reportDateStr);
      return;
    }

    RecordEntity record = recordMapper.selectById(recordId);
    if (record == null) {
      return;
    }

    // Update record date and title
    String diseaseName = "未分类疾病";
    if (record.getDiseaseProfileId() != null) {
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(record.getDiseaseProfileId());
      if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
        diseaseName = profile.getName();
      }
    }
    String sourceType = record.getSourceType() != null ? record.getSourceType() : "报告";
    String nextTitle = diseaseName + "-" + sourceType + "-" + reportDateStr;

    recordMapper.update(null, new LambdaUpdateWrapper<RecordEntity>()
        .eq(RecordEntity::getId, recordId)
        .set(RecordEntity::getRecordDate, extractedDate)
        .set(RecordEntity::getTitle, nextTitle)
        .set(RecordEntity::getUpdatedAt, LocalDateTime.now()));
  }

  private String querySummary(UUID recordId) {
    List<GeneratedOutputEntity> outputs = generatedOutputMapper
        .selectList(new LambdaQueryWrapper<GeneratedOutputEntity>()
            .eq(GeneratedOutputEntity::getRecordId, recordId)
            .eq(GeneratedOutputEntity::getType, "SUMMARY")
            .orderByDesc(GeneratedOutputEntity::getVersion)
            .last("limit 1"));
    if (outputs.isEmpty()) {
      return "No summary yet.";
    }
    String content = outputs.get(0).getContent();
    return content == null || content.isBlank() ? "No summary yet." : content;
  }

  private String queryLatestParseStatus(UUID recordId) {
    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getRecordId, recordId)
        .orderByDesc(ParseJobEntity::getUpdatedAt)
        .orderByDesc(ParseJobEntity::getCreatedAt)
        .last("limit 1"));
    if (jobs.isEmpty()) {
      return "NOT_PARSED";
    }
    return String.valueOf(jobs.get(0).getStatus());
  }

  private StructuredResultData queryLatestStructuredResult(UUID recordId) {
    List<StructuredResultEntity> rows = structuredResultMapper
        .selectList(new LambdaQueryWrapper<StructuredResultEntity>()
            .eq(StructuredResultEntity::getRecordId, recordId)
            .orderByDesc(StructuredResultEntity::getRevision)
            .last("limit 1"));
    if (rows.isEmpty()) {
      return new StructuredResultData("v1", 0, objectMapper.createObjectNode());
    }
    StructuredResultEntity latest = rows.get(0);
    return new StructuredResultData(
        latest.getSchemaVersion(),
        latest.getRevision() == null ? 0 : latest.getRevision(),
        parsePayload(latest.getPayloadJson()));
  }

  private static String sourceTypeLabel(String sourceType) {
    return switch (sourceType) {
      case "UPLOAD" -> "常规检查";
      case "LAB" -> "检验报告";
      case "IMAGING" -> "影像报告";
      case "OUTPATIENT" -> "门诊记录";
      case "DISCHARGE" -> "出院小结";
      default -> sourceType;
    };
  }

  private String normalizeReportCategoryName(String name) {
    if (name == null) {
      return null;
    }
    String normalized = name.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > MAX_REPORT_CATEGORY_NAME_LENGTH) {
      throw new IllegalArgumentException("Report category name is too long");
    }
    return normalized;
  }

  private void ensureReportCategoryByName(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    ReportCategoryEntity existing = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, tenantContextProvider.currentTenantId())
        .eq(ReportCategoryEntity::getPatientId, tenantContextProvider.currentPatientId())
        .apply("lower(name) = lower({0})", name)
        .last("limit 1"));
    if (existing != null) {
      return;
    }

    ReportCategoryEntity toCreate = new ReportCategoryEntity();
    toCreate.setId(UUID.randomUUID());
    toCreate.setTenantId(tenantContextProvider.currentTenantId());
    toCreate.setUserId(tenantContextProvider.currentUserId());
    toCreate.setPatientId(tenantContextProvider.currentPatientId());
    toCreate.setName(name);
    toCreate.setCreatedAt(LocalDateTime.now());
    toCreate.setUpdatedAt(LocalDateTime.now());
    reportCategoryMapper.insert(toCreate);
  }

  private void ensureReportCategoryByNameForRecord(String name, RecordEntity record) {
    if (name == null || name.isBlank()) {
      return;
    }
    ReportCategoryEntity existing = reportCategoryMapper.selectOne(new LambdaQueryWrapper<ReportCategoryEntity>()
        .eq(ReportCategoryEntity::getTenantId, record.getTenantId())
        .eq(ReportCategoryEntity::getPatientId, record.getPatientId())
        .apply("lower(name) = lower({0})", name)
        .last("limit 1"));
    if (existing != null) {
      return;
    }

    ReportCategoryEntity toCreate = new ReportCategoryEntity();
    toCreate.setId(UUID.randomUUID());
    toCreate.setTenantId(record.getTenantId());
    toCreate.setUserId(record.getUserId());
    toCreate.setPatientId(record.getPatientId());
    toCreate.setName(name);
    toCreate.setCreatedAt(LocalDateTime.now());
    toCreate.setUpdatedAt(LocalDateTime.now());
    reportCategoryMapper.insert(toCreate);
  }

  private List<TrendField> extractTrendFields(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      return List.of();
    }
    JsonNode fieldsNode = payload.path("fields");
    if (!fieldsNode.isArray()) {
      return List.of();
    }

    List<TrendField> fields = new ArrayList<>();
    for (JsonNode fieldNode : fieldsNode) {
      if (!fieldNode.isObject()) {
        continue;
      }
      TrendField trendField = structuredFieldInterpreter.toTrendField(fieldNode);
      if (trendField != null) {
        fields.add(trendField);
      }
    }
    return fields;
  }

  private JsonNode parsePayload(String payloadJson) {
    try {
      JsonNode parsed = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
      if (parsed.isObject()) {
        return structuredFieldInterpreter.enrichPayload(parsed);
      }
      ObjectNode fallback = objectMapper.createObjectNode();
      fallback.put("raw", payloadJson);
      return structuredFieldInterpreter.enrichPayload(fallback);
    } catch (Exception ignored) {
      return structuredFieldInterpreter.enrichPayload(objectMapper.createObjectNode());
    }
  }

  private JsonNode parsePayloadOrEmptyFields(String payloadJson) {
    try {
      JsonNode parsed = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
      if (parsed.isObject()) {
        return structuredFieldInterpreter.enrichPayload(parsed);
      }
      ObjectNode fallback = objectMapper.createObjectNode();
      fallback.putArray("fields");
      return structuredFieldInterpreter.enrichPayload(fallback);
    } catch (Exception ignored) {
      ObjectNode fallback = objectMapper.createObjectNode();
      fallback.putArray("fields");
      return structuredFieldInterpreter.enrichPayload(fallback);
    }
  }
}
