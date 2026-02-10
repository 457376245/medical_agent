package com.medical.agent.api;

import com.medical.agent.application.service.IngestionService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.request.CompleteAssetRequest;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.request.PresignRequest;
import com.medical.agent.domain.dto.request.ProxyUploadRequest;
import com.medical.agent.domain.dto.response.AssetCreatedResponseData;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.dto.response.PresignResponseData;
import com.medical.agent.domain.dto.response.ProxyUploadResponseData;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestions")
public class IngestionController {
  private final IngestionService ingestionService;

  public IngestionController(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @PostMapping("/presign")
  public ApiResponse<PresignResponseData> createPresign(@RequestBody PresignRequest request) {
    PresignResponseData data = ingestionService.createPresign(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/proxy-upload")
  public ApiResponse<ProxyUploadResponseData> proxyUpload(@RequestBody ProxyUploadRequest request) {
    ProxyUploadResponseData data = ingestionService.proxyUpload(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/assets")
  public ApiResponse<AssetCreatedResponseData> completeAsset(@RequestBody CompleteAssetRequest request) {
    AssetCreatedResponseData data = ingestionService.completeAsset(request);
    return new ApiResponse<>("OK", "success", RequestIdUtil.newRequestId(), data);
  }

  @PostMapping("/parse-jobs")
  public ApiResponse<ParseJobResponseData> createParseJob(
      @RequestBody CreateParseJobRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    ParseJobResponseData data = ingestionService.createParseJob(request, idempotencyKey);
    return new ApiResponse<>("OK", "queued", RequestIdUtil.newRequestId(), data);
  }
}
