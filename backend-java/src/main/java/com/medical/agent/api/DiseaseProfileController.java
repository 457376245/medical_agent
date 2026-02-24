package com.medical.agent.api;

import com.medical.agent.application.DiseaseProfileQueryService;
import com.medical.agent.application.DiseaseProfileService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.NameRequest;
import com.medical.agent.domain.dto.response.DiseaseProfileCreateResponseData;
import com.medical.agent.domain.dto.response.DiseaseProfileDeleteResponseData;
import com.medical.agent.domain.dto.response.DiseaseProfileDetailResponseData;
import com.medical.agent.domain.dto.response.DiseaseProfileListResponseData;
import com.medical.agent.domain.dto.response.DiseaseProfileOverviewResponseData;
import com.medical.agent.domain.dto.response.DiseaseProfileRefResponseData;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.domain.vo.DiseaseProfileSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disease-profiles")
public class DiseaseProfileController {
  private final DiseaseProfileService diseaseProfileService;
  private final DiseaseProfileQueryService diseaseProfileQueryService;

  public DiseaseProfileController(
      DiseaseProfileService diseaseProfileService,
      DiseaseProfileQueryService diseaseProfileQueryService) {
    this.diseaseProfileService = diseaseProfileService;
    this.diseaseProfileQueryService = diseaseProfileQueryService;
  }

  @GetMapping
  public ApiResponse<DiseaseProfileListResponseData> list() {
    List<DiseaseProfileSummary> profiles = diseaseProfileService.listProfiles();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileListResponseData(profiles));
  }

  @GetMapping("/overview")
  public ApiResponse<DiseaseProfileOverviewResponseData> overview() {
    List<DiseaseProfileOverview> profiles = diseaseProfileQueryService.listProfiles();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileOverviewResponseData(profiles));
  }

  @GetMapping("/{profileId}/records")
  public ApiResponse<DiseaseProfileDetailResponseData> profileRecords(@PathVariable("profileId") String profileId) {
    List<DiseaseProfileRecordSummary> records = diseaseProfileQueryService.listProfileRecords(profileId);
    String diseaseName = diseaseProfileQueryService.diseaseNameByProfile(profileId);
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileDetailResponseData(profileId, diseaseName, records));
  }

  @PostMapping
  public ApiResponse<DiseaseProfileCreateResponseData> create(@RequestBody NameRequest request) {
    String name = request == null || request.name() == null ? "" : request.name().trim();
    UUID profileId = diseaseProfileService.createProfile(name);
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileCreateResponseData(profileId.toString(), name));
  }

  @DeleteMapping("/{diseaseProfileId}")
  public ResponseEntity<ApiResponse<?>> delete(
      @PathVariable("diseaseProfileId") String diseaseProfileId,
      @RequestParam(value = "onlyIfEmpty", defaultValue = "false") boolean onlyIfEmpty) {
    UUID diseaseProfileUuid;
    try {
      diseaseProfileUuid = UUID.fromString(diseaseProfileId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_DISEASE_PROFILE_ID",
          "diseaseProfileId is invalid",
          RequestIdUtil.newRequestId(),
          new DiseaseProfileRefResponseData(diseaseProfileId, false)));
    }

    if (onlyIfEmpty) {
      DiseaseProfileService.DeleteDiseaseProfileIfEmptyResult result =
          diseaseProfileService.deleteProfileIfEmpty(diseaseProfileUuid);
      if (!result.deleted() && "NOT_FOUND".equals(result.reason())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
            "NOT_FOUND",
            "disease profile not found",
            RequestIdUtil.newRequestId(),
            new DiseaseProfileDeleteResponseData(
                diseaseProfileId,
                false,
                result.reason(),
                result.linkedRecordCount(),
                null,
                null)));
      }
      if (!result.deleted() && "HAS_ASSOCIATED_RECORDS".equals(result.reason())) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(
            "CONFLICT",
            "disease profile has associated records",
            RequestIdUtil.newRequestId(),
            new DiseaseProfileDeleteResponseData(
                diseaseProfileId,
                false,
                result.reason(),
                result.linkedRecordCount(),
                null,
                null)));
      }
      if (!result.deleted()) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
            "DELETE_FAILED",
            "failed to delete disease profile",
            RequestIdUtil.newRequestId(),
            new DiseaseProfileDeleteResponseData(
                diseaseProfileId,
                false,
                result.reason(),
                result.linkedRecordCount(),
                null,
                null)));
      }
      return ResponseEntity.ok(new ApiResponse<>(
          "OK",
          "deleted",
          RequestIdUtil.newRequestId(),
          new DiseaseProfileDeleteResponseData(
              diseaseProfileId,
              true,
              result.reason(),
              result.linkedRecordCount(),
              null,
              null)));
    }

    DiseaseProfileService.DeleteDiseaseProfileResult result = diseaseProfileService.deleteProfile(diseaseProfileUuid);
    if (!result.deleted()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "disease profile not found",
          RequestIdUtil.newRequestId(),
          new DiseaseProfileRefResponseData(diseaseProfileId, false)));
    }
    return ResponseEntity.ok(new ApiResponse<>(
        "OK",
        "deleted",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileDeleteResponseData(
            diseaseProfileId,
            true,
            "DELETED",
            null,
            result.deletedRecordCount(),
            result.deletedAssetCount())));
  }
}
