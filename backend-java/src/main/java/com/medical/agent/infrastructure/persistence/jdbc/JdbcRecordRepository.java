package com.medical.agent.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.application.repository.RecordRepository;
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
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecordRepository implements RecordRepository {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final int MAX_REPORT_CATEGORY_NAME_LENGTH = 64;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JdbcRecordRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public UUID ensureRecord(UUID recordId) {
    return ensureRecord(recordId, null, null, null, null);
  }

  @Override
  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title) {
    return ensureRecord(recordId, diseaseProfileId, reportDate, title, null);
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
  public Map<String, Object> fetchRecord(UUID recordId) {
    try {
      jdbcTemplate.queryForMap(
          "select id from records where id = ? and tenant_id = ? and user_id = ?",
          recordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
    } catch (EmptyResultDataAccessException ignored) {
      throw new IllegalArgumentException("record not found");
    }
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

  @Override
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

  @Override
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

  @Override
  public boolean diseaseProfileExists(UUID diseaseProfileId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from disease_profiles where id = ? and tenant_id = ? and user_id = ?",
        Integer.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return count != null && count > 0;
  }

  @Override
  public List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId) {
    return jdbcTemplate.queryForList(
        "select a.object_key "
            + "from assets a join records r on r.id = a.record_id "
            + "where r.disease_profile_id = ? and r.tenant_id = ?",
        String.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID);
  }

  @Override
  public int countRecordsByDiseaseProfile(UUID diseaseProfileId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from records where disease_profile_id = ? and tenant_id = ?",
        Integer.class,
        diseaseProfileId,
        DEFAULT_TENANT_ID);
    return count == null ? 0 : count;
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
  public boolean reportCategoryExists(UUID reportCategoryId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(1) from report_categories where id = ? and tenant_id = ? and user_id = ?",
        Integer.class,
        reportCategoryId,
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);
    return count != null && count > 0;
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
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
