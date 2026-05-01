package com.medical.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.agent.application.context.TenantContextProvider;
import com.medical.agent.infrastructure.persistence.ScopeConstants;
import com.medical.agent.infrastructure.persistence.entity.AssetEntity;
import com.medical.agent.infrastructure.persistence.entity.GeneratedOutputEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobAssetEntity;
import com.medical.agent.infrastructure.persistence.entity.ParseJobEntity;
import com.medical.agent.infrastructure.persistence.entity.RecordEntity;
import com.medical.agent.infrastructure.persistence.entity.StructuredResultEntity;
import com.medical.agent.infrastructure.persistence.mapper.AssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.DataRightsRequestMapper;
import com.medical.agent.infrastructure.persistence.mapper.DiseaseProfileMapper;
import com.medical.agent.infrastructure.persistence.mapper.GeneratedOutputMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobAssetMapper;
import com.medical.agent.infrastructure.persistence.mapper.ParseJobMapper;
import com.medical.agent.infrastructure.persistence.mapper.RecordMapper;
import com.medical.agent.infrastructure.persistence.mapper.StructuredResultMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiseaseProfileServiceCancelParsingTest {
  @BeforeAll
  static void initMybatisPlusLambdaCache() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, RecordEntity.class);
    TableInfoHelper.initTableInfo(assistant, AssetEntity.class);
    TableInfoHelper.initTableInfo(assistant, ParseJobEntity.class);
    TableInfoHelper.initTableInfo(assistant, ParseJobAssetEntity.class);
    TableInfoHelper.initTableInfo(assistant, StructuredResultEntity.class);
    TableInfoHelper.initTableInfo(assistant, GeneratedOutputEntity.class);
    TableInfoHelper.initTableInfo(assistant, com.medical.agent.infrastructure.persistence.entity.DataRightsRequestEntity.class);
  }
  @Mock private DiseaseProfileMapper diseaseProfileMapper;
  @Mock private RecordMapper recordMapper;
  @Mock private AssetMapper assetMapper;
  @Mock private ParseJobMapper parseJobMapper;
  @Mock private ParseJobAssetMapper parseJobAssetMapper;
  @Mock private StructuredResultMapper structuredResultMapper;
  @Mock private GeneratedOutputMapper generatedOutputMapper;
  @Mock private DataRightsRequestMapper dataRightsRequestMapper;
  @Mock private OssPresignService ossPresignService;
  @Mock private TenantContextProvider tenantContextProvider;

  private DiseaseProfileService service;

  @BeforeEach
  void setUp() {
    when(tenantContextProvider.currentTenantId()).thenReturn(ScopeConstants.DEFAULT_TENANT_ID);
    when(tenantContextProvider.currentPatientId()).thenReturn(ScopeConstants.DEFAULT_PATIENT_ID);
    service = new DiseaseProfileService(
        diseaseProfileMapper, recordMapper, assetMapper, parseJobMapper, parseJobAssetMapper,
        structuredResultMapper, generatedOutputMapper, dataRightsRequestMapper,
        ossPresignService, tenantContextProvider);
  }

  @Test
  void cancelDeletesOnlyNonSuccessRecordsAndPreservesSuccess() {
    UUID profileId = UUID.randomUUID();
    UUID queuedRecordId = UUID.randomUUID();
    UUID successRecordId = UUID.randomUUID();

    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(queuedRecordId, profileId),
        record(successRecordId, profileId)));

    // First record -> QUEUED, Second record -> SUCCESS
    ParseJobEntity queuedJob = parseJob(queuedRecordId, "QUEUED");
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(queuedJob))
        .thenReturn(List.of(parseJob(successRecordId, "SUCCESS")))
        .thenReturn(List.of(queuedJob)); // deleteRecordsCascadeInternal query

    when(assetMapper.selectList(any())).thenReturn(List.of(
        asset(queuedRecordId, "uploads/file1.pdf")));

    when(assetMapper.delete(any())).thenReturn(1);
    when(parseJobMapper.delete(any())).thenReturn(1);
    when(parseJobAssetMapper.delete(any())).thenReturn(1);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(1);

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords(profileId.toString());

    assertEquals(1, result.deletedRecordCount());
    assertEquals(1, result.deletedAssetCount());
    assertEquals(1, result.deletedParseJobCount());

    // Verify OSS cleanup for the queued record's asset
    verify(ossPresignService).deleteObject("uploads/file1.pdf");

    // Verify cascade deletes called
    verify(recordMapper).delete(any());
    verify(parseJobMapper).delete(any());
    verify(assetMapper).delete(any());
  }

  @Test
  void cancelReturnsZeroCountsWhenNoRecordsExist() {
    UUID profileId = UUID.randomUUID();
    when(recordMapper.selectList(any())).thenReturn(List.of());

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords(profileId.toString());

    assertEquals(0, result.deletedRecordCount());
    assertEquals(0, result.deletedAssetCount());
    assertEquals(0, result.deletedParseJobCount());

    verify(ossPresignService, never()).deleteObject(any());
    verify(recordMapper, never()).delete(any());
  }

  @Test
  void cancelReturnsZeroCountsWhenAllRecordsAreSuccess() {
    UUID profileId = UUID.randomUUID();
    UUID successRecordId = UUID.randomUUID();

    when(recordMapper.selectList(any())).thenReturn(List.of(record(successRecordId, profileId)));
    when(parseJobMapper.selectList(any())).thenReturn(List.of(parseJob(successRecordId, "SUCCESS")));

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords(profileId.toString());

    assertEquals(0, result.deletedRecordCount());
    verify(ossPresignService, never()).deleteObject(any());
    verify(recordMapper, never()).delete(any());
  }

  @Test
  void cancelHandlesFailedAndDeadLetterRecords() {
    UUID profileId = UUID.randomUUID();
    UUID failedId = UUID.randomUUID();
    UUID deadLetterId = UUID.randomUUID();
    UUID noJobId = UUID.randomUUID();

    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(failedId, profileId),
        record(deadLetterId, profileId),
        record(noJobId, profileId)));

    ParseJobEntity failedJob = parseJob(failedId, "FAILED");
    ParseJobEntity deadLetterJob = parseJob(deadLetterId, "DEAD_LETTER");
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(failedJob))
        .thenReturn(List.of(deadLetterJob))
        .thenReturn(List.of()) // no parse job = NOT_PARSED
        .thenReturn(List.of(failedJob, deadLetterJob)); // deleteRecordsCascadeInternal query

    when(assetMapper.selectList(any())).thenReturn(List.of());
    when(assetMapper.delete(any())).thenReturn(0);
    when(parseJobMapper.delete(any())).thenReturn(2);
    when(parseJobAssetMapper.delete(any())).thenReturn(0);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(3);

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords(profileId.toString());

    assertEquals(3, result.deletedRecordCount());
    assertEquals(2, result.deletedParseJobCount());
  }

  @Test
  void cancelOnlyAffectsSpecifiedProfileNotOtherProfiles() {
    UUID targetProfileId = UUID.randomUUID();
    UUID otherProfileId = UUID.randomUUID();
    UUID targetRecordId = UUID.randomUUID();
    UUID otherRecordId = UUID.randomUUID();

    // First call: target profile records. Second call: other profile would have records
    // but we only call cancelParsingRecords for targetProfileId
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(targetRecordId, targetProfileId)));

    ParseJobEntity targetJob = parseJob(targetRecordId, "QUEUED");
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(targetJob))
        .thenReturn(List.of(targetJob)); // deleteRecordsCascadeInternal query

    when(assetMapper.selectList(any())).thenReturn(List.of());
    when(assetMapper.delete(any())).thenReturn(0);
    when(parseJobMapper.delete(any())).thenReturn(1);
    when(parseJobAssetMapper.delete(any())).thenReturn(0);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(1);

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords(targetProfileId.toString());

    assertEquals(1, result.deletedRecordCount());
    assertEquals(targetProfileId.toString(), result.diseaseProfileId());
  }

  @Test
  void cancelCallsOssDeleteForEveryCandidateAsset() {
    UUID profileId = UUID.randomUUID();
    UUID record1 = UUID.randomUUID();
    UUID record2 = UUID.randomUUID();

    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(record1, profileId),
        record(record2, profileId)));

    ParseJobEntity job1 = parseJob(record1, "QUEUED");
    ParseJobEntity job2 = parseJob(record2, "FAILED");
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(job1))
        .thenReturn(List.of(job2))
        .thenReturn(List.of(job1, job2)); // deleteRecordsCascadeInternal query

    when(assetMapper.selectList(any())).thenReturn(List.of(
        asset(record1, "uploads/a.pdf"),
        asset(record2, "uploads/b.pdf")));

    when(assetMapper.delete(any())).thenReturn(2);
    when(parseJobMapper.delete(any())).thenReturn(2);
    when(parseJobAssetMapper.delete(any())).thenReturn(2);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(2);

    service.cancelParsingRecords(profileId.toString());

    verify(ossPresignService).deleteObject("uploads/a.pdf");
    verify(ossPresignService).deleteObject("uploads/b.pdf");
    verify(ossPresignService, times(2)).deleteObject(any());
  }

  @Test
  void cancelWithUnknownProfileIdQueriesForNullDiseaseProfileId() {
    UUID recordId = UUID.randomUUID();

    when(recordMapper.selectList(any())).thenReturn(List.of(record(recordId, null)));
    ParseJobEntity unknownJob = parseJob(recordId, "QUEUED");
    when(parseJobMapper.selectList(any()))
        .thenReturn(List.of(unknownJob))
        .thenReturn(List.of(unknownJob)); // deleteRecordsCascadeInternal query
    when(assetMapper.selectList(any())).thenReturn(List.of());
    when(assetMapper.delete(any())).thenReturn(0);
    when(parseJobMapper.delete(any())).thenReturn(1);
    when(parseJobAssetMapper.delete(any())).thenReturn(0);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(1);

    DiseaseProfileService.CancelParsingRecordsResult result =
        service.cancelParsingRecords("unknown");

    assertEquals(1, result.deletedRecordCount());
    assertEquals("unknown", result.diseaseProfileId());
  }

  @Test
  void deleteProfileRecordsRejectsRecordsOutsideProfileWithoutDeleting() {
    UUID profileId = UUID.randomUUID();
    UUID targetRecordId = UUID.randomUUID();
    UUID otherRecordId = UUID.randomUUID();

    when(diseaseProfileMapper.selectCount(any())).thenReturn(1L);
    when(recordMapper.selectList(any())).thenReturn(List.of(record(targetRecordId, profileId)));

    DiseaseProfileService.DeleteProfileRecordsResult result =
        service.deleteProfileRecords(profileId, List.of(targetRecordId, otherRecordId));

    assertTrue(result.profileExists());
    assertEquals(1, result.rejectedRecordIds().size());
    assertEquals(otherRecordId.toString(), result.rejectedRecordIds().get(0));
    assertEquals(0, result.deletedRecordCount());
    verify(ossPresignService, never()).deleteObject(any());
    verify(recordMapper, never()).delete(any());
  }

  @Test
  void deleteProfileRecordsDeletesScopedRecordsAndAssets() {
    UUID profileId = UUID.randomUUID();
    UUID firstRecordId = UUID.randomUUID();
    UUID secondRecordId = UUID.randomUUID();
    ParseJobEntity firstJob = parseJob(firstRecordId, "SUCCESS");
    ParseJobEntity secondJob = parseJob(secondRecordId, "SUCCESS");

    when(diseaseProfileMapper.selectCount(any())).thenReturn(1L);
    when(recordMapper.selectList(any())).thenReturn(List.of(
        record(firstRecordId, profileId),
        record(secondRecordId, profileId)));
    when(assetMapper.selectList(any())).thenReturn(List.of(
        asset(firstRecordId, "uploads/first.pdf"),
        asset(secondRecordId, "uploads/second.pdf")));
    when(parseJobMapper.selectList(any())).thenReturn(List.of(firstJob, secondJob));
    when(assetMapper.delete(any())).thenReturn(2);
    when(parseJobMapper.delete(any())).thenReturn(2);
    when(parseJobAssetMapper.delete(any())).thenReturn(2);
    when(structuredResultMapper.delete(any())).thenReturn(0);
    when(generatedOutputMapper.delete(any())).thenReturn(0);
    when(dataRightsRequestMapper.delete(any())).thenReturn(0);
    when(recordMapper.delete(any())).thenReturn(2);

    DiseaseProfileService.DeleteProfileRecordsResult result =
        service.deleteProfileRecords(profileId, List.of(firstRecordId, firstRecordId, secondRecordId));

    assertTrue(result.profileExists());
    assertTrue(result.rejectedRecordIds().isEmpty());
    assertEquals(2, result.requestedRecordCount());
    assertEquals(2, result.deletedRecordCount());
    assertEquals(2, result.deletedAssetCount());
    assertEquals(2, result.deletedParseJobCount());
    verify(ossPresignService).deleteObject("uploads/first.pdf");
    verify(ossPresignService).deleteObject("uploads/second.pdf");
  }

  private RecordEntity record(UUID id, UUID profileId) {
    RecordEntity record = new RecordEntity();
    record.setId(id);
    record.setDiseaseProfileId(profileId);
    record.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    record.setPatientId(ScopeConstants.DEFAULT_PATIENT_ID);
    return record;
  }

  private ParseJobEntity parseJob(UUID recordId, String status) {
    ParseJobEntity job = new ParseJobEntity();
    job.setId(UUID.randomUUID());
    job.setRecordId(recordId);
    job.setTenantId(ScopeConstants.DEFAULT_TENANT_ID);
    job.setStatus(status);
    return job;
  }

  private AssetEntity asset(UUID recordId, String objectKey) {
    AssetEntity asset = new AssetEntity();
    asset.setId(UUID.randomUUID());
    asset.setRecordId(recordId);
    asset.setObjectKey(objectKey);
    return asset;
  }
}
