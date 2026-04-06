package com.medical.agent.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.yulichang.toolkit.JoinWrappers;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.dto.response.ParseJobStatusResponseData;
import com.medical.agent.domain.enums.ParseJobStatus;
import com.medical.agent.domain.exception.ResourceNotFoundException;
import com.medical.agent.domain.repository.ParseJobRepository;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.ParseRequestEvent;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;

import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobAssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Service
@Tag(name = "解析任务服务", description = "负责解析任务全生命周期管理，包括创建、状态流转、重试与死信处理")
public class ParseJobService {
  private static final int MAX_PARSE_RETRY_COUNT = 3;

  private final ParseJobRepository parseJobRepository;
  private final ParseJobAssetMapper parseJobAssetMapper;
  private final StructuredResultMapper structuredResultMapper;
  private final GeneratedOutputMapper generatedOutputMapper;
  private final RecordService recordService;
  private final RecordMapper recordMapper;
  private final ReportCategoryService reportCategoryService;
  private final ParseRequestPublisher parseRequestPublisher;
  private final TenantContextProvider tenantContextProvider;

  public ParseJobService(
      ParseJobRepository parseJobRepository,
      ParseJobAssetMapper parseJobAssetMapper,
      StructuredResultMapper structuredResultMapper,
      GeneratedOutputMapper generatedOutputMapper,
      RecordService recordService,
      RecordMapper recordMapper,
      ReportCategoryService reportCategoryService,
      ParseRequestPublisher parseRequestPublisher,
      TenantContextProvider tenantContextProvider) {
    this.parseJobRepository = parseJobRepository;
    this.parseJobAssetMapper = parseJobAssetMapper;
    this.structuredResultMapper = structuredResultMapper;
    this.generatedOutputMapper = generatedOutputMapper;
    this.recordService = recordService;
    this.recordMapper = recordMapper;
    this.reportCategoryService = reportCategoryService;
    this.parseRequestPublisher = parseRequestPublisher;
    this.tenantContextProvider = tenantContextProvider;
  }

  public record ParseApplyResult(UUID recordId, String finalStatus, boolean stateChanged) {
  }

  public record ParseRetryCandidate(UUID jobId, UUID recordId, int retryCount) {
  }

  @Operation(summary = "创建解析任务并投递请求", description = "根据记录与资产创建任务，绑定幂等键后向消息队列发布解析请求")
  public ParseJobResponseData create(CreateParseJobRequest request, String idempotencyKey) {
    UUID recordId = UUID.fromString(request.recordId());
    UUID tenantId = tenantContextProvider.currentTenantId();
    UUID jobId = createOrReuseParseJob(recordId, idempotencyKey, tenantId);

    List<String> rawAssetIds = request.assetIds() == null ? List.of() : request.assetIds();
    List<UUID> assetIds = rawAssetIds.stream().map(UUID::fromString).toList();
    bindParseJobAssets(jobId, assetIds);

    List<AssetRef> assetRefs = recordService.listAssetRefs(assetIds);
    ParseJobContext context = parseJobContext(jobId);

    RecordEntity record = recordMapper.selectById(recordId);
    String currentSourceType = record != null ? record.getSourceType() : null;
    List<String> existingCategories = reportCategoryService.listCategories()
        .stream().map(ReportCategorySummary::name).toList();

    parseRequestPublisher.publish(new ParseRequestEvent(
        jobId.toString(),
        context.tenantId(),
        context.userId(),
        assetRefs,
        UUID.randomUUID().toString().replace("-", ""),
        "v1",
        idempotencyKey,
        recordId.toString(),
        currentSourceType,
        existingCategories));

    return new ParseJobResponseData(jobId.toString(), ParseJobStatus.QUEUED.name());
  }

  @Operation(summary = "获取解析任务状态", description = "按租户范围查询任务状态、进度与错误信息，用于前端轮询")
  public ParseJobStatusResponseData getStatus(UUID jobId) {
    UUID tenantId = tenantContextProvider.currentTenantId();
    ParseJobEntity job = parseJobRepository.findByIdAndTenantId(jobId, tenantId);
    if (job == null) {
      throw new ResourceNotFoundException("parse job not found");
    }
    return new ParseJobStatusResponseData(
        String.valueOf(job.getId()),
        String.valueOf(job.getStatus()),
        job.getProgress(),
        job.getErrorCode(),
        job.getUpdatedAt() == null ? null : job.getUpdatedAt().toString());
  }

