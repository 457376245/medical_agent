package com.medical.agent.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.medical.agent.domain.enums.ParseJobStatus;
import com.medical.agent.domain.repository.ParseJobRepository;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;

@Repository
public class ParseJobRepositoryImpl implements ParseJobRepository {
    private final ParseJobMapper parseJobMapper;

    public ParseJobRepositoryImpl(ParseJobMapper parseJobMapper) {
        this.parseJobMapper = parseJobMapper;
    }

    @Override
    public ParseJobEntity findByIdAndTenantId(UUID id, UUID tenantId) {
        return parseJobMapper.selectOne(new LambdaQueryWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getId, id)
                .eq(ParseJobEntity::getTenantId, tenantId)
                .last("limit 1"));
    }

    @Override
    public ParseJobEntity findById(UUID id) {
        return parseJobMapper.selectById(id);
    }

    @Override
    public ParseJobEntity findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return parseJobMapper.selectOne(new LambdaQueryWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getTenantId, tenantId)
                .eq(ParseJobEntity::getIdempotencyKey, idempotencyKey)
                .last("limit 1"));
    }

    @Override
    public void save(ParseJobEntity parseJob) {
        parseJobMapper.insert(parseJob);
    }

    @Override
    public void updateStatusFields(UUID jobId, ParseJobStatus status, int progress, String errorCode,
            Integer retryCount) {
        LambdaUpdateWrapper<ParseJobEntity> wrapper = new LambdaUpdateWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getId, jobId)
                .set(ParseJobEntity::getStatus, status.name())
                .set(ParseJobEntity::getProgress, progress)
                .set(ParseJobEntity::getErrorCode, errorCode)
                .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now());

        if (retryCount != null) {
            wrapper.set(ParseJobEntity::getRetryCount, retryCount);
        }
        parseJobMapper.update(null, wrapper);
    }

    @Override
    public List<ParseJobEntity> findFailedJobsLessThanRetryCount(int maxRetryCount, int limit) {
        return parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getStatus, ParseJobStatus.FAILED.name())
                .lt(ParseJobEntity::getRetryCount, maxRetryCount)
                .orderByAsc(ParseJobEntity::getUpdatedAt)
                .last("limit " + limit));
    }

    @Override
    public List<ParseJobEntity> findFailedJobsGreaterThanOrEqualRetryCount(int maxRetryCount, int limit) {
        return parseJobMapper.selectList(new LambdaQueryWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getStatus, ParseJobStatus.FAILED.name())
                .ge(ParseJobEntity::getRetryCount, maxRetryCount)
                .orderByAsc(ParseJobEntity::getUpdatedAt)
                .last("limit " + limit));
    }

    @Override
    public boolean markJobRetrying(UUID jobId) {
        int updated = parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getId, jobId)
                .eq(ParseJobEntity::getStatus, ParseJobStatus.FAILED.name())
                .set(ParseJobEntity::getStatus, ParseJobStatus.RETRYING.name())
                .set(ParseJobEntity::getProgress, 35)
                .set(ParseJobEntity::getErrorCode, null)
                .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
        return updated > 0;
    }

    @Override
    public void markJobFailedAfterRetryDispatch(UUID jobId, String errorCode) {
        parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getId, jobId)
                .eq(ParseJobEntity::getStatus, ParseJobStatus.RETRYING.name())
                .set(ParseJobEntity::getStatus, ParseJobStatus.FAILED.name())
                .set(ParseJobEntity::getProgress, 100)
                .set(ParseJobEntity::getErrorCode, errorCode)
                .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public void markJobDeadLetter(UUID jobId, String errorCode) {
        parseJobMapper.update(null, new LambdaUpdateWrapper<ParseJobEntity>()
                .eq(ParseJobEntity::getId, jobId)
                .eq(ParseJobEntity::getStatus, ParseJobStatus.FAILED.name())
                .set(ParseJobEntity::getStatus, ParseJobStatus.DEAD_LETTER.name())
                .set(ParseJobEntity::getProgress, 100)
                .set(ParseJobEntity::getErrorCode, errorCode)
                .set(ParseJobEntity::getUpdatedAt, LocalDateTime.now()));
    }
}
