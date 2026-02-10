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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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

  public boolean profileExists(UUID diseaseProfileId) {
    Long count = diseaseProfileMapper.selectCount(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, diseaseProfileId)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID));
    return count != null && count > 0;
  }

  public int countRecords(UUID diseaseProfileId) {
    Long count = recordMapper.selectCount(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getDiseaseProfileId, diseaseProfileId)
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID));
    return count == null ? 0 : count.intValue();
  }

  @Transactional
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
