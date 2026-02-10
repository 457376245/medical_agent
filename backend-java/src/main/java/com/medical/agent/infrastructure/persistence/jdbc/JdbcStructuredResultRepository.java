package com.medical.agent.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.repository.StructuredResultRepository;
import com.medical.agent.domain.vo.RecordAnalysisContext;
import com.medical.agent.domain.vo.StructuredResultData;
import java.util.Map;
import java.util.Optional;
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
  public Optional<RecordAnalysisContext> fetchRecordAnalysisContext(UUID recordId) {
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
      return Optional.empty();
    }

    Map<String, Object> latestResult = queryLatestStructuredResult(recordId);
    String parseStatus = queryLatestParseStatus(recordId);
    StructuredResultData structuredResult = new StructuredResultData(
        String.valueOf(latestResult.get("schemaVersion")),
        ((Number) latestResult.get("revision")).intValue(),
        (JsonNode) latestResult.get("payload"));

    return Optional.of(new RecordAnalysisContext(
        String.valueOf(record.get("id")),
        record.get("title") == null ? "未命名报告" : String.valueOf(record.get("title")),
        String.valueOf(record.get("record_date")),
        String.valueOf(record.get("source_type")),
        String.valueOf(record.get("disease_name")),
        parseStatus,
        structuredResult));
  }

  private Map<String, Object> queryLatestStructuredResult(UUID recordId) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "select schema_version, revision, payload_json "
              + "from structured_results where record_id = ? "
              + "order by revision desc limit 1",
          recordId);
      return Map.of(
          "schemaVersion", String.valueOf(row.get("schema_version")),
          "revision", row.get("revision"),
          "payload", parsePayload(String.valueOf(row.get("payload_json"))));
    } catch (EmptyResultDataAccessException ignored) {
      return Map.of(
          "schemaVersion", "v1",
          "revision", 0,
          "payload", objectMapper.createObjectNode().putArray("fields"));
    }
  }

  private String queryLatestParseStatus(UUID recordId) {
    try {
      return String.valueOf(jdbcTemplate.queryForObject(
          "select status from parse_jobs where record_id = ? order by updated_at desc, created_at desc limit 1",
          String.class,
          recordId));
    } catch (EmptyResultDataAccessException ignored) {
      return "NOT_PARSED";
    }
  }

  private JsonNode parsePayload(String payloadJson) {
    try {
      return objectMapper.readTree(payloadJson);
    } catch (Exception ignored) {
      return objectMapper.createObjectNode().putArray("fields");
    }
  }
}
