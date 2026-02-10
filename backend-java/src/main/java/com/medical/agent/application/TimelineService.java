package com.medical.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
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
public class TimelineService {
  private final RecordMapper recordMapper;
  private final DiseaseProfileMapper diseaseProfileMapper;
  private final ParseJobMapper parseJobMapper;

  public TimelineService(
      RecordMapper recordMapper,
      DiseaseProfileMapper diseaseProfileMapper,
      ParseJobMapper parseJobMapper) {
    this.recordMapper = recordMapper;
    this.diseaseProfileMapper = diseaseProfileMapper;
    this.parseJobMapper = parseJobMapper;
  }

  public List<TimelineBatchSummary> listBatches() {
    List<RecordEntity> records = recordMapper.selectList(new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .isNotNull(RecordEntity::getDiseaseProfileId)
        .orderByDesc(RecordEntity::getRecordDate)
        .orderByDesc(RecordEntity::getUpdatedAt)
        .orderByDesc(RecordEntity::getCreatedAt));

    Map<UUID, BatchAccumulator> grouped = new LinkedHashMap<>();
    for (RecordEntity record : records) {
      UUID batchId = record.getDiseaseProfileId();
      BatchAccumulator current = grouped.get(batchId);
      if (current == null) {
        grouped.put(batchId, new BatchAccumulator(record, 1));
      } else {
        current.recordCount += 1;
      }
    }

    List<TimelineBatchSummary> result = new ArrayList<>();
    for (Map.Entry<UUID, BatchAccumulator> entry : grouped.entrySet()) {
      UUID batchId = entry.getKey();
      BatchAccumulator accumulator = entry.getValue();
      DiseaseProfileEntity profile = diseaseProfileMapper.selectById(batchId);
      String diseaseName = profile == null || profile.getName() == null || profile.getName().isBlank()
          ? "未分类疾病"
          : profile.getName();
      String latestParseStatus = queryLatestParseStatus(accumulator.latestRecord.getId());
      result.add(new TimelineBatchSummary(
          String.valueOf(batchId),
          diseaseName,
          accumulator.recordCount,
          String.valueOf(accumulator.latestRecord.getRecordDate()),
          String.valueOf(accumulator.latestRecord.getId()),
          accumulator.latestRecord.getTitle() == null ? "未命名报告" : accumulator.latestRecord.getTitle(),
          latestParseStatus));
    }
    return result;
  }

  public List<TimelineRecordSummary> listBatchRecords(String batchId) {
    LambdaQueryWrapper<RecordEntity> query = new LambdaQueryWrapper<RecordEntity>()
        .eq(RecordEntity::getTenantId, ScopeConstants.DEFAULT_TENANT_ID)
        .eq(RecordEntity::getUserId, ScopeConstants.DEFAULT_USER_ID)
        .orderByDesc(RecordEntity::getRecordDate);

    if ("unknown".equalsIgnoreCase(batchId)) {
      query.isNull(RecordEntity::getDiseaseProfileId);
    } else {
      UUID profileId;
      try {
        profileId = UUID.fromString(batchId);
      } catch (IllegalArgumentException error) {
        return List.of();
      }
      query.eq(RecordEntity::getDiseaseProfileId, profileId);
    }

    List<RecordEntity> records = recordMapper.selectList(query);
    List<TimelineRecordSummary> summaries = new ArrayList<>();
    for (RecordEntity record : records) {
      summaries.add(new TimelineRecordSummary(
          String.valueOf(record.getId()),
          record.getTitle() == null ? "未命名报告" : record.getTitle(),
          String.valueOf(record.getRecordDate()),
          String.valueOf(record.getSourceType())));
    }
    return summaries;
  }

  public String diseaseNameByBatch(String batchId) {
    if ("unknown".equalsIgnoreCase(batchId)) {
      return "未分类疾病";
    }
    UUID profileId;
    try {
      profileId = UUID.fromString(batchId);
    } catch (IllegalArgumentException error) {
      return "未分类疾病";
    }

    DiseaseProfileEntity profile = diseaseProfileMapper.selectOne(new LambdaQueryWrapper<DiseaseProfileEntity>()
        .eq(DiseaseProfileEntity::getId, profileId)
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

  private static final class BatchAccumulator {
    private final RecordEntity latestRecord;
    private int recordCount;

    private BatchAccumulator(RecordEntity latestRecord, int recordCount) {
      this.latestRecord = latestRecord;
      this.recordCount = recordCount;
    }
  }
}
