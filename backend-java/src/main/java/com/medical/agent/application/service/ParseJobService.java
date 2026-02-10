package com.medical.agent.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yulichang.toolkit.JoinWrappers;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.ParseRequestEvent;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobAssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParseJobService {
  private static final int MAX_PARSE_RETRY_COUNT = 3;

  private final ParseJobMapper parseJobMapper;
  private final ParseJobAssetMapper parseJobAssetMapper;
  private final StructuredResultMapper structuredResultMapper;
  private final GeneratedOutputMapper generatedOutputMapper;
  private final RecordService recordService;
  private final ParseRequestPublisher parseRequestPublisher;

  public ParseJobService(
      ParseJobMapper parseJobMapper,
      ParseJobAssetMapper parseJobAssetMapper,
      StructuredResultMapper structuredResultMapper,
      GeneratedOutputMapper generatedOutputMapper,
      RecordService recordService,
      ParseRequestPublisher parseRequestPublisher) {
    this.parseJobMapper = parseJobMapper;
    this.parseJobAssetMapper = parseJobAssetMapper;
    this.structuredResultMapper = structuredResultMapper;
    this.generatedOutputMapper = generatedOutputMapper;
    this.recordService = recordService;
    this.parseRequestPublisher = parseRequestPublisher;
  }

  public record ParseApplyResult(UUID recordId, String finalStatus, boolean stateChanged) {}

  public record ParseRetryCandidate(UUID jobId, UUID recordId, int retryCount) {}

  public ParseJobResponseData create(CreateParseJobRequest request, String idempotencyKey) {
    UUID recordId = UUID.fromString(request.recordId());
    UUID jobId = createOrReuseParseJob(recordId, idempotencyKey);

    List<String> rawAssetIds = request.assetIds() == null ? List.of() : request.assetIds();
    List<UUID> assetIds = rawAssetIds.stream().map(UUID::fromString).toList();
    bindParseJobAssets(jobId, assetIds);

    List<AssetRef> assetRefs = recordService.listAssetRefs(assetIds);
    ParseJobContext context = parseJobContext(jobId);

    parseRequestPublisher.publish(new ParseRequestEvent(
        jobId.toString(),
        context.tenantId(),
        context.userId(),
        assetRefs,
        UUID.randomUUID().toString().replace("-", ""),
        "v1",
        idempotencyKey));

    return new ParseJobResponseData(jobId.toString(), "QUEUED");
  }

  @Transactional
  public ParseApplyResult applyParseResult(
      UUID jobId,
      String status,
      String structuredResultJson,
      double confidence,
      String errorCode) {
    ParseJobEntity job = parseJobMapper.selectById(jobId);
    if (job == null) {
      throw new IllegalArgumentException("parse job not found");
    }

    String currentStatus = String.valueOf(job.getStatus());
    int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
    if ("SUCCESS".equals(currentStatus) || "DEAD_LETTER".equals(currentStatus)) {
      return new ParseApplyResult(job.getRecordId(), currentStatus, false);
    }

    boolean success = "SUCCESS".equals(status);
    int nextRetryCount = success ? retryCount : retryCount + 1;
    String nextStatus = success ? "SUCCESS" : "FAILED";
    if (!success && nextRetryCount >= MAX_PARSE_RETRY_COUNT) {
      nextStatus = "DEAD_LETTER";
    }

    parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getId, jobId)
        .set(ParseJobEntity::getStatus, nextStatus)
        .set(ParseJobEntity::getProgress, 100)
        .set(ParseJobEntity::getErrorCode, success ? null : errorCode)
        .set(ParseJobEntity::getRetryCount, nextRetryCount)
        .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));

    if ("SUCCESS".equals(nextStatus)) {
      LocalDateTime now = LocalDateTime.now();
      structuredResultMapper.insertWithJson(
          UUID.randomUUID(),
          ScopeConstants.DEFAULT_TENANT_ID,
          jobId,
          job.getRecordId(),
          "v1",
          structuredResultJson,
          BigDecimal.valueOf(confidence),
          1,
          false,
          now,
          now);

      int finalVersion = nextGeneratedOutputVersion(job.getRecordId(), "SUMMARY");
      generatedOutputMapper.insertWithJsonMeta(
          UUID.randomUUID(),
          ScopeConstants.DEFAULT_TENANT_ID,
          recordService.ensureRecord(job.getRecordId()),
          "SUMMARY",
          finalVersion,
          "Auto summary generated from structured result.",
          "{\"provider\":\"gateway\"}",
          true,
          now);
    }

    return new ParseApplyResult(job.getRecordId(), nextStatus, true);
  }

  public List<ParseRetryCandidate> listFailedParseJobsForRetry(int maxRetryCount, int limit) {
    int normalizedLimit = Math.max(1, limit);
    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getStatus, "FAILED")
        .lt(ParseJobEntity::getRetryCount, maxRetryCount)
        .orderByAsc(ParseJobEntity::getUpdatedAt)
        .last("limit " + normalizedLimit));
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (ParseJobEntity job : jobs) {
      candidates.add(new ParseRetryCandidate(job.getId(), job.getRecordId(), job.getRetryCount() == null ? 0 : job.getRetryCount()));
    }
    return candidates;
  }

  public List<ParseRetryCandidate> listFailedParseJobsForDeadLetter(int maxRetryCount, int limit) {
    int normalizedLimit = Math.max(1, limit);
    List<ParseJobEntity> jobs = parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getStatus, "FAILED")
        .ge(ParseJobEntity::getRetryCount, maxRetryCount)
        .orderByAsc(ParseJobEntity::getUpdatedAt)
        .last("limit " + normalizedLimit));
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (ParseJobEntity job : jobs) {
      candidates.add(new ParseRetryCandidate(job.getId(), job.getRecordId(), job.getRetryCount() == null ? 0 : job.getRetryCount()));
    }
    return candidates;
  }

  public boolean markParseJobRetrying(UUID jobId) {
    int updated = parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getId, jobId)
        .eq(ParseJobEntity::getStatus, "FAILED")
        .set(ParseJobEntity::getStatus, "RETRYING")
        .set(ParseJobEntity::getProgress, 35)
        .set(ParseJobEntity::getErrorCode, null)
        .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
    return updated > 0;
  }

  public void markParseJobFailedAfterRetryDispatch(UUID jobId, String errorCode) {
    parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getId, jobId)
        .eq(ParseJobEntity::getStatus, "RETRYING")
        .set(ParseJobEntity::getStatus, "FAILED")
        .set(ParseJobEntity::getProgress, 100)
        .set(ParseJobEntity::getErrorCode, errorCode)
        .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
  }

  public void markParseJobDeadLetter(UUID jobId, String errorCode) {
    parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getId, jobId)
        .eq(ParseJobEntity::getStatus, "FAILED")
        .set(ParseJobEntity::getStatus, "DEAD_LETTER")
        .set(ParseJobEntity::getProgress, 100)
        .set(ParseJobEntity::getErrorCode, errorCode)
        .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
  }

  public List<AssetRef> listAssetRefsByJobId(UUID jobId) {
    MPJLambdaWrapper<ParseJobAssetEntity> wrapper = JoinWrappers.lambda(ParseJobAssetEntity.class)
        .selectAs(AssetEntity::getId, "id")
        .selectAs(AssetEntity::getObjectKey, "object_key")
        .selectAs(AssetEntity::getFileType, "file_type")
        .leftJoin(AssetEntity.class, AssetEntity::getId, ParseJobAssetEntity::getAssetId)
        .eq(ParseJobAssetEntity::getJobId, jobId)
        .orderByAsc(ParseJobAssetEntity::getCreatedAt);

    List<Map<String, Object>> rows = parseJobAssetMapper.selectJoinMaps(wrapper);
    List<AssetRef> refs = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      refs.add(new AssetRef(
          String.valueOf(row.get("id")),
          String.valueOf(row.get("object_key")),
          String.valueOf(row.get("file_type"))));
    }
    return refs;
  }

  public ParseJobContext parseJobContext(UUID jobId) {
    ParseJobEntity job = parseJobMapper.selectById(jobId);
    if (job == null) {
      throw new IllegalArgumentException("parse job not found");
    }
    return new ParseJobContext(
        String.valueOf(job.getRecordId()),
        String.valueOf(job.getTenantId()),
        ScopeConstants.DEFAULT_USER_ID.toString());
  }

  private UUID createOrReuseParseJob(UUID recordId, String idempotencyKey) {
    ParseJobEntity existing = parseJobMapper.selectOne(new LambdaQueryWrapper<ParseJobEntity>()
        .eq(ParseJobEntity::getIdempotencyKey, idempotencyKey)
        .last("limit 1"));
    if (existing != null) {
      return existing.getId();
    }

    ParseJobEntity job = new ParseJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    job.setRecordId(recordService.ensureRecord(recordId));
    job.setStatus("QUEUED");
    job.setProgress(0);
    job.setRetryCount(0);
    job.setErrorCode(null);
    job.setTraceId(UUID.randomUUID().toString().replace("-", ""));
    job.setIdempotencyKey(idempotencyKey);
    job.setCreatedAt(LocalDateTime.now());
    job.setUpdatedAt(LocalDateTime.now());
    parseJobMapper.insert(job);
    return job.getId();
  }

  private void bindParseJobAssets(UUID jobId, List<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (UUID assetId : assetIds) {
      parseJobAssetMapper.insertIgnore(jobId, assetId, now);
    }
  }

  private int nextGeneratedOutputVersion(UUID recordId, String type) {
    List<GeneratedOutputEntity> latest = generatedOutputMapper.selectList(new LambdaQueryWrapper<GeneratedOutputEntity>()
        .select(GeneratedOutputEntity::getVersion)
        .eq(GeneratedOutputEntity::getRecordId, recordId)
        .eq(GeneratedOutputEntity::getType, type)
        .orderByDesc(GeneratedOutputEntity::getVersion)
        .last("limit 1"));
    if (latest.isEmpty() || latest.get(0).getVersion() == null) {
      return 1;
    }
    return latest.get(0).getVersion() + 1;
  }
}
