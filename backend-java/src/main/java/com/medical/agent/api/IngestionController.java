package com.medical.agent.api;

import com.medical.agent.application.service.IngestionService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.CompleteAssetRequest;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.request.PresignRequest;
import com.medical.agent.domain.dto.request.ProxyUploadRequest;
import com.medical.agent.domain.dto.response.AssetCreatedResponseData;
import com.medical.agent.domain.dto.response.PresignResponseData;
import com.medical.agent.domain.dto.response.ProxyUploadResponseData;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.dto.response.ParseJobStatusResponseData;
import com.medical.agent.domain.dto.response.ParseJobRefResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestions")
@Tag(name = "数据摄取", description = "上传、资产登记与解析任务接口")
public class IngestionController {
  private final IngestionService ingestionService;

  public IngestionController(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @PostMapping("/presign")
  @Operation(
      summary = "创建上传签名地址",
      description = "为前端直传生成预签名 URL",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "签名请求参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\",\"size\":102400}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "创建成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_301\",\"data\":{\"uploadUrl\":\"https://oss.example.com/...\",\"objectKey\":\"uploads/a1/report.pdf\",\"expireAt\":\"2026-02-24T11:00:00Z\"}}")))
  })
  public ApiResponse<PresignResponseData> createPresign(@RequestBody PresignRequest request) {
    PresignResponseData data = ingestionService.createPresign(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/proxy-upload")
  @Operation(
      summary = "代理上传文件",
      description = "通过后端代理上传二进制内容到对象存储",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "代理上传参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"objectKey\":\"uploads/a1/report.pdf\",\"contentType\":\"application/pdf\",\"base64Data\":\"JVBERi0xLjQ...\"}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "上传成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_302\",\"data\":{\"objectKey\":\"uploads/a1/report.pdf\",\"size\":102400}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "请求参数错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_REQUEST\",\"message\":\"objectKey is required\",\"requestId\":\"req_20260224_303\",\"data\":null}")))
  })
  public ApiResponse<ProxyUploadResponseData> proxyUpload(@RequestBody ProxyUploadRequest request) {
    ProxyUploadResponseData data = ingestionService.proxyUpload(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/assets")
  @Operation(
      summary = "完成资产登记",
      description = "上传成功后登记资产并关联记录",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "资产登记参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"diseaseProfileId\":\"d5a113ca-56cf-4aca-a265-8f4ec0a3292c\",\"reportDate\":\"2026-02-24\",\"objectKey\":\"uploads/a1/report.pdf\",\"checksum\":\"sha256:abcd\",\"title\":\"门诊检验\",\"sourceType\":\"LAB\",\"size\":102400}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "登记成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_304\",\"data\":{\"assetId\":\"32b01565-a0cb-47b0-858d-adf56dd1aa04\"}}")))
  })
  public ApiResponse<AssetCreatedResponseData> completeAsset(@RequestBody CompleteAssetRequest request) {
    AssetCreatedResponseData data = ingestionService.completeAsset(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/parse-jobs")
  @Operation(
      summary = "创建解析任务",
      description = "创建解析任务并投递到消息队列",
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "解析任务参数",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"recordId\":\"07abefef-a580-4b6a-b15f-fd54e8f282f4\",\"assetIds\":[\"32b01565-a0cb-47b0-858d-adf56dd1aa04\"]}"))))
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "创建成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"queued\",\"requestId\":\"req_20260224_305\",\"data\":{\"jobId\":\"2f4037a8-3973-4f05-a381-f9fef38eb189\",\"status\":\"QUEUED\"}}")))
  })
  public ApiResponse<ParseJobResponseData> createParseJob(
      @RequestBody CreateParseJobRequest request,
      @Parameter(description = "幂等键，防止重复创建任务", example = "idem_20260224_parse_001")
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    ParseJobResponseData data = ingestionService.createParseJob(request, idempotencyKey);
    return new ApiResponse<>("OK", "queued", RequestIdUtil.newRequestId(), data);
  }

  @GetMapping("/parse-jobs/{jobId}")
  @Operation(summary = "查询解析任务状态", description = "根据任务ID查询解析状态与进度")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "查询成功",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"OK\",\"message\":\"success\",\"requestId\":\"req_20260224_306\",\"data\":{\"jobId\":\"2f4037a8-3973-4f05-a381-f9fef38eb189\",\"status\":\"SUCCESS\",\"progress\":100,\"errorCode\":null,\"updatedAt\":\"2026-02-24T10:30:00\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "任务ID格式错误",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"INVALID_PARSE_JOB_ID\",\"message\":\"jobId is invalid\",\"requestId\":\"req_20260224_307\",\"data\":{\"jobId\":\"abc\"}}"))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "404",
          description = "任务不存在",
          content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
              "{\"code\":\"NOT_FOUND\",\"message\":\"parse job not found\",\"requestId\":\"req_20260224_308\",\"data\":{\"jobId\":\"2f4037a8-3973-4f05-a381-f9fef38eb199\"}}")))
  })
  public ResponseEntity<ApiResponse<?>> getParseJobStatus(
      @Parameter(description = "解析任务ID（UUID）", example = "2f4037a8-3973-4f05-a381-f9fef38eb189")
      @PathVariable("jobId") String jobId) {
    UUID jobUuid;
    try {
      jobUuid = UUID.fromString(jobId);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
          "INVALID_PARSE_JOB_ID",
          "jobId is invalid",
          RequestIdUtil.newRequestId(),
          new ParseJobRefResponseData(jobId)));
    }

    ParseJobStatusResponseData data;
    try {
      data = ingestionService.getParseJobStatus(jobUuid);
    } catch (IllegalArgumentException error) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
          "NOT_FOUND",
          "parse job not found",
          RequestIdUtil.newRequestId(),
          new ParseJobRefResponseData(jobId)));
    }

    return ResponseEntity.ok(new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data));
  }
}

