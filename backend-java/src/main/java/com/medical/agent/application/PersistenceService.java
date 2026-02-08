package com.medical.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public PersistenceService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID ensureRecord(UUID recordId) {
    return ensureRecord(recordId, null, null, null);
  }

  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title) {
    UUID finalRecordId = recordId == null ? UUID.randomUUID() : recordId;
    UUID finalDiseaseProfileId = diseaseProfileId == null ? ensureDefaultDiseaseProfile() : diseaseProfileId;
    LocalDate finalReportDate = reportDate == null ? LocalDate.now() : reportDate;
    String finalTitle = title == null || title.isBlank() ? "Imported record" : title;
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
          "UPLOAD",
          now(),
          now());
    } else {
      jdbcTemplate.update(
          "update records set disease_profile_id = coalesce(?, disease_profile_id), record_date = coalesce(?, record_date), title = coalesce(?, title), updated_at = ? where id = ?",
          diseaseProfileId,
          reportDate,
          title == null || title.isBlank() ? null : title,
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
      String title) {
    UUID assetId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into assets (id, tenant_id, record_id, object_key, file_type, file_size, checksum, created_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?)",
        assetId,
        DEFAULT_TENANT_ID,
        ensureRecord(recordId, diseaseProfileId, reportDate, title),
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

  public Map<String, String> parseJobContext(UUID jobId) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "select record_id, tenant_id from parse_jobs where id = ?",
        jobId);
    return Map.of(
        "recordId", String.valueOf(row.get("record_id")),
        "tenantId", String.valueOf(row.get("tenant_id")),
        "userId", DEFAULT_USER_ID.toString());
  }

  public void applyParseResult(UUID jobId, String status, String structuredResultJson, double confidence, String errorCode) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "select record_id, status, retry_count from parse_jobs where id = ?",
        jobId);
    UUID recordId = (UUID) row.get("record_id");
    String currentStatus = String.valueOf(row.get("status"));
    int retryCount = ((Number) row.get("retry_count")).intValue();
    if ("SUCCESS".equals(currentStatus) || "DEAD_LETTER".equals(currentStatus)) {
      return;
    }

    int progress = "SUCCESS".equals(status) ? 100 : 100;
    int nextRetryCount = retryCount;
    String nextStatus = status;
    if (!"SUCCESS".equals(status)) {
      nextRetryCount = retryCount + 1;
      if (nextRetryCount > 2) {
        nextStatus = "DEAD_LETTER";
      }
    }

    jdbcTemplate.update(
        "update parse_jobs set status = ?, progress = ?, error_code = ?, retry_count = ?, updated_at = ? where id = ?",
        nextStatus,
        progress,
        errorCode,
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
    return Map.of("recordId", recordId.toString(), "summary", summary, "structuredResult", latestResult);
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

  public List<Map<String, Object>> listDiseaseProfiles() {
    return jdbcTemplate.queryForList(
        "select id, name, updated_at from disease_profiles where tenant_id = ? and user_id = ? order by updated_at desc, name asc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
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
        "select coalesce(dp.id::text, 'unknown') as batch_id, coalesce(dp.name, 'Unassigned') as disease_name, "
            + "count(*) as record_count, max(r.record_date) as latest_record_at "
            + "from records r left join disease_profiles dp on dp.id = r.disease_profile_id "
            + "group by coalesce(dp.id::text, 'unknown'), coalesce(dp.name, 'Unassigned') "
            + "order by latest_record_at desc");
  }

  public List<Map<String, Object>> listRecordsByBatch(String batchId) {
    return jdbcTemplate.queryForList(
        "select id, title, record_date from records where disease_profile_id::text = ? order by record_date desc",
        batchId);
  }

  private UUID ensureDefaultDiseaseProfile() {
    return createDiseaseProfile("General");
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
