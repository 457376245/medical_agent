package com.medical.agent.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.repository.GeneratedOutputRepository;
import com.medical.agent.application.repository.RecordRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGeneratedOutputRepository implements GeneratedOutputRepository {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final JdbcTemplate jdbcTemplate;
  private final RecordRepository recordRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JdbcGeneratedOutputRepository(JdbcTemplate jdbcTemplate, RecordRepository recordRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.recordRepository = recordRepository;
  }

  @Override
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
        recordRepository.ensureRecord(recordId),
        type,
        finalVersion,
        content,
        "{\"provider\":\"gateway\"}",
        true,
        now());
    return finalVersion;
  }

  @Override
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
        recordRepository.ensureRecord(recordId),
        type,
        finalVersion,
        content,
        modelMetaJson,
        true,
        now());
    return finalVersion;
  }

  @Override
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