  @Transactional
  @Operation(summary = "应用解析结果回调", description = "消费解析回调并更新任务状态，成功时写入结构化结果与默认摘要")
  public ParseApplyResult applyParseResult(
      UUID jobId,
      String status,
      String structuredResultJson,
      double confidence,
      String errorCode) {
    ParseJobEntity job = parseJobRepository.findById(jobId);
    if (job == null) {
      throw new ResourceNotFoundException("parse job not found");
    }

    String currentStatus = String.valueOf(job.getStatus());
    int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
    if (ParseJobStatus.SUCCESS.name().equals(currentStatus)
        || ParseJobStatus.DEAD_LETTER.name().equals(currentStatus)) {
      return new ParseApplyResult(job.getRecordId(), currentStatus, false);
    }

    boolean success = ParseJobStatus.SUCCESS.name().equals(status);
    int nextRetryCount = success ? retryCount : retryCount + 1;
    String nextStatus = success ? ParseJobStatus.SUCCESS.name() : ParseJobStatus.FAILED.name();
    if (!success && nextRetryCount >= MAX_PARSE_RETRY_COUNT) {
      nextStatus = ParseJobStatus.DEAD_LETTER.name();
    }

    parseJobRepository.updateStatusFields(jobId, ParseJobStatus.valueOf(nextStatus), 100, success ? null : errorCode,
        nextRetryCount);

    if (ParseJobStatus.SUCCESS.name().equals(nextStatus)) {
      LocalDateTime now = LocalDateTime.now();
      structuredResultMapper.insertWithJson(
          UUID.randomUUID(),
          tenantContextProvider.currentTenantId(),
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
          tenantContextProvider.currentTenantId(),
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

  @Operation(summary = "查询待重试的失败解析任务", description = "查询失败且重试次数低于阈值的任务，用于定时补偿重试")
  public List<ParseRetryCandidate> listFailedParseJobsForRetry(int maxRetryCount, int limit) {
    int normalizedLimit = Math.max(1, limit);
    List<ParseJobEntity> jobs = parseJobRepository.findFailedJobsLessThanRetryCount(maxRetryCount, normalizedLimit);
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (ParseJobEntity job : jobs) {
      candidates.add(new ParseRetryCandidate(job.getId(), job.getRecordId(),
          job.getRetryCount() == null ? 0 : job.getRetryCount()));
    }
    return candidates;
  }

  @Operation(summary = "查询待入死信的失败解析任务", description = "查询失败且重试次数达到阈值的任务，进入死信处理流程")
  public List<ParseRetryCandidate> listFailedParseJobsForDeadLetter(int maxRetryCount, int limit) {
    int normalizedLimit = Math.max(1, limit);
    List<ParseJobEntity> jobs = parseJobRepository.findFailedJobsGreaterThanOrEqualRetryCount(maxRetryCount,
        normalizedLimit);
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (ParseJobEntity job : jobs) {
      candidates.add(new ParseRetryCandidate(job.getId(), job.getRecordId(),
          job.getRetryCount() == null ? 0 : job.getRetryCount()));
    }
    return candidates;
  }

  @Operation(summary = "标记解析任务为重试中", description = "在分发重试前将任务状态置为重试中，避免并发重复调度")
  public boolean markParseJobRetrying(UUID jobId) {
    return parseJobRepository.markJobRetrying(jobId);
  }

  @Operation(summary = "重试投递后标记任务失败", description = "重试消息分发失败时回滚任务状态并记录错误码")
  public void markParseJobFailedAfterRetryDispatch(UUID jobId, String errorCode) {
    parseJobRepository.markJobFailedAfterRetryDispatch(jobId, errorCode);
  }

  @Operation(summary = "标记解析任务为死信", description = "将超过重试上限或不可恢复的任务标记为死信，停止继续重试")
  public void markParseJobDeadLetter(UUID jobId, String errorCode) {
    parseJobRepository.markJobDeadLetter(jobId, errorCode);
  }

  @Operation(summary = "按任务ID查询资产引用", description = "查询任务绑定的资产清单，供解析引擎拉取原始文件")
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

  @Operation(summary = "构建解析任务上下文", description = "根据任务提取记录、租户与用户上下文，用于消息投递和追踪")
  public ParseJobContext parseJobContext(UUID jobId) {
    ParseJobEntity job = parseJobRepository.findById(jobId);
    if (job == null) {
      throw new ResourceNotFoundException("parse job not found");
    }
    return new ParseJobContext(
        String.valueOf(job.getRecordId()),
        String.valueOf(job.getTenantId()),
        tenantContextProvider.currentUserId().toString());
  }

  private UUID createOrReuseParseJob(UUID recordId, String idempotencyKey, UUID tenantId) {
    ParseJobEntity existing = parseJobRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    if (existing != null) {
      return existing.getId();
    }

    ParseJobEntity job = new ParseJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(tenantId);
    job.setRecordId(recordService.ensureRecord(recordId));
    job.setStatus(ParseJobStatus.QUEUED.name());
    job.setProgress(0);
    job.setRetryCount(0);
    job.setErrorCode(null);
    job.setTraceId(UUID.randomUUID().toString().replace("-", ""));
    job.setIdempotencyKey(idempotencyKey);
    job.setCreatedAt(LocalDateTime.now());
    job.setUpdatedAt(LocalDateTime.now());
    parseJobRepository.save(job);
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
    List<GeneratedOutputEntity> latest = generatedOutputMapper
        .selectList(new LambdaQueryWrapper<GeneratedOutputEntity>()
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
