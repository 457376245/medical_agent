package com.medical.agent.application.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RecordRepository {
  UUID ensureRecord(UUID recordId);

  UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title);

  UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title, String sourceType);

  UUID createAsset(
      String objectKey,
      String checksum,
      UUID recordId,
      String fileType,
      long fileSize,
      UUID diseaseProfileId,
      LocalDate reportDate,
      String title,
      String sourceType);

  List<Map<String, Object>> listAssetRefs(List<UUID> assetIds);

  Map<String, Object> fetchRecord(UUID recordId);

  UUID createDiseaseProfile(String name);

  List<Map<String, Object>> listDiseaseProfiles();

  boolean diseaseProfileExists(UUID diseaseProfileId);

  List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId);

  int countRecordsByDiseaseProfile(UUID diseaseProfileId);

  boolean deleteDiseaseProfileIfEmpty(UUID diseaseProfileId);

  int deleteDiseaseProfileCascade(UUID diseaseProfileId);

  UUID createReportCategory(String name);

  List<Map<String, Object>> listReportCategories();

  boolean reportCategoryExists(UUID reportCategoryId);

  int countRecordsByReportCategory(UUID reportCategoryId);

  boolean deleteReportCategoryIfEmpty(UUID reportCategoryId);

  List<Map<String, Object>> listTimelineBatches();

  List<Map<String, Object>> listRecordsByBatch(String batchId);

  String getDiseaseNameByBatch(String batchId);

  Map<String, Object> updateRecordSourceType(UUID recordId, String sourceType);

  Map<String, Object> fetchRecordTrend(UUID recordId, int limit);

  boolean deleteRecord(UUID recordId);
}
