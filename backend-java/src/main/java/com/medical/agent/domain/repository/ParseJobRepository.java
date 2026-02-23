package com.medical.agent.domain.repository;

import java.util.List;
import java.util.UUID;

import com.medical.agent.domain.enums.ParseJobStatus;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;

public interface ParseJobRepository {
    ParseJobEntity findByIdAndTenantId(UUID id, UUID tenantId);

    ParseJobEntity findById(UUID id);

    ParseJobEntity findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    void save(ParseJobEntity parseJob);

    void updateStatusFields(UUID jobId, ParseJobStatus status, int progress, String errorCode, Integer retryCount);

    List<ParseJobEntity> findFailedJobsLessThanRetryCount(int maxRetryCount, int limit);

    List<ParseJobEntity> findFailedJobsGreaterThanOrEqualRetryCount(int maxRetryCount, int limit);

    boolean markJobRetrying(UUID jobId);

    void markJobFailedAfterRetryDispatch(UUID jobId, String errorCode);

    void markJobDeadLetter(UUID jobId, String errorCode);
}
