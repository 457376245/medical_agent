package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.vo.DiseaseProfileSummary;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.StructuredResultEntity;
import com.medical.agent.infrastructure.persistence.mapper.AssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.DataRightsRequestMapper;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobAssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Tag(name = "疾病档案服务", description = "负责疾病档案的创建、校验、统计与删除编排，并处理关联资源清理")
public class DiseaseProfileService {
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final RecordMapper recordMapper;
  private final AssetMapper assetMapper;
  private final ParseJobMapper parseJobMapper;
  private final ParseJobAssetMapper parseJobAssetMapper;
  private final StructuredResultMapper structuredResultMapper;
  private final GeneratedOutputMapper generatedOutputMapper;
  private final DataRightsRequestMapper dataRightsRequestMapper;
  private final OssPresignService ossPresignService;

  public DiseaseProfileService(
      DiseaseProfileMapper diseaseProfileMapper,
      RecordMapper recordMapper,
      AssetMapper assetMapper,
      ParseJobMapper parseJobMapper,
      ParseJobAssetMapper parseJobAssetMapper,
      StructuredResultMapper structuredResultMapper,
      GeneratedOutputMapper generatedOutputMapper,
      DataRightsRequestMapper dataRightsRequestMapper,
      OssPresignService ossPresignService) {
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.recordMapper = recordMapper;
    this.assetMapper = assetMapper;
    this.parseJobMapper = parseJobMapper;
    this.parseJobAssetMapper = parseJobAssetMapper;
    this.structuredResultMapper = structuredResultMapper;
    this.generatedOutputMapper = generatedOutputMapper;
    this.dataRightsRequestMapper = dataRightsRequestMapper;
    this.ossPresignService = ossPresignService;
  }

  @Operation(summary = "创建或复用疾病档案", description = "按名称幂等创建疾病档案；若同租户同用户下存在同名档案则直接复用")
  public UUID createProfile(String name) {
    String normalizedName = name == null ? "" : name.trim();
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("Disease profile name is required");
    }

