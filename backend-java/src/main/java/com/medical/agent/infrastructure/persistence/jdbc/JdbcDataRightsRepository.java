package com.medical.agent.infrastructure.persistence.jdbc;

import com.medical.agent.application.repository.DataRightsRepository;
import com.medical.agent.application.repository.RecordRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDataRightsRepository implements DataRightsRepository {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private final JdbcTemplate jdbcTemplate;
  private final RecordRepository recordRepository;

  public JdbcDataRightsRepository(JdbcTemplate jdbcTemplate, RecordRepository recordRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.recordRepository = recordRepository;
  }

  @Override
  public UUID createDataRightsRequest(UUID recordId, String requestType) {
    UUID requestId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into data_rights_requests (id, tenant_id, user_id, record_id, request_type, status, download_url, expire_at, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, null, null, ?, ?)",
        requestId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID,
        recordRepository.ensureRecord(recordId),
        requestType,
        "REQUESTED",
        now(),
        now());
    return requestId;
  }

  @Override
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

  private Timestamp now() {
    return Timestamp.from(Instant.now());
  }
}
