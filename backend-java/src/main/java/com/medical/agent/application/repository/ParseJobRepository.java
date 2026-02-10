package com.medical.agent.application.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ParseJobRepository {
  record ParseApplyResult(UUID recordId, String finalStatus, boolean stateChanged) {}

  record ParseRetryCandidate(UUID jobId, UUID recordId, int retryCount) {}

  UUID createOrReuseParseJob(UUID recordId, String idempotencyKey);

  void bindParseJobAssets(UUID jobId, List<UUID> assetIds);

  List<Map<String, Object>> listAssetRefsByJobId(UUID jobId);

  Map<String, String> parseJobContext(UUID jobId);

  ParseApplyResult applyParseResult(
      UUID jobId,
      String status,
      String structuredResultJson,
      double confidence,
      String errorCode);

  List<ParseRetryCandidate> listFailedParseJobsForRetry(int maxRetryCount, int limit);

  List<ParseRetryCandidate> listFailedParseJobsForDeadLetter(int maxRetryCount, int limit);

  boolean markParseJobRetrying(UUID jobId);

  void markParseJobFailedAfterRetryDispatch(UUID jobId, String errorCode);

  void markParseJobDeadLetter(UUID jobId, String errorCode);
}
