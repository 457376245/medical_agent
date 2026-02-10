package com.medical.agent.application;

import com.medical.agent.application.repository.GeneratedOutputRepository;
import com.medical.agent.application.repository.ParseJobRepository;
import com.medical.agent.application.repository.RecordRepository;
import com.medical.agent.application.repository.StructuredResultRepository;
import com.medical.agent.domain.vo.AssetRef;
import com.medical.agent.domain.vo.DiseaseProfileSummary;
import com.medical.agent.domain.vo.GeneratedOutputSnapshot;
import com.medical.agent.domain.vo.ParseJobContext;
import com.medical.agent.domain.vo.RecordAnalysisContext;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.ReportCategorySummary;
import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistenceService {
  private final RecordRepository recordRepository;
  private final ParseJobRepository parseJobRepository;
  private final StructuredResultRepository structuredResultRepository;
  private final GeneratedOutputRepository generatedOutputRepository;

  public PersistenceService(
      RecordRepository recordRepository,
      ParseJobRepository parseJobRepository,
      StructuredResultRepository structuredResultRepository,
      GeneratedOutputRepository generatedOutputRepository) {
    this.recordRepository = recordRepository;
    this.parseJobRepository = parseJobRepository;
    this.structuredResultRepository = structuredResultRepository;
    this.generatedOutputRepository = generatedOutputRepository;
  }

  public record ParseApplyResult(UUID recordId, String finalStatus, boolean stateChanged) {}

  public record ParseRetryCandidate(UUID jobId, UUID recordId, int retryCount) {}

  public UUID ensureRecord(UUID recordId) {
    return recordRepository.ensureRecord(recordId);
  }

  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title) {
    return recordRepository.ensureRecord(recordId, diseaseProfileId, reportDate, title);
  }

  public UUID ensureRecord(UUID recordId, UUID diseaseProfileId, LocalDate reportDate, String title, String sourceType) {
    return recordRepository.ensureRecord(recordId, diseaseProfileId, reportDate, title, sourceType);
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
    return recordRepository.createAsset(
        objectKey,
        checksum,
        recordId,
        fileType,
        fileSize,
        diseaseProfileId,
        reportDate,
        title,
        sourceType);
  }

  public List<AssetRef> listAssetRefs(List<UUID> assetIds) {
    return recordRepository.listAssetRefs(assetIds);
  }

  public void bindParseJobAssets(UUID jobId, List<UUID> assetIds) {
    parseJobRepository.bindParseJobAssets(jobId, assetIds);
  }

  public List<AssetRef> listAssetRefsByJobId(UUID jobId) {
    return parseJobRepository.listAssetRefsByJobId(jobId);
  }

  public ParseJobContext parseJobContext(UUID jobId) {
    return parseJobRepository.parseJobContext(jobId);
  }

  @Transactional
  public ParseApplyResult applyParseResult(UUID jobId, String status, String structuredResultJson, double confidence, String errorCode) {
    ParseJobRepository.ParseApplyResult result = parseJobRepository.applyParseResult(
        jobId,
        status,
        structuredResultJson,
        confidence,
        errorCode);
    return new ParseApplyResult(result.recordId(), result.finalStatus(), result.stateChanged());
  }

  public UUID createOrReuseParseJob(UUID recordId, String idempotencyKey) {
    return parseJobRepository.createOrReuseParseJob(recordId, idempotencyKey);
  }

  public List<ParseRetryCandidate> listFailedParseJobsForRetry(int maxRetryCount, int limit) {
    return parseJobRepository.listFailedParseJobsForRetry(maxRetryCount, limit).stream()
        .map(candidate -> new ParseRetryCandidate(candidate.jobId(), candidate.recordId(), candidate.retryCount()))
        .toList();
  }

  public List<ParseRetryCandidate> listFailedParseJobsForDeadLetter(int maxRetryCount, int limit) {
    return parseJobRepository.listFailedParseJobsForDeadLetter(maxRetryCount, limit).stream()
        .map(candidate -> new ParseRetryCandidate(candidate.jobId(), candidate.recordId(), candidate.retryCount()))
        .toList();
  }

  public boolean markParseJobRetrying(UUID jobId) {
    return parseJobRepository.markParseJobRetrying(jobId);
  }

  public void markParseJobFailedAfterRetryDispatch(UUID jobId, String errorCode) {
    parseJobRepository.markParseJobFailedAfterRetryDispatch(jobId, errorCode);
  }

  public void markParseJobDeadLetter(UUID jobId, String errorCode) {
    parseJobRepository.markParseJobDeadLetter(jobId, errorCode);
  }

  public RecordDetail fetchRecord(UUID recordId) {
    return recordRepository.fetchRecord(recordId);
  }

  public Optional<GeneratedOutputSnapshot> fetchLatestGeneratedOutput(UUID recordId, String type) {
    return generatedOutputRepository.fetchLatestGeneratedOutput(recordId, type);
  }

  public Optional<RecordAnalysisContext> fetchRecordAnalysisContext(UUID recordId) {
    return structuredResultRepository.fetchRecordAnalysisContext(recordId);
  }

  public UUID createDiseaseProfile(String name) {
    return recordRepository.createDiseaseProfile(name);
  }

  public UUID createReportCategory(String name) {
    return recordRepository.createReportCategory(name);
  }

  public List<ReportCategorySummary> listReportCategories() {
    return recordRepository.listReportCategories();
  }

  public boolean reportCategoryExists(UUID reportCategoryId) {
    return recordRepository.reportCategoryExists(reportCategoryId);
  }

  public int countRecordsByReportCategory(UUID reportCategoryId) {
    return recordRepository.countRecordsByReportCategory(reportCategoryId);
  }

  public boolean deleteReportCategoryIfEmpty(UUID reportCategoryId) {
    return recordRepository.deleteReportCategoryIfEmpty(reportCategoryId);
  }

  public List<DiseaseProfileSummary> listDiseaseProfiles() {
    return recordRepository.listDiseaseProfiles();
  }

  public boolean diseaseProfileExists(UUID diseaseProfileId) {
    return recordRepository.diseaseProfileExists(diseaseProfileId);
  }

  public List<String> listAssetObjectKeysByDiseaseProfile(UUID diseaseProfileId) {
    return recordRepository.listAssetObjectKeysByDiseaseProfile(diseaseProfileId);
  }

  public int countRecordsByDiseaseProfile(UUID diseaseProfileId) {
    return recordRepository.countRecordsByDiseaseProfile(diseaseProfileId);
  }

  public boolean deleteDiseaseProfileIfEmpty(UUID diseaseProfileId) {
    return recordRepository.deleteDiseaseProfileIfEmpty(diseaseProfileId);
  }

  @Transactional
  public int deleteDiseaseProfileCascade(UUID diseaseProfileId) {
    return recordRepository.deleteDiseaseProfileCascade(diseaseProfileId);
  }

  public int createGeneratedOutput(UUID recordId, String type, String content) {
    return generatedOutputRepository.createGeneratedOutput(recordId, type, content);
  }

  public int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson) {
    return generatedOutputRepository.createGeneratedOutputWithMeta(recordId, type, content, modelMetaJson);
  }

  public List<TimelineBatchSummary> listTimelineBatches() {
    return recordRepository.listTimelineBatches();
  }

  public List<TimelineRecordSummary> listRecordsByBatch(String batchId) {
    return recordRepository.listRecordsByBatch(batchId);
  }

  public String getDiseaseNameByBatch(String batchId) {
    return recordRepository.getDiseaseNameByBatch(batchId);
  }

  public UpdateRecordSourceTypeResult updateRecordSourceType(UUID recordId, String sourceType) {
    return recordRepository.updateRecordSourceType(recordId, sourceType);
  }

  public RecordTrendData fetchRecordTrend(UUID recordId, int limit) {
    return recordRepository.fetchRecordTrend(recordId, limit);
  }

  @Transactional
  public boolean deleteRecord(UUID recordId) {
    return recordRepository.deleteRecord(recordId);
  }
}
