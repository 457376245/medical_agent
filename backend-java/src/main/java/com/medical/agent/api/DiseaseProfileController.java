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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "疾病档案", description = "疾病档案管理接口")
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
  @Operation(summary = "查询疾病档案列表", description = "返回当前用户下的疾病档案列表")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_101\",\"data\":{\"profiles\":[{\"id\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"name\":\"高血压\",\"updatedAt\":\"2026-02-24T10:00:00\",\"recordCount\":3}]}}")))
  })
  public ApiResponse<DiseaseProfileListResponseData> list() {
    List<DiseaseProfileSummary> profiles = diseaseProfileService.listProfiles();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileListResponseData(profiles));
  }

  @GetMapping("/overview")
  @Operation(summary = "查询疾病档案总览", description = "按最新记录返回疾病档案总览信息")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_102\",\"data\":{\"profiles\":[{\"profileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"diseaseName\":\"高血压\",\"recordCount\":3,\"latestRecordAt\":\"2026-02-24\",\"latestRecordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"latestRecordTitle\":\"高血压-门诊记录-2026-02-24\",\"latestParseStatus\":\"SUCCESS\"}]}}")))
  })
  public ApiResponse<DiseaseProfileOverviewResponseData> overview() {
    List<DiseaseProfileOverview> profiles = diseaseProfileQueryService.listProfiles();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileOverviewResponseData(profiles));
  }

  @GetMapping("/{profileId}/records")
  @Operation(summary = "查询疾病档案下的记录", description = "按疾病档案 ID 查询关联记录")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_103\",\"data\":{\"profileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"diseaseName\":\"高血压\",\"records\":[{\"id\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"title\":\"门诊随访\",\"recordDate\":\"2026-02-24\",\"sourceType\":\"OUTPATIENT\"}]}}")))
  })
  public ApiResponse<DiseaseProfileDetailResponseData> profileRecords(
      @Parameter(description = "疾病档案ID，传 unknown 可查询未分类疾病", example = "d5a113ca-56cf-4aca-a265-8f4ec0a3292c")
      @PathVariable("profileId") String profileId) {
    DiseaseProfileQueryService.ProfileRecordsResult result = diseaseProfileQueryService.listProfileRecords(profileId);
    String diseaseName = diseaseProfileQueryService.diseaseNameByProfile(profileId);
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new DiseaseProfileDetailResponseData(
            profileId,
            diseaseName,
            result.records(),
            result.examNodes(),
            result.parsingCount()));
  }

  @PostMapping
  @Operation(
      summary = "创建疾病档案",
      description = "根据名称创建疾病档案，若同名已存在则返回已有档案ID",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "疾病档案创建参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"name\":\"高血压\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "创建成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_104\",\"data\":{\"diseaseProfileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"name\":\"高血压\"}}")))
  })
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
  @Operation(summary = "删除疾病档案", description = "支持普通删除，或通过 onlyIfEmpty=true 仅删除空档案")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "删除成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"deleted\",\"requestId\":\"req_20260224_105\",\"data\":{\"diseaseProfileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"deleted\":true,\"reason\":\"DELETED\",\"linkedRecordCount\":null,\"deletedRecordCount\":3,\"deletedAssetCount\":3}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "参数错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_DISEASE_PROFILE_ID\",\"message\":\"diseaseProfileId is invalid\",\"requestId\":\"req_20260224_106\",\"data\":{\"diseaseProfileId\":\"abc\",\"deleted\":false}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "409",
          description = "存在关联记录，无法按空档案删除",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"CONFLICT\",\"message\":\"disease profile has associated records\",\"requestId\":\"req_20260224_107\",\"data\":{\"diseaseProfileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"deleted\":false,\"reason\":\"HAS_ASSOCIATED_RECORDS\",\"linkedRecordCount\":3,\"deletedRecordCount\":null,\"deletedAssetCount\":null}}")))
  })
  public ResponseEntity<ApiResponse<?>> delete(
      @Parameter(description = "疾病档案ID（UUID）", example = "d5a113ca-56cf-4aca-a265-8f4ec0a3292c")
      @PathVariable("diseaseProfileId") String diseaseProfileId,
      @Parameter(description = "是否仅允许删除空档案", example = "true")
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
