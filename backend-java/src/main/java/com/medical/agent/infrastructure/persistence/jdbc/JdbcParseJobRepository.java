package com.medical.agent.infrastructure.persistence.jdbc;

import com.medical.agent.application.repository.ParseJobRepository;
import com.medical.agent.application.repository.RecordRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcParseJobRepository implements ParseJobRepository {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final int MAX_PARSE_RETRY_COUNT = 3;

  private final JdbcTemplate jdbcTemplate;
  private final RecordRepository recordRepository;

  public JdbcParseJobRepository(JdbcTemplate jdbcTemplate, RecordRepository recordRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.recordRepository = recordRepository;
  }

  @Override
  public UUID createOrReuseParseJob(UUID recordId, String idempotencyKey) {
    try {
      return jdbcTemplate.queryForObject(
          "select id from parse_jobs where idempotency_key = ?",
          UUID.class,
          idempotencyKey);
    } catch (EmptyResultDataAccessException ignored) {
      UUID jobId = UUID.randomUUID();
      jdbcTemplate.update(
          "insert into parse_jobs (id, tenant_id, record_id, status, progress, retry_count, error_code, trace_id, idempotency_key, created_at, updated_at) "
              + "values (?, ?, ?, ?, ?, 0, null, ?, ?, ?, ?)",
          jobId,
          DEFAULT_TENANT_ID,
          recordRepository.ensureRecord(recordId),
          "QUEUED",
          0,
          RequestTrace.newTraceId(),
          idempotencyKey,
          now(),
          now());
      return jobId;
    }
  }

  @Override
  public void bindParseJobAssets(UUID jobId, List<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return;
    }
    for (UUID assetId : assetIds) {
      jdbcTemplate.update(
          "insert into parse_job_assets (job_id, asset_id, created_at) values (?, ?, ?) "
              + "on conflict (job_id, asset_id) do nothing",
          jobId,
          assetId,
          now());
    }
  }

  @Override
  public List<Map<String, Object>> listAssetRefsByJobId(UUID jobId) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select a.id, a.object_key, a.file_type "
            + "from parse_job_assets pja "
            + "join assets a on a.id = pja.asset_id "
            + "where pja.job_id = ? "
            + "order by pja.created_at asc",
        jobId);
    List<Map<String, Object>> refs = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      refs.add(Map.of(
          "assetId", String.valueOf(row.get("id")),
          "objectKey", String.valueOf(row.get("object_key")),
          "fileType", String.valueOf(row.get("file_type"))));
    }
    return refs;
  }

  @Override
  public Map<String, String> parseJobContext(UUID jobId) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "select record_id, tenant_id from parse_jobs where id = ?",
        jobId);
    return Map.of(
        "recordId", String.valueOf(row.get("record_id")),
        "tenantId", String.valueOf(row.get("tenant_id")),
        "userId", DEFAULT_USER_ID.toString());
  }

  @Override
  public ParseApplyResult applyParseResult(
      UUID jobId,
      String status,
      String structuredResultJson,
      double confidence,
      String errorCode) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "select record_id, status, retry_count from parse_jobs where id = ?",
        jobId);
    UUID recordId = (UUID) row.get("record_id");
    String currentStatus = String.valueOf(row.get("status"));
    int retryCount = ((Number) row.get("retry_count")).intValue();
    if ("SUCCESS".equals(currentStatus) || "DEAD_LETTER".equals(currentStatus)) {
      return new ParseApplyResult(recordId, currentStatus, false);
    }

    boolean success = "SUCCESS".equals(status);
    int nextRetryCount = success ? retryCount : retryCount + 1;
    String nextStatus = success ? "SUCCESS" : "FAILED";
    if (!success && nextRetryCount >= MAX_PARSE_RETRY_COUNT) {
      nextStatus = "DEAD_LETTER";
    }

    jdbcTemplate.update(
        "update parse_jobs set status = ?, progress = ?, error_code = ?, retry_count = ?, updated_at = ? where id = ?",
        nextStatus,
        100,
        success ? null : errorCode,
        nextRetryCount,
        now(),
        jobId);

    if ("SUCCESS".equals(nextStatus)) {
      jdbcTemplate.update(
          "insert into structured_results (id, tenant_id, job_id, record_id, schema_version, payload_json, confidence_score, revision, is_user_edited, created_at, updated_at) "
              + "values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          DEFAULT_TENANT_ID,
          jobId,
          recordId,
          "v1",
          structuredResultJson,
          confidence,
          1,
          false,
          now(),
          now());

      Integer version = jdbcTemplate.queryForObject(
          "select coalesce(max(version), 0) + 1 from generated_outputs where record_id = ? and type = ?",
          Integer.class,
          recordId,
          "SUMMARY");
      int finalVersion = version == null ? 1 : version;
      jdbcTemplate.update(
          "insert into generated_outputs (id, tenant_id, record_id, type, version, content, model_meta, requires_confirmation, created_at) "
              + "values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)",
          UUID.randomUUID(),
          DEFAULT_TENANT_ID,
          recordRepository.ensureRecord(recordId),
          "SUMMARY",
          finalVersion,
          "Auto summary generated from structured result.",
          "{\"provider\":\"gateway\"}",
          true,
          now());
    }
    return new ParseApplyResult(recordId, nextStatus, true);
  }

  @Override
  public List<ParseRetryCandidate> listFailedParseJobsForRetry(int maxRetryCount, int limit) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select id, record_id, retry_count "
            + "from parse_jobs "
            + "where status = 'FAILED' and retry_count < ? "
            + "order by updated_at asc "
            + "limit ?",
        maxRetryCount,
        limit);
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      candidates.add(new ParseRetryCandidate(
          (UUID) row.get("id"),
          (UUID) row.get("record_id"),
          ((Number) row.get("retry_count")).intValue()));
    }
    return candidates;
  }

  @Override
  public List<ParseRetryCandidate> listFailedParseJobsForDeadLetter(int maxRetryCount, int limit) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select id, record_id, retry_count "
            + "from parse_jobs "
            + "where status = 'FAILED' and retry_count >= ? "
            + "order by updated_at asc "
            + "limit ?",
        maxRetryCount,
        limit);
    List<ParseRetryCandidate> candidates = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      candidates.add(new ParseRetryCandidate(
          (UUID) row.get("id"),
          (UUID) row.get("record_id"),
          ((Number) row.get("retry_count")).intValue()));
    }
    return candidates;
  }

  @Override
  public boolean markParseJobRetrying(UUID jobId) {
    int updated = jdbcTemplate.update(
        "update parse_jobs set status = ?, progress = ?, error_code = ?, updated_at = ? "
            + "where id = ? and status = ?",
        "RETRYING",
        35,
        null,
        now(),
        jobId,
        "FAILED");
    return updated > 0;
  }

  @Override
  public void markParseJobFailedAfterRetryDispatch(UUID jobId, String errorCode) {
    jdbcTemplate.update(
        "update parse_jobs set status = ?, progress = ?, error_code = ?, updated_at = ? where id = ? and status = ?",
        "FAILED",
        100,
        errorCode,
        now(),
        jobId,
        "RETRYING");
  }

  @Override
  public void markParseJobDeadLetter(UUID jobId, String errorCode) {
    jdbcTemplate.update(
        "update parse_jobs set status = ?, progress = ?, error_code = ?, updated_at = ? where id = ? and status = ?",
        "DEAD_LETTER",
        100,
        errorCode,
        now(),
        jobId,
        "FAILED");
  }

  private Timestamp now() {
    return Timestamp.from(Instant.now());
  }

  private static final class RequestTrace {
    private RequestTrace() {}

    static String newTraceId() {
      return UUID.randomUUID().toString().replace("-", "");
    }
  }
}
