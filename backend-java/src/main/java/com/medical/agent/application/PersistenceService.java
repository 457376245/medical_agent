package com.medical.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PersistenceService {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final int MAX_REPORT_CATEGORY_NAME_LENGTH = 64;
  private static final int MAX_PARSE_RETRY_COUNT = 3;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public PersistenceService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record ParseApplyResult(UUID recordId, String finalStatus, boolean stateChanged) {}

  public record ParseRetryCandidate(UUID jobId, UUID recordId, int retryCount) {}

  public UUID ensureRecord(UUID recordId) {
    return ensureRecord(recordId, null, null, null, null);
  }

  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title) {
    return ensureRecord(recordId, diseaseProfileId, reportDate, title, null);
  }

  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title, String sourceType) {
    UUID finalRecordId = recordId == null ? UUID.randomUUID() : recordId;
    UUID finalDiseaseProfileId = diseaseProfileId;
    LocalDate finalReportDate = reportDate == null ? LocalDate.now() : reportDate;
    String finalTitle = title == null || title.isBlank() ? "Imported record" : title;
    String normalizedSourceType = normalizeReportCategoryName(sourceType);
    String finalSourceType = normalizedSourceType == null ? "未分类" : normalizedSourceType;
    ensureReportCategoryByName(finalSourceType);
    Integer exists = jdbcTemplate.queryForObject(
        "select count(1) from records where id = ?",
        Integer.class,
        finalRecordId);
    if (exists == null || exists == 0) {
      jdbcTemplate.update(
          "insert into records (id, tenant_id, user_id, disease_profile_id, record_date, title, source_type, created_at, updated_at) "
              + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          finalRecordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          finalDiseaseProfileId,
          finalReportDate,
          finalTitle,
          finalSourceType,
          now(),
          now());
    } else {
      if (normalizedSourceType != null) {
        ensureReportCategoryByName(normalizedSourceType);
      }
      jdbcTemplate.update(
          "update records set disease_profile_id = coalesce(?, disease_profile_id), record_date = coalesce(?, record_date), title = coalesce(?, title), source_type = coalesce(?, source_type), updated_at = ? where id = ?",
          diseaseProfileId,
          reportDate,
          title == null || title.isBlank() ? null : title,
          normalizedSourceType,
          now(),
          finalRecordId);
    }
    return finalRecordId;
  }

  public UUID createAsset(
      String objectKey,
      String checksum,
      UUID recordId,
      String fileType,
      long fileSize,
      UUID diseaseProfileId,
      LocalDate reportDate,
      String title,
      String sourceType) {
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into assets (id, tenant_id, record_id, object_key, file_type, file_size, checksum, created_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?)",
        assetId,
        DEFAULT_TENANT_ID,
        ensureRecord(recordId, diseaseProfileId, reportDate, title, sourceType),
        objectKey,
        fileType,
        fileSize,
        checksum,
        now());
    return assetId;
  }

  public List<Map<String, Object>> listAssetRefs(List<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> refs = new ArrayList<>();
    for (UUID assetId : assetIds) {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "select id, object_key, file_type from assets where id = ?",
          assetId);
      refs.add(Map.of(
          "assetId", String.valueOf(row.get("id")),
          "objectKey", String.valueOf(row.get("object_key")),
          "fileType", String.valueOf(row.get("file_type"))));
    }
    return refs;
  }

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

  public Map<String, String> parseJobContext(UUID jobId) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "select record_id, tenant_id from parse_jobs where id = ?",
        jobId);
    return Map.of(
        "recordId", String.valueOf(row.get("record_id")),
        "tenantId", String.valueOf(row.get("tenant_id")),
        "userId", DEFAULT_USER_ID.toString());
  }

  public ParseApplyResult applyParseResult(UUID jobId, String status, String structuredResultJson, double confidence, String errorCode) {
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
      createGeneratedOutput(recordId, "SUMMARY", "Auto summary generated from structured result.");
    }
    return new ParseApplyResult(recordId, nextStatus, true);
  }

  public Map<String, Object> createGenerateTask(UUID recordId, String type, String idempotencyKey) {
    UUID taskId = UUID.randomUUID();
    Map<String, Object> payload = new HashMap<>();
    payload.put("taskId", taskId.toString());
    payload.put("recordId", ensureRecord(recordId).toString());
    payload.put("type", type);
    payload.put("status", "QUEUED");
    payload.put("traceId", RequestTrace.newTraceId());
    payload.put("tenantId", DEFAULT_TENANT_ID.toString());
    payload.put("userId", DEFAULT_USER_ID.toString());
    payload.put("idempotencyKey", idempotencyKey);
    return payload;
  }

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
          ensureRecord(recordId),
          "QUEUED",
          0,
          RequestTrace.newTraceId(),
          idempotencyKey,
          now(),
          now());
      return jobId;
    }
  }

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

  public Map<String, Object> getAndAdvanceParseJob(UUID jobId) {
    Map<String, Object> current = jdbcTemplate.queryForMap(
        "select id, status, progress, record_id, error_code from parse_jobs where id = ?",
        jobId);
    String status = String.valueOf(current.get("status"));
    int progress = ((Number) current.get("progress")).intValue();
    String errorCode = current.get("error_code") == null ? null : String.valueOf(current.get("error_code"));

    if ("QUEUED".equals(status)) {
      status = "PROCESSING";
      progress = 35;
      jdbcTemplate.update("update parse_jobs set status = ?, progress = ?, updated_at = ? where id = ?",
          status, progress, now(), jobId);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("status", status);
    response.put("progress", progress);
    response.put("errorCode", errorCode);
    response.put("resultId", "SUCCESS".equals(status) ? jobId.toString() : null);
    return response;
  }

  public Map<String, Object> patchStructuredResult(UUID recordId, int revision, String payloadJson) {
    UUID resultId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into structured_results (id, tenant_id, job_id, record_id, schema_version, payload_json, confidence_score, revision, is_user_edited, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)",
        resultId,
        DEFAULT_TENANT_ID,
        UUID.randomUUID(),
        ensureRecord(recordId),
        "v1",
        payloadJson,
        0.8,
        revision,
        true,
        now(),
        now());
    return Map.of("resultId", resultId.toString(), "revision", revision);
  }

  public Map<String, Object> fetchRecord(UUID recordId) {
    ensureRecord(recordId);
    List<Map<String, Object>> outputs = jdbcTemplate.queryForList(
        "select type, version, content from generated_outputs where record_id = ? order by version desc",
        recordId);
    List<Map<String, Object>> parseJobs = jdbcTemplate.queryForList(
        "select status from parse_jobs where record_id = ? order by updated_at desc, created_at desc limit 1",
        recordId);
    List<Map<String, Object>> results = jdbcTemplate.queryForList(
        "select schema_version, revision, payload_json from structured_results where record_id = ? order by revision desc",
        recordId);
    String summary = outputs.stream()
        .filter(row -> "SUMMARY".equals(String.valueOf(row.get("type"))))
        .findFirst()
        .map(row -> String.valueOf(row.get("content")))
        .orElse("No summary yet.");
    Map<String, Object> latestResult = results.isEmpty()
        ? Map.of("schemaVersion", "v1", "revision", 0, "payload", Map.of())
        : Map.of(
            "schemaVersion", results.get(0).get("schema_version"),
            "revision", results.get(0).get("revision"),
            "payload", parsePayload(String.valueOf(results.get(0).get("payload_json"))));
    String parseStatus = parseJobs.isEmpty() ? "NOT_PARSED" : String.valueOf(parseJobs.get(0).get("status"));
    return Map.of(
        "recordId", recordId.toString(),
        "summary", summary,
        "parseStatus", parseStatus,
        "structuredResult", latestResult);
  }

  public Map<String, Object> fetchLatestGeneratedOutput(UUID recordId, String type) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "select version, content, model_meta "
              + "from generated_outputs where record_id = ? and type = ? "
              + "order by version desc limit 1",
          recordId,
          type);
      return Map.of(
          "recordId", recordId.toString(),
          "type", type,
          "version", row.get("version"),
          "content", String.valueOf(row.get("content")),
          "modelMeta", row.get("model_meta") == null
              ? Map.of()
              : parsePayload(String.valueOf(row.get("model_meta"))));
    } catch (EmptyResultDataAccessException ignored) {
      return Map.of();
    }
  }

  public Map<String, Object> fetchRecordAnalysisContext(UUID recordId) {
    Map<String, Object> record;
    try {
      record = jdbcTemplate.queryForMap(
          "select r.id, r.title, r.record_date, r.source_type, coalesce(dp.name, '未分类疾病') as disease_name "
              + "from records r "
              + "left join disease_profiles dp on dp.id = r.disease_profile_id "
              + "where r.id = ? and r.tenant_id = ? and r.user_id = ?",
          recordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
    } catch (EmptyResultDataAccessException ignored) {
      return Map.of();
    }

    List<Map<String, Object>> results = jdbcTemplate.queryForList(
        "select schema_version, revision, payload_json "
            + "from structured_results where record_id = ? "
            + "order by revision desc limit 1",
        recordId);
    List<Map<String, Object>> parseJobs = jdbcTemplate.queryForList(
        "select status from parse_jobs where record_id = ? order by updated_at desc, created_at desc limit 1",
        recordId);
    Map<String, Object> latestResult = results.isEmpty()
        ? Map.of("schemaVersion", "v1", "revision", 0, "payload", Map.of("fields", List.of()))
        : Map.of(
            "schemaVersion", String.valueOf(results.get(0).get("schema_version")),
            "revision", results.get(0).get("revision"),
            "payload", parsePayload(String.valueOf(results.get(0).get("payload_json"))));
    String parseStatus = parseJobs.isEmpty() ? "NOT_PARSED" : String.valueOf(parseJobs.get(0).get("status"));

    return Map.of(
        "recordId", String.valueOf(record.get("id")),
        "title", record.get("title") == null ? "未命名报告" : String.valueOf(record.get("title")),
        "recordDate", String.valueOf(record.get("record_date")),
        "sourceType", String.valueOf(record.get("source_type")),
        "diseaseName", String.valueOf(record.get("disease_name")),
        "parseStatus", parseStatus,
        "structuredResult", latestResult);
  }

  public UUID createDiseaseProfile(String name) {
    String normalizedName = name == null ? "" : name.trim();
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("Disease profile name is required");
    }
    try {
      return jdbcTemplate.queryForObject(
          "select id from disease_profiles where tenant_id = ? and user_id = ? and lower(name) = lower(?)",
          UUID.class,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          normalizedName);
    } catch (EmptyResultDataAccessException ignored) {
      UUID profileId = UUID.randomUUID();
      jdbcTemplate.update(
          "insert into disease_profiles (id, tenant_id, user_id, name, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
          profileId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          normalizedName,
          now(),
          now());
      return profileId;
    }
  }

  public UUID createReportCategory(String name) {
    String normalizedName = normalizeReportCategoryName(name);
    if (normalizedName == null) {
      throw new IllegalArgumentException("Report category name is required");
    }
    try {
      return jdbcTemplate.queryForObject(
          "select id from report_categories where tenant_id = ? and user_id = ? and lower(name) = lower(?)",
          UUID.class,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          normalizedName);
    } catch (EmptyResultDataAccessException ignored) {
      UUID categoryId = UUID.randomUUID();
      jdbcTemplate.update(
          "insert into report_categories (id, tenant_id, user_id, name, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
          categoryId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          normalizedName,
          now(),
          now());
      return categoryId;
    }
  }

  public List<Map<String, Object>> listReportCategories() {
    return jdbcTemplate.queryForList(
        "select rc.id, rc.name, rc.updated_at, count(r.id) as record_count "
            + "from report_categories rc "
            + "left join records r on r.source_type = rc.name and r.tenant_id = rc.tenant_id and r.user_id = rc.user_id "
            + "where rc.tenant_id = ? and rc.user_id = ? "
            + "group by rc.id, rc.name, rc.updated_at "
            + "order by rc.updated_at desc, rc.name asc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
  }

  public boolean reportCategoryExists(UUID reportCategoryId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from report_categories where id = ? and tenant_id = ? and user_id = ?",
        Integer.class,
        reportCategoryId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return count != null && count > 0;
  }

  public int countRecordsByReportCategory(UUID reportCategoryId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) "
            + "from report_categories rc "
            + "left join records r on r.source_type = rc.name and r.tenant_id = rc.tenant_id and r.user_id = rc.user_id "
            + "where rc.id = ? and rc.tenant_id = ? and rc.user_id = ?",
        Integer.class,
        reportCategoryId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return count == null ? 0 : count;
  }

  public boolean deleteReportCategoryIfEmpty(UUID reportCategoryId) {
    int deleted = jdbcTemplate.update(
        "delete from report_categories rc "
            + "where rc.id = ? and rc.tenant_id = ? and rc.user_id = ? "
            + "and not exists ("
            + "  select 1 from records r "
            + "  where r.source_type = rc.name and r.tenant_id = rc.tenant_id and r.user_id = rc.user_id"
            + ")",
        reportCategoryId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return deleted > 0;
  }

  public List<Map<String, Object>> listDiseaseProfiles() {
    return jdbcTemplate.queryForList(
        "select dp.id, dp.name, dp.updated_at, count(r.id) as record_count "
            + "from disease_profiles dp "
            + "left join records r on r.disease_profile_id = dp.id and r.tenant_id = dp.tenant_id "
            + "where dp.tenant_id = ? and dp.user_id = ? "
            + "group by dp.id, dp.name, dp.updated_at "
            + "order by dp.updated_at desc, dp.name asc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
  }

  public boolean diseaseProfileExists(UUID diseaseProfileId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from disease_profiles where id = ? and tenant_id = ? and user_id = ?",
        Integer.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return count != null && count > 0;
  }

  public List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId) {
    return jdbcTemplate.queryForList(
        "select a.object_key "
            + "from assets a join records r on r.id = a.record_id "
            + "where r.disease_profile_id = ? and r.tenant_id = ?",
        String.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID);
  }

  public int countRecordsByDiseaseProfile(UUID diseaseProfileId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from records where disease_profile_id = ? and tenant_id = ?",
        Integer.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    return count == null ? 0 : count;
  }

  public boolean deleteDiseaseProfileIfEmpty(UUID diseaseProfileId) {
    int deleted = jdbcTemplate.update(
        "delete from disease_profiles "
            + "where id = ? and tenant_id = ? and user_id = ? "
            + "and not exists ("
            + "  select 1 from records r where r.disease_profile_id = ? and r.tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID,
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    return deleted > 0;
  }

  public int deleteDiseaseProfileCascade(UUID diseaseProfileId) {
    jdbcTemplate.update(
        "delete from data_rights_requests where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from structured_results where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from generated_outputs where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from parse_job_assets where job_id in ("
            + "select id from parse_jobs where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + "))",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from parse_jobs where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from assets where record_id in ("
            + "select id from records where disease_profile_id = ? and tenant_id = ?"
            + ")",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    int deletedRecords = jdbcTemplate.update(
        "delete from records where disease_profile_id = ? and tenant_id = ?",
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    jdbcTemplate.update(
        "delete from disease_profiles where id = ? and tenant_id = ? and user_id = ?",
        diseaseProfileId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return deletedRecords;
  }

  public int createGeneratedOutput(UUID recordId, String type, String content) {
    Integer version = jdbcTemplate.queryForObject(
        "select coalesce(max(version), 0) + 1 from generated_outputs where record_id = ? and type = ?",
        Integer.class,
        recordId,
        type);
    int finalVersion = version == null ? 1 : version;
    jdbcTemplate.update(
        "insert into generated_outputs (id, tenant_id, record_id, type, version, content, model_meta, requires_confirmation, created_at) "
            + "values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)",
        UUID.randomUUID(),
        DEFAULT_TENANT_ID,
        ensureRecord(recordId),
        type,
        finalVersion,
        content,
        "{\"provider\":\"gateway\"}",
        true,
        now());
    return finalVersion;
  }

  public int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson) {
    Integer version = jdbcTemplate.queryForObject(
        "select coalesce(max(version), 0) + 1 from generated_outputs where record_id = ? and type = ?",
        Integer.class,
        recordId,
        type);
    int finalVersion = version == null ? 1 : version;
    jdbcTemplate.update(
        "insert into generated_outputs (id, tenant_id, record_id, type, version, content, model_meta, requires_confirmation, created_at) "
            + "values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)",
        UUID.randomUUID(),
        DEFAULT_TENANT_ID,
        ensureRecord(recordId),
        type,
        finalVersion,
        content,
        modelMetaJson,
        true,
        now());
    return finalVersion;
  }

  public UUID createDataRightsRequest(UUID recordId, String requestType) {
    UUID requestId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into data_rights_requests (id, tenant_id, user_id, record_id, request_type, status, download_url, expire_at, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, null, null, ?, ?)",
        requestId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID,
        ensureRecord(recordId),
        requestType,
        "REQUESTED",
        now(),
        now());
    return requestId;
  }

  public Map<String, Object> getDataRightsRequest(UUID requestId) {
    Map<String, Object> request = jdbcTemplate.queryForMap(
        "select id, request_type, status, download_url, expire_at, updated_at from data_rights_requests where id = ?",
        requestId);
    String requestType = String.valueOf(request.get("request_type"));
    String status = String.valueOf(request.get("status"));
    if ("EXPORT".equals(requestType) && "REQUESTED".equals(status)) {
      status = "COMPLETED";
      String downloadUrl = "https://download.example.com/exports/" + requestId;
      Timestamp expireAt = Timestamp.from(Instant.now().plusSeconds(900));
      jdbcTemplate.update(
          "update data_rights_requests set status = ?, download_url = ?, expire_at = ?, updated_at = ? where id = ?",
          status,
          downloadUrl,
          expireAt,
          now(),
          requestId);
      request = jdbcTemplate.queryForMap(
          "select id, request_type, status, download_url, expire_at, updated_at from data_rights_requests where id = ?",
          requestId);
    } else if ("DELETE".equals(requestType) && "REQUESTED".equals(status)) {
      status = "PROCESSING";
      jdbcTemplate.update(
          "update data_rights_requests set status = ?, updated_at = ? where id = ?",
          status,
          now(),
          requestId);
      request = jdbcTemplate.queryForMap(
          "select id, request_type, status, download_url, expire_at, updated_at from data_rights_requests where id = ?",
          requestId);
    }
    return request;
  }

  public List<Map<String, Object>> listTimelineBatches() {
    return jdbcTemplate.queryForList(
        "with latest_parse as ("
            + "  select pj.record_id, pj.status, "
            + "         row_number() over (partition by pj.record_id order by pj.created_at desc, pj.updated_at desc) as rn "
            + "  from parse_jobs pj"
            + "), ranked as ("
            + "  select dp.id::text as batch_id, "
            + "         dp.name as disease_name, "
            + "         r.id as record_id, "
            + "         r.title as record_title, "
            + "         r.record_date as record_date, "
            + "         row_number() over ("
            + "           partition by dp.id::text "
            + "           order by r.record_date desc, r.updated_at desc, r.created_at desc"
            + "         ) as rn "
            + "  from records r "
            + "  join disease_profiles dp on dp.id = r.disease_profile_id "
            + "    and dp.tenant_id = r.tenant_id "
            + "    and dp.user_id = r.user_id "
            + "  where r.tenant_id = ? and r.user_id = ?"
            + ") "
            + "select ranked.batch_id, ranked.disease_name, "
            + "       count(*) as record_count, "
            + "       max(ranked.record_date) as latest_record_at, "
            + "       max(case when ranked.rn = 1 then ranked.record_id::text end) as latest_record_id, "
            + "       max(case when ranked.rn = 1 then ranked.record_title end) as latest_record_title, "
            + "       max(case when ranked.rn = 1 then coalesce(lp.status, 'NOT_PARSED') end) as latest_parse_status "
            + "from ranked "
            + "left join latest_parse lp on lp.record_id = ranked.record_id and lp.rn = 1 "
            + "group by ranked.batch_id, ranked.disease_name "
            + "order by latest_record_at desc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
  }

  public List<Map<String, Object>> listRecordsByBatch(String batchId) {
    if ("unknown".equalsIgnoreCase(batchId)) {
      return jdbcTemplate.queryForList(
          "select id, title, record_date, source_type from records "
              + "where disease_profile_id is null and tenant_id = ? order by record_date desc",
          DEFAULT_TENANT_ID);
    }
    return jdbcTemplate.queryForList(
        "select id, title, record_date, source_type from records where disease_profile_id::text = ? order by record_date desc",
        batchId);
  }

  public String getDiseaseNameByBatch(String batchId) {
    try {
      UUID diseaseProfileId = UUID.fromString(batchId);
      String diseaseName = jdbcTemplate.queryForObject(
          "select name from disease_profiles where id = ? and tenant_id = ? and user_id = ?",
          String.class,
          diseaseProfileId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
      if (diseaseName != null && !diseaseName.isBlank()) {
        return diseaseName;
      }
    } catch (IllegalArgumentException | EmptyResultDataAccessException ignored) {
      // fallback to record-based resolution for legacy/unknown batches
    }

    try {
      return jdbcTemplate.queryForObject(
          "select coalesce(dp.name, '未分类疾病') "
              + "from records r "
              + "left join disease_profiles dp on dp.id = r.disease_profile_id "
              + "where coalesce(dp.id::text, 'unknown') = ? and r.tenant_id = ? "
              + "order by r.record_date desc, r.updated_at desc, r.created_at desc "
              + "limit 1",
          String.class,
          batchId,
          DEFAULT_TENANT_ID);
    } catch (EmptyResultDataAccessException ignored) {
      return "未分类疾病";
    }
  }

  public Map<String, Object> updateRecordSourceType(UUID recordId, String sourceType) {
    String normalizedSourceType = normalizeReportCategoryName(sourceType);
    if (normalizedSourceType == null) {
      throw new IllegalArgumentException("sourceType is required");
    }
    ensureReportCategoryByName(normalizedSourceType);

    Map<String, Object> record;
    try {
      record = jdbcTemplate.queryForMap(
          "select r.record_date, coalesce(dp.name, '未分类疾病') as disease_name "
              + "from records r "
              + "left join disease_profiles dp on dp.id = r.disease_profile_id "
              + "where r.id = ? and r.tenant_id = ? and r.user_id = ?",
          recordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
    } catch (EmptyResultDataAccessException ignored) {
      return Map.of("updated", false);
    }

    Object recordDateValue = record.get("record_date");
    String recordDate = recordDateValue == null ? LocalDate.now().toString() : String.valueOf(recordDateValue);
    String diseaseName = String.valueOf(record.get("disease_name"));
    String nextTitle = diseaseName + "-" + sourceTypeLabel(normalizedSourceType) + "-" + recordDate;
    int updated = jdbcTemplate.update(
        "update records set source_type = ?, title = ?, updated_at = ? where id = ? and tenant_id = ? and user_id = ?",
        normalizedSourceType,
        nextTitle,
        now(),
        recordId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    if (updated <= 0) {
      return Map.of("updated", false);
    }
    return Map.of(
        "updated", true,
        "sourceType", normalizedSourceType,
        "title", nextTitle,
        "recordDate", recordDate,
        "diseaseName", diseaseName);
  }

  public Map<String, Object> fetchRecordTrend(UUID recordId, int limit) {
    Map<String, Object> currentRecord;
    try {
      currentRecord = jdbcTemplate.queryForMap(
          "select id, disease_profile_id, source_type, record_date, title, updated_at, created_at "
              + "from records where id = ? and tenant_id = ? and user_id = ?",
          recordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
    } catch (EmptyResultDataAccessException ignored) {
      throw new IllegalArgumentException("record not found");
    }

    UUID diseaseProfileId = (UUID) currentRecord.get("disease_profile_id");
    String sourceType = String.valueOf(currentRecord.get("source_type"));
    List<Map<String, Object>> scopedRecords;
    if (diseaseProfileId == null) {
      scopedRecords = jdbcTemplate.queryForList(
          "select id, record_date, title, source_type, updated_at, created_at "
              + "from records "
              + "where disease_profile_id is null and tenant_id = ? and user_id = ? and source_type = ? "
              + "order by record_date desc, updated_at desc, created_at desc",
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          sourceType);
    } else {
      scopedRecords = jdbcTemplate.queryForList(
          "select id, record_date, title, source_type, updated_at, created_at "
              + "from records "
              + "where disease_profile_id = ? and tenant_id = ? and user_id = ? and source_type = ? "
              + "order by record_date desc, updated_at desc, created_at desc",
          diseaseProfileId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID,
          sourceType);
    }

    int anchorIndex = -1;
    for (int i = 0; i < scopedRecords.size(); i++) {
      if (recordId.equals(scopedRecords.get(i).get("id"))) {
        anchorIndex = i;
        break;
      }
    }
    if (anchorIndex < 0) {
      throw new IllegalArgumentException("record not found in scoped records");
    }

    int endExclusive = Math.min(scopedRecords.size(), anchorIndex + Math.max(1, limit));
    List<Map<String, Object>> window = new ArrayList<>(scopedRecords.subList(anchorIndex, endExclusive));
    Collections.reverse(window);

    List<Map<String, Object>> snapshots = new ArrayList<>();
    for (Map<String, Object> row : window) {
      UUID rowRecordId = (UUID) row.get("id");
      List<Map<String, Object>> results = jdbcTemplate.queryForList(
          "select payload_json from structured_results where record_id = ? order by revision desc limit 1",
          rowRecordId);
      Object payload = results.isEmpty() ? Map.of("fields", List.of()) : parsePayload(String.valueOf(results.get(0).get("payload_json")));
      List<Map<String, Object>> fields = extractTrendFields(payload);
      snapshots.add(Map.of(
          "recordId", rowRecordId.toString(),
          "recordDate", String.valueOf(row.get("record_date")),
          "title", row.get("title") == null ? "未命名报告" : String.valueOf(row.get("title")),
          "sourceType", String.valueOf(row.get("source_type")),
          "fields", fields));
    }

    Map<String, Object> response = new HashMap<>();
    response.put("recordId", recordId.toString());
    response.put("sourceType", sourceType);
    response.put("diseaseProfileId", diseaseProfileId == null ? "unknown" : diseaseProfileId.toString());
    response.put("limit", Math.max(1, limit));
    response.put("snapshots", snapshots);
    return response;
  }

  public boolean deleteRecord(UUID recordId) {
    jdbcTemplate.update("delete from data_rights_requests where record_id = ?", recordId);
    jdbcTemplate.update("delete from structured_results where record_id = ?", recordId);
    jdbcTemplate.update("delete from generated_outputs where record_id = ?", recordId);
    jdbcTemplate.update("delete from parse_job_assets where job_id in (select id from parse_jobs where record_id = ?)", recordId);
    jdbcTemplate.update("delete from parse_jobs where record_id = ?", recordId);
    jdbcTemplate.update("delete from assets where record_id = ?", recordId);
    int deleted = jdbcTemplate.update("delete from records where id = ?", recordId);
    return deleted > 0;
  }

  private static String sourceTypeLabel(String sourceType) {
    return switch (sourceType) {
      case "UPLOAD" -> "常规检查";
      case "LAB" -> "检验报告";
      case "IMAGING" -> "影像报告";
      case "OUTPATIENT" -> "门诊记录";
      case "DISCHARGE" -> "出院小结";
      default -> sourceType;
    };
  }

  private String normalizeReportCategoryName(String name) {
    if (name == null) {
      return null;
    }
    String normalized = name.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > MAX_REPORT_CATEGORY_NAME_LENGTH) {
      throw new IllegalArgumentException("Report category name is too long");
    }
    return normalized;
  }

  private void ensureReportCategoryByName(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    createReportCategory(name);
  }

  private List<Map<String, Object>> extractTrendFields(Object payload) {
    if (!(payload instanceof Map<?, ?> payloadMapRaw)) {
      return List.of();
    }
    Object fieldsRaw = payloadMapRaw.get("fields");
    if (!(fieldsRaw instanceof List<?> fieldsList)) {
      return List.of();
    }

    List<Map<String, Object>> fields = new ArrayList<>();
    for (Object fieldRaw : fieldsList) {
      if (!(fieldRaw instanceof Map<?, ?> fieldMapRaw)) {
        continue;
      }
      String name = readStringField(fieldMapRaw, "name");
      String value = readStringField(fieldMapRaw, "value");
      if (name.isEmpty() || value.isEmpty()) {
        continue;
      }
      String unit = readStringField(fieldMapRaw, "unit");
      String referenceRange = readStringField(fieldMapRaw, "referenceRange");
      Map<String, Object> normalized = new HashMap<>();
      normalized.put("name", name);
      normalized.put("value", value);
      if (!unit.isEmpty() && !"null".equalsIgnoreCase(unit)) {
        normalized.put("unit", unit);
      }
      if (!referenceRange.isEmpty() && !"null".equalsIgnoreCase(referenceRange)) {
        normalized.put("referenceRange", referenceRange);
      }
      fields.add(normalized);
    }
    return fields;
  }

  private String readStringField(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private void insertStructuredResultIfMissing(UUID jobId, UUID recordId) {
    Integer exists = jdbcTemplate.queryForObject(
        "select count(1) from structured_results where job_id = ?",
        Integer.class,
        jobId);
    if (exists == null || exists == 0) {
      jdbcTemplate.update(
          "insert into structured_results (id, tenant_id, job_id, record_id, schema_version, payload_json, confidence_score, revision, is_user_edited, created_at, updated_at) "
              + "values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          DEFAULT_TENANT_ID,
          jobId,
          recordId,
          "v1",
          "{\"fields\":[]}",
          0.9,
          1,
          false,
          now(),
          now());
    }
  }

  private Object parsePayload(String payloadJson) {
    try {
      return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ignored) {
      return payloadJson;
    }
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
