package com.medical.agent.infrastructure.scheduler;

import com.medical.agent.application.PersistenceService;
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

  private final PersistenceService persistenceService;
  private final ParseRequestPublisher parseRequestPublisher;
  private final boolean enabled;
  private final int maxRetryCount;
  private final int batchSize;

  public ParseRetryScheduler(
      PersistenceService persistenceService,
      ParseRequestPublisher parseRequestPublisher,
      @Value("${app.parse.retry.enabled:true}") boolean enabled,
      @Value("${app.parse.retry.max-retry-count:3}") int maxRetryCount,
      @Value("${app.parse.retry.batch-size:20}") int batchSize) {
    this.persistenceService = persistenceService;
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
    List<PersistenceService.ParseRetryCandidate> candidates =
        persistenceService.listFailedParseJobsForRetry(maxRetryCount, batchSize);
    for (PersistenceService.ParseRetryCandidate candidate : candidates) {
      retrySingleJob(candidate);
    }
  }

  private void moveExpiredFailedJobsToDeadLetter() {
    List<PersistenceService.ParseRetryCandidate> expired =
        persistenceService.listFailedParseJobsForDeadLetter(maxRetryCount, batchSize);
    for (PersistenceService.ParseRetryCandidate candidate : expired) {
      persistenceService.markParseJobDeadLetter(candidate.jobId(), RETRY_EXHAUSTED_ERROR);
      LOGGER.warn(
          "Marked parse job as dead letter after retry limit reached: jobId={} retryCount={}",
          candidate.jobId(),
          candidate.retryCount());
    }
  }

  private void retrySingleJob(PersistenceService.ParseRetryCandidate candidate) {
    UUID jobId = candidate.jobId();
    if (!persistenceService.markParseJobRetrying(jobId)) {
      return;
    }
    try {
      List<AssetRef> assetRefs = persistenceService.listAssetRefsByJobId(jobId);
      if (assetRefs.isEmpty()) {
        persistenceService.markParseJobDeadLetter(jobId, MISSING_ASSET_ERROR);
        LOGGER.warn("Marked parse job as dead letter due to missing assets: jobId={}", jobId);
        return;
      }
      ParseJobContext context = persistenceService.parseJobContext(jobId);
      parseRequestPublisher.publish(new ParseRequestEvent(
          jobId.toString(),
          context.tenantId(),
          context.userId(),
          assetRefs,
          UUID.randomUUID().toString().replace("-", ""),
          "v1",
          "parse-retry-" + jobId + "-" + candidate.retryCount()));
      LOGGER.info(
          "Requeued failed parse job: jobId={} retryCount={}",
          jobId,
          candidate.retryCount());
    } catch (Exception ex) {
      persistenceService.markParseJobFailedAfterRetryDispatch(jobId, RETRY_DISPATCH_ERROR);
      LOGGER.error("Failed to dispatch retry parse job: jobId={}", jobId, ex);
    }
  }
}
