package com.medical.agent.api;

import com.medical.agent.application.DiseaseProfileService;
import com.medical.agent.application.PersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disease-profiles")
public class DiseaseProfileController {
  private final PersistenceService persistenceService;
  private final DiseaseProfileService diseaseProfileService;

  public DiseaseProfileController(PersistenceService persistenceService, DiseaseProfileService diseaseProfileService) {
    this.persistenceService = persistenceService;
    this.diseaseProfileService = diseaseProfileService;
  }

  @GetMapping
  public Map<String, Object> list() {
    List<Map<String, Object>> profiles = persistenceService.listDiseaseProfiles();
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("profiles", profiles));
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    UUID profileId = persistenceService.createDiseaseProfile(name);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("diseaseProfileId", profileId.toString(), "name", name));
  }

  @DeleteMapping("/{diseaseProfileId}")
  public ResponseEntity<Map<String, Object>> delete(
      @PathVariable("diseaseProfileId") String diseaseProfileId,
      @RequestParam(value = "onlyIfEmpty", defaultValue = "false") boolean onlyIfEmpty) {
    if (onlyIfEmpty) {
      DiseaseProfileService.DeleteDiseaseProfileIfEmptyResult result =
          diseaseProfileService.deleteProfileIfEmpty(UUID.fromString(diseaseProfileId));
      if (!result.deleted() && "NOT_FOUND".equals(result.reason())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "code", "NOT_FOUND",
            "message", "disease profile not found",
            "requestId", RequestIdUtil.newRequestId(),
            "data", Map.of(
                "diseaseProfileId", diseaseProfileId,
                "deleted", false,
                "reason", result.reason(),
                "linkedRecordCount", result.linkedRecordCount())));
      }
      if (!result.deleted() && "HAS_ASSOCIATED_RECORDS".equals(result.reason())) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "code", "CONFLICT",
            "message", "disease profile has associated records",
            "requestId", RequestIdUtil.newRequestId(),
            "data", Map.of(
                "diseaseProfileId", diseaseProfileId,
                "deleted", false,
                "reason", result.reason(),
                "linkedRecordCount", result.linkedRecordCount())));
      }
      if (!result.deleted()) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "code", "DELETE_FAILED",
            "message", "failed to delete disease profile",
            "requestId", RequestIdUtil.newRequestId(),
            "data", Map.of(
                "diseaseProfileId", diseaseProfileId,
                "deleted", false,
                "reason", result.reason(),
                "linkedRecordCount", result.linkedRecordCount())));
      }
      return ResponseEntity.ok(Map.of(
          "code", "OK",
          "message", "deleted",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of(
              "diseaseProfileId", diseaseProfileId,
              "deleted", true,
              "reason", result.reason(),
              "linkedRecordCount", result.linkedRecordCount())));
    }

    DiseaseProfileService.DeleteDiseaseProfileResult result =
        diseaseProfileService.deleteProfile(UUID.fromString(diseaseProfileId));
    if (!result.deleted()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "code", "NOT_FOUND",
          "message", "disease profile not found",
          "requestId", RequestIdUtil.newRequestId(),
          "data", Map.of("diseaseProfileId", diseaseProfileId, "deleted", false)));
    }
    return ResponseEntity.ok(Map.of(
        "code", "OK",
        "message", "deleted",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of(
            "diseaseProfileId", diseaseProfileId,
            "deleted", true,
            "deletedRecordCount", result.deletedRecordCount(),
            "deletedAssetCount", result.deletedAssetCount())));
  }
}
