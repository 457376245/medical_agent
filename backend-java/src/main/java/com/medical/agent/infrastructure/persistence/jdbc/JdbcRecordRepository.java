package com.medical.agent.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medical.agent.application.repository.RecordRepository;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.DiseaseProfileSummary;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.domain.vo.StructuredResultData;
import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
  public List<AssetRef> listAssetRefs(List<UUID> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }
    List<AssetRef> refs = new ArrayList<>();
    for (UUID assetId : assetIds) {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "select id, object_key, file_type from assets where id = ?",
          assetId);
      refs.add(new AssetRef(
          String.valueOf(row.get("id")),
          String.valueOf(row.get("object_key")),
          String.valueOf(row.get("file_type"))));
    }
    return refs;
  }

  @Override
  public RecordDetail fetchRecord(UUID recordId) {
    try {
      jdbcTemplate.queryForMap(
          "select id from records where id = ? and tenant_id = ? and user_id = ?",
          recordId,
          DEFAULT_TENANT_ID,
          DEFAULT_USER_ID);
    } catch (EmptyResultDataAccessException ignored) {
      throw new IllegalArgumentException("record not found");
    }

    String summary = querySummary(recordId);
    String parseStatus = queryLatestParseStatus(recordId);
    StructuredResultData structuredResult = queryLatestStructuredResult(recordId);

    return new RecordDetail(recordId.toString(), summary, parseStatus, structuredResult);
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
  public List<DiseaseProfileSummary> listDiseaseProfiles() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select dp.id, dp.name, dp.updated_at, count(r.id) as record_count "
            + "from disease_profiles dp "
            + "left join records r on r.disease_profile_id = dp.id and r.tenant_id = dp.tenant_id "
            + "where dp.tenant_id = ? and dp.user_id = ? "
            + "group by dp.id, dp.name, dp.updated_at "
            + "order by dp.updated_at desc, dp.name asc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);

    List<DiseaseProfileSummary> profiles = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      profiles.add(new DiseaseProfileSummary(
          String.valueOf(row.get("id")),
          String.valueOf(row.get("name")),
          String.valueOf(row.get("updated_at")),
          ((Number) row.get("record_count")).intValue()));
    }
    return profiles;
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
  public List<ReportCategorySummary> listReportCategories() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select rc.id, rc.name, rc.updated_at, count(r.id) as record_count "
            + "from report_categories rc "
            + "left join records r on r.source_type = rc.name and r.tenant_id = rc.tenant_id and r.user_id = rc.user_id "
            + "where rc.tenant_id = ? and rc.user_id = ? "
            + "group by rc.id, rc.name, rc.updated_at "
            + "order by rc.updated_at desc, rc.name asc",
        DEFAULT_TENANT_ID,
        DEFAULT_USER_ID);

    List<ReportCategorySummary> categories = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      categories.add(new ReportCategorySummary(
          String.valueOf(row.get("id")),
          String.valueOf(row.get("name")),
          String.valueOf(row.get("updated_at")),
          ((Number) row.get("record_count")).intValue()));
    }
    return categories;
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
  public List<TimelineBatchSummary> listTimelineBatches() {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
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

    List<TimelineBatchSummary> batches = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      batches.add(new TimelineBatchSummary(
          String.valueOf(row.get("batch_id")),
          String.valueOf(row.get("disease_name")),
          ((Number) row.get("record_count")).intValue(),
          String.valueOf(row.get("latest_record_at")),
          String.valueOf(row.get("latest_record_id")),
          String.valueOf(row.get("latest_record_title")),
          String.valueOf(row.get("latest_parse_status"))));
    }
    return batches;
  }

  @Override
  public List<TimelineRecordSummary> listRecordsByBatch(String batchId) {
    List<Map<String, Object>> rows;
    if ("unknown".equalsIgnoreCase(batchId)) {
      rows = jdbcTemplate.queryForList(
          "select id, title, record_date, source_type from records "
              + "where disease_profile_id is null and tenant_id = ? order by record_date desc",
          DEFAULT_TENANT_ID);
    } else {
      rows = jdbcTemplate.queryForList(
          "select id, title, record_date, source_type from records where disease_profile_id::text = ? order by record_date desc",
          batchId);
    }

    List<TimelineRecordSummary> records = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      records.add(new TimelineRecordSummary(
          String.valueOf(row.get("id")),
          row.get("title") == null ? "未命名报告" : String.valueOf(row.get("title")),
          String.valueOf(row.get("record_date")),
          String.valueOf(row.get("source_type"))));
    }
    return records;
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
  public UpdateRecordSourceTypeResult updateRecordSourceType(UUID recordId, String sourceType) {
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
      return new UpdateRecordSourceTypeResult(false, null, null, null, null);
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
      return new UpdateRecordSourceTypeResult(false, null, null, null, null);
    }
    return new UpdateRecordSourceTypeResult(true, normalizedSourceType, nextTitle, recordDate, diseaseName);
  }

  @Override
  public RecordTrendData fetchRecordTrend(UUID recordId, int limit) {
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

    List<TrendSnapshot> snapshots = new ArrayList<>();
    for (Map<String, Object> row : window) {
      UUID rowRecordId = (UUID) row.get("id");
      JsonNode payload = queryLatestPayloadNode(rowRecordId);
      List<TrendField> fields = extractTrendFields(payload);
      snapshots.add(new TrendSnapshot(
          rowRecordId.toString(),
          String.valueOf(row.get("record_date")),
          row.get("title") == null ? "未命名报告" : String.valueOf(row.get("title")),
          String.valueOf(row.get("source_type")),
          fields));
    }

    return new RecordTrendData(
        recordId.toString(),
        sourceType,
        diseaseProfileId == null ? "unknown" : diseaseProfileId.toString(),
        Math.max(1, limit),
        snapshots);
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

  private String querySummary(UUID recordId) {
    List<Map<String, Object>> outputs = jdbcTemplate.queryForList(
        "select type, version, content from generated_outputs where record_id = ? order by version desc",
        recordId);
    return outputs.stream()
        .filter(row -> "SUMMARY".equals(String.valueOf(row.get("type"))))
        .findFirst()
        .map(row -> String.valueOf(row.get("content")))
        .orElse("No summary yet.");
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

  private StructuredResultData queryLatestStructuredResult(UUID recordId) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          "select schema_version, revision, payload_json from structured_results where record_id = ? order by revision desc limit 1",
          recordId);
      return new StructuredResultData(
          String.valueOf(row.get("schema_version")),
          ((Number) row.get("revision")).intValue(),
          parsePayload(String.valueOf(row.get("payload_json"))));
    } catch (EmptyResultDataAccessException ignored) {
      return new StructuredResultData("v1", 0, objectMapper.createObjectNode());
    }
  }

  private JsonNode queryLatestPayloadNode(UUID recordId) {
    try {
      String payloadJson = String.valueOf(jdbcTemplate.queryForObject(
          "select payload_json from structured_results where record_id = ? order by revision desc limit 1",
          String.class,
          recordId));
      return parsePayload(payloadJson);
    } catch (EmptyResultDataAccessException ignored) {
      return objectMapper.createObjectNode().putArray("fields");
    }
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

  private List<TrendField> extractTrendFields(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      return List.of();
    }
    JsonNode fieldsNode = payload.path("fields");
    if (!fieldsNode.isArray()) {
      return List.of();
    }

    List<TrendField> fields = new ArrayList<>();
    for (JsonNode fieldNode : fieldsNode) {
      if (!fieldNode.isObject()) {
        continue;
      }
      String name = readStringField(fieldNode, "name");
      String value = readStringField(fieldNode, "value");
      if (name.isEmpty() || value.isEmpty()) {
        continue;
      }
      String unit = readStringField(fieldNode, "unit");
      String referenceRange = readStringField(fieldNode, "referenceRange");
      if (unit.isEmpty()) {
        unit = null;
      }
      if (referenceRange.isEmpty()) {
        referenceRange = null;
      }
      fields.add(new TrendField(name, value, unit, referenceRange));
    }
    return fields;
  }

  private String readStringField(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return "";
    }
    return value.asText("").trim();
  }

  private JsonNode parsePayload(String payloadJson) {
    try {
      JsonNode parsed = objectMapper.readTree(payloadJson);
      if (parsed.isObject()) {
        return parsed;
      }
      ObjectNode fallback = objectMapper.createObjectNode();
      fallback.put("raw", payloadJson);
      return fallback;
    } catch (Exception ignored) {
      return objectMapper.createObjectNode();
    }
  }

  private Timestamp now() {
    return Timestamp.from(Instant.now());
  }
}
