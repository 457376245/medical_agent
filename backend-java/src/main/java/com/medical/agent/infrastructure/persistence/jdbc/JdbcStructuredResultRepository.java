package com.medical.agent.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.repository.StructuredResultRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStructuredResultRepository implements StructuredResultRepository {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JdbcStructuredResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Map<String, Object> patchStructuredResult(UUID recordId, int revision, String payloadJson) {
    UUID resultId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into structured_results (id, tenant_id, job_id, record_id, schema_version, payload_json, confidence_score, revision, is_user_edited, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)",
        resultId,
        DEFAULT_TENANT_ID,
        UUID.randomUUID(),
        recordId,
        "v1",
        payloadJson,
        0.8,
        revision,
        true,
        now(),
        now());
    return Map.of("resultId", resultId.toString(), "revision", revision);
  }

  @Override
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
}
