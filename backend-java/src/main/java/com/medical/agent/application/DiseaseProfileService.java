package com.medical.agent.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiseaseProfileService {
  private final PersistenceService persistenceService;
  private final OssPresignService ossPresignService;

  public DiseaseProfileService(PersistenceService persistenceService, OssPresignService ossPresignService) {
    this.persistenceService = persistenceService;
    this.ossPresignService = ossPresignService;
  }

  @Transactional
  public DeleteDiseaseProfileResult deleteProfile(UUID diseaseProfileId) {
    if (!persistenceService.diseaseProfileExists(diseaseProfileId)) {
      return new DeleteDiseaseProfileResult(false, 0, 0);
    }

    List<String> objectKeys = persistenceService.listAssetObjectKeysByDiseaseProfile(diseaseProfileId);
    for (String objectKey : objectKeys) {
      ossPresignService.deleteObject(objectKey);
    }

    int deletedRecords = persistenceService.deleteDiseaseProfileCascade(diseaseProfileId);
    return new DeleteDiseaseProfileResult(true, deletedRecords, objectKeys.size());
  }

  @Transactional
  public DeleteDiseaseProfileIfEmptyResult deleteProfileIfEmpty(UUID diseaseProfileId) {
    if (!persistenceService.diseaseProfileExists(diseaseProfileId)) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "NOT_FOUND", 0);
    }

    int linkedRecordCount = persistenceService.countRecordsByDiseaseProfile(diseaseProfileId);
    if (linkedRecordCount > 0) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "HAS_ASSOCIATED_RECORDS", linkedRecordCount);
    }

    boolean deleted = persistenceService.deleteDiseaseProfileIfEmpty(diseaseProfileId);
    if (!deleted) {
      return new DeleteDiseaseProfileIfEmptyResult(false, "DELETE_FAILED", 0);
    }
    return new DeleteDiseaseProfileIfEmptyResult(true, "DELETED", 0);
  }

  public record DeleteDiseaseProfileResult(boolean deleted, int deletedRecordCount, int deletedAssetCount) {}

  public record DeleteDiseaseProfileIfEmptyResult(boolean deleted, String reason, int linkedRecordCount) {}
}