    DiseaseProfileEntity existing = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .apply("lower(name) = lower({0})", normalizedName)
        .last("limit 1"));
    if (existing != null) {
      return existing.getId();
    }

    DiseaseProfileEntity entity = new DiseaseProfileEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    entity.setUserId(ScopeConstants.DEFAULT_USER_ID);
    entity.setName(normalizedName);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    diseaseProfileMapper.insert(entity);
    return entity.getId();
  }

  @Operation(summary = "查询疾病档案摘要", description = "返回疾病档案基础信息并附带每个档案下的记录数量")
  public List<DiseaseProfileSummary> listProfiles() {
    List<DiseaseProfileEntity> profiles = diseaseProfileMapper.selectList(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .orderByDesc(DiseaseProfileEntity::getUpdatedAt)
        .orderByAsc(DiseaseProfileEntity::getName));

    List<DiseaseProfileSummary> result = new ArrayList<>();
    for (DiseaseProfileEntity profile : profiles) {
      Long count = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
          .eq(RecordEntity::getDiseaseProfileId, profile.getId())
          .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID));
      result.add(new DiseaseProfileSummary(
          String.valueOf(profile.getId()),
          String.valueOf(profile.getName()),
          String.valueOf(profile.getUpdatedAt()),
          count == null ? 0 : count.intValue()));
    }
    return result;
  }

  @Operation(summary = "检查疾病档案是否存在", description = "在当前租户与用户范围内校验指定疾病档案是否存在")
  public boolean profileExists(UUID diseaseProfileId) {
    Long count = diseaseProfileMapper.selectCount(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, diseaseProfileId)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    return count != null && count > 0;
  }

  @Operation(summary = "统计疾病档案下记录数", description = "统计指定疾病档案关联的记录条数，用于删除前校验")
  public int countRecords(UUID diseaseProfileId) {
    Long count = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getDiseaseProfileId, diseaseProfileId)
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID));
    return count == null ? 0 : count.intValue();
  }

  @Transactional
  @Operation(summary = "级联删除疾病档案及关联资源", description = "删除档案及其关联记录、资产、解析结果与生成内容，并清理对象存储文件")
  public DeleteDiseaseProfileResult deleteProfile(UUID diseaseProfileId) {
    if (!profileExists(diseaseProfileId)) {
      return new DeleteDiseaseProfileResult(false, 0, 0);
    }

    List<String> objectKeys = listAssetObjectKeysByDiseaseProfile(diseaseProfileId);
    for (String objectKey : objectKeys) {
      ossPresignService.deleteObject(objectKey);
    }

    int deletedRecords = deleteProfileCascadeInternal(diseaseProfileId);
    return new DeleteDiseaseProfileResult(true, deletedRecords, objectKeys.size());
  }

  @Transactional
  @Operation(summary = "仅在空档案时删除疾病档案", description = "仅当档案无关联记录时允许删除，避免误删仍在使用的数据")
  public DeleteDiseaseProfileIfEmptyResult deleteProfileIfEmpty(UUID diseaseProfileId) {
    if (!profileExists(diseaseProfileId)) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "NOT_FOUND", 0);
    }

    int linkedRecordCount = countRecords(diseaseProfileId);
    if (linkedRecordCount > 0) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "HAS_ASSOCIATED_RECORDS", linkedRecordCount);
    }

    int deleted = diseaseProfileMapper.delete(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, diseaseProfileId)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    if (deleted <= 0) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "DELETE_FAILED", 0);
    }
    return new DeleteDiseaseProfileIfEmptyResult(true, "DELETED", 0);
  }

  private List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId) {
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .select(RecordEntity::getId)
        .eq(RecordEntity::getDiseaseProfileId, diseaseProfileId)
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID));
    if (records.isEmpty()) {
      return List.of();
    }
    List<UUID> recordIds = records.stream().map(RecordEntity::getId).toList();
    List<AssetEntity> assets = assetMapper.selectList(new LambdaQueryWrapper<AssetEntity>()
        .select(AssetEntity::getObjectKey)
        .in(AssetEntity::getRecordId, recordIds));
    return assets.stream().map(AssetEntity::getObjectKey).filter(v -> v != null && !v.isBlank()).toList();
  }

  private int deleteProfileCascadeInternal(UUID diseaseProfileId) {
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .select(RecordEntity::getId)
        .eq(RecordEntity::getDiseaseProfileId, diseaseProfileId)
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID));
    if (records.isEmpty()) {
      diseaseProfileMapper.delete(new LambdaQueryWrapper<DiseaseProfileEntity>()
          .eq(DiseaseProfileEntity::getId, diseaseProfileId)
          .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
          .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
      return 0;
    }

    List<UUID> recordIds = records.stream().map(RecordEntity::getId).toList();
    dataRightsRequestMapper.delete(new LambdaQueryWrapper<com.medical.agent.infrastructure.persistence.entity.DataRightsRequestEntity>()
        .in(com.medical.agent.infrastructure.persistence.entity.DataRightsRequestEntity::getRecordId, recordIds));
    structuredResultMapper.delete(new LambdaQueryWrapper<StructuredResultEntity>()
        .in(StructuredResultEntity::getRecordId, recordIds));
    generatedOutputMapper.delete(new LambdaQueryWrapper<GeneratedOutputEntity>()
        .in(GeneratedOutputEntity::getRecordId, recordIds));

    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .select(ParseJobEntity::getId)
        .in(ParseJobEntity::getRecordId, recordIds));
    if (!jobs.isEmpty()) {
      List<UUID> jobIds = jobs.stream().map(ParseJobEntity::getId).toList();
      parseJobAssetMapper.delete(new LambdaQueryWrapper<ParseJobAssetEntity>()
          .in(ParseJobAssetEntity::getJobId, jobIds));
    }

    parseJobMapper.delete(new LambdaQueryWrapper<ParseJobEntity>()
        .in(ParseJobEntity::getRecordId, recordIds));
    assetMapper.delete(new LambdaQueryWrapper<AssetEntity>()
        .in(AssetEntity::getRecordId, recordIds));
    int deletedRecords = recordMapper.delete(new LambdaQueryWrapper<RecordEntity>()
        .in(RecordEntity::getId, recordIds));

    diseaseProfileMapper.delete(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, diseaseProfileId)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    return deletedRecords;
  }

  public record DeleteDiseaseProfileResult(boolean deleted, int deletedRecordCount, int deletedAssetCount) {}

  public record DeleteDiseaseProfileIfEmptyResult(boolean deleted, String reason, int linkedRecordCount) {}
}
