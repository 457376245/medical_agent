package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.DiseaseProfileEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiseaseProfileQueryService {
  private final RecordMapper recordMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final ParseJobMapper parseJobMapper;

  public DiseaseProfileQueryService(
      RecordMapper recordMapper,
      DiseaseProfileMapper diseaseProfileMapper,
      ParseJobMapper parseJobMapper) {
    this.recordMapper = recordMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.parseJobMapper = parseJobMapper;
  }

  public List<DiseaseProfileOverview> listProfiles() {
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .isNotNull(RecordEntity::getDiseaseProfileId)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt));

    Map<UUID, ProfileAccumulator> grouped = new LinkedHashMap<>();
    for (RecordEntity record : records) {
      UUID profileId = record.getDiseaseProfileId();
      ProfileAccumulator current = grouped.get(profileId);
      if (current == null) {
        grouped.put(profileId, new ProfileAccumulator(record, 1));
      } else {
        current.recordCount += 1;
      }
    }

    List<DiseaseProfileOverview> result = new ArrayList<>();
    for (Map.Entry<UUID, ProfileAccumulator> entry : grouped.entrySet()) {
      UUID profileId = entry.getKey();
      ProfileAccumulator accumulator = entry.getValue();
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(profileId);
      String diseaseName = profile == null || profile.getName() == null || profile.getName().isBlank()
          ? "未分类疾病"
          : profile.getName();
      String latestParseStatus = queryLatestParseStatus(accumulator.latestRecord.getId());
      result.add(new DiseaseProfileOverview(
          String.valueOf(profileId),
          diseaseName,
          accumulator.recordCount,
          String.valueOf(accumulator.latestRecord.getRecordDate()),
          String.valueOf(accumulator.latestRecord.getId()),
          accumulator.latestRecord.getTitle() == null ? "未命名报告" : accumulator.latestRecord.getTitle(),
          latestParseStatus));
    }
    return result;
  }

  public List<DiseaseProfileRecordSummary> listProfileRecords(String profileId) {
    LambdaQueryWrapper<RecordEntity> query = new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .orderByDesc(RecordEntity::getRecordDate);

    if ("unknown".equalsIgnoreCase(profileId)) {
      query.isNull(RecordEntity::getDiseaseProfileId);
    } else {
      UUID targetProfileId;
      try {
        targetProfileId = UUID.fromString(profileId);
      } catch (IllegalArgumentException error) {
        return List.of();
      }
      query.eq(RecordEntity::getDiseaseProfileId, targetProfileId);
    }

    List<RecordEntity> records = recordMapper.selectList(query);
    List<DiseaseProfileRecordSummary> summaries = new ArrayList<>();
    for (RecordEntity record : records) {
      summaries.add(new DiseaseProfileRecordSummary(
          String.valueOf(record.getId()),
          record.getTitle() == null ? "未命名报告" : record.getTitle(),
          String.valueOf(record.getRecordDate()),
          String.valueOf(record.getSourceType())));
    }
    return summaries;
  }

  public String diseaseNameByProfile(String profileId) {
    if ("unknown".equalsIgnoreCase(profileId)) {
      return "未分类疾病";
    }
    UUID targetProfileId;
    try {
      targetProfileId = UUID.fromString(profileId);
    } catch (IllegalArgumentException error) {
      return "未分类疾病";
    }

    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, targetProfileId)
        .eq(DiseaseProfileEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(DiseaseProfileEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .last("limit 1"));
    if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
      return "未分类疾病";
    }
    return profile.getName();
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

  private static final class ProfileAccumulator {
    private final RecordEntity latestRecord;
    private int recordCount;

    private ProfileAccumulator(RecordEntity latestRecord, int recordCount) {
      this.latestRecord = latestRecord;
      this.recordCount = recordCount;
    }
  }
}
