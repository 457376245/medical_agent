package com.medical.agent.infrastructure.scheduler;

import com.medical.agent.application.service.ParseJobService;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.ParseRequestEvent;
import com.medical.agent.infrastructure.mq.ParseRequestPublisher;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ParseRetryScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ParseRetryScheduler.class);
  private static final String RETRY_EXHAUSTED_ERROR = "EXT_PARSE_RETRY_EXHAUSTED";
  private static final String RETRY_DISPATCH_ERROR = "EXT_RETRY_DISPATCH_FAILED";
  private static final String MISSING_ASSET_ERROR = "BIZ_MISSING_ASSET_REFS";

  private final ParseJobService parseJobService;
  private final ParseRequestPublisher parseRequestPublisher;
  private final boolean enabled;
  private final int maxRetryCount;
  private final int batchSize;

  public ParseRetryScheduler(
      ParseJobService parseJobService,
      ParseRequestPublisher parseRequestPublisher,
      @Value("${app.parse.retry.enabled:true}") boolean enabled,
      @Value("${app.parse.retry.max-retry-count:3}") int maxRetryCount,
      @Value("${app.parse.retry.batch-size:20}") int batchSize) {
    this.parseJobService = parseJobService;
    this.parseRequestPublisher = parseRequestPublisher;
    this.enabled = enabled;
    this.maxRetryCount = Math.max(1, maxRetryCount);
    this.batchSize = Math.max(1, batchSize);
  }

  @Scheduled(fixedDelayString = "${app.parse.retry.fixed-delay-ms:60000}")
  public void retryFailedParseJobs() {
    if (!enabled) {
      return;
    }

    moveExpiredFailedJobsToDeadLetter();
    List<ParseJobService.ParseRetryCandidate> candidates =
        parseJobService.listFailedParseJobsForRetry(maxRetryCount, batchSize);
    for (ParseJobService.ParseRetryCandidate candidate : candidates) {
      retrySingleJob(candidate);
    }
  }

  private void moveExpiredFailedJobsToDeadLetter() {
    List<ParseJobService.ParseRetryCandidate> expired =
        parseJobService.listFailedParseJobsForDeadLetter(maxRetryCount, batchSize);
    for (ParseJobService.ParseRetryCandidate candidate : expired) {
      parseJobService.markParseJobDeadLetter(candidate.jobId(), RETRY_EXHAUSTED_ERROR);
      LOGGER.warn(
          "Marked parse job as dead letter after retry limit reached: jobId={} retryCount={}",
          candidate.jobId(),
          candidate.retryCount());
    }
  }

  private void retrySingleJob(ParseJobService.ParseRetryCandidate candidate) {
    UUID jobId = candidate.jobId();
    if (!parseJobService.markParseJobRetrying(jobId)) {
      return;
    }
    try {
      List<AssetRef> assetRefs = parseJobService.listAssetRefsByJobId(jobId);
      if (assetRefs.isEmpty()) {
        parseJobService.markParseJobDeadLetter(jobId, MISSING_ASSET_ERROR);
        LOGGER.warn("Marked parse job as dead letter due to missing assets: jobId={}", jobId);
        return;
      }
      ParseJobContext context = parseJobService.parseJobContext(jobId);
      parseRequestPublisher.publish(new ParseRequestEvent(
          jobId.toString(),
          context.tenantId(),
          context.userId(),
          assetRefs,
          UUID.randomUUID().toString().replace("-", ""),
          "v1",
          "parse-retry-" + jobId + "-" + candidate.retryCount(),
          context.recordId(),
          null,
          List.of()));
      LOGGER.info(
          "Requeued failed parse job: jobId={} retryCount={}",
          jobId,
          candidate.retryCount());
    } catch (Exception ex) {
      parseJobService.markParseJobFailedAfterRetryDispatch(jobId, RETRY_DISPATCH_ERROR);
      LOGGER.error("Failed to dispatch retry parse job: jobId={}", jobId, ex);
    }
  }
}
