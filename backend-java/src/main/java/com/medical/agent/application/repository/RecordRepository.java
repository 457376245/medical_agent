package com.medical.agent.application.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.DiseaseProfileSummary;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;

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

  List<AssetRef> listAssetRefs(List<UUID> assetIds);

  RecordDetail fetchRecord(UUID recordId);

  UUID createDiseaseProfile(String name);

  List<DiseaseProfileSummary> listDiseaseProfiles();

  boolean diseaseProfileExists(UUID diseaseProfileId);

  List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId);

  int countRecordsByDiseaseProfile(UUID diseaseProfileId);

  boolean deleteDiseaseProfileIfEmpty(UUID diseaseProfileId);

  int deleteDiseaseProfileCascade(UUID diseaseProfileId);

  UUID createReportCategory(String name);

  List<ReportCategorySummary> listReportCategories();

  boolean reportCategoryExists(UUID reportCategoryId);

  int countRecordsByReportCategory(UUID reportCategoryId);

  boolean deleteReportCategoryIfEmpty(UUID reportCategoryId);

  List<TimelineBatchSummary> listTimelineBatches();

  List<TimelineRecordSummary> listRecordsByBatch(String batchId);

  String getDiseaseNameByBatch(String batchId);

  UpdateRecordSourceTypeResult updateRecordSourceType(UUID recordId, String sourceType);

  RecordTrendData fetchRecordTrend(UUID recordId, int limit);

  boolean deleteRecord(UUID recordId);
}
