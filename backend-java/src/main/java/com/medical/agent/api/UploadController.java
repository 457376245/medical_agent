package com.medical.agent.api;

import com.medical.agent.application.OssPresignService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
  @Value("${app.upload.base-url:http://localhost:8080/mock-upload}")
  private String uploadBaseUrl;

  private final OssPresignService ossPresignService;

  public UploadController(OssPresignService ossPresignService) {
    this.ossPresignService = ossPresignService;
  }

  @PostMapping("/presign")
  public Map<String, Object> presign(@RequestBody Map<String, Object> body) {
    String fileName = String.valueOf(body.getOrDefault("fileName", "upload.bin"));
    String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"));
    String objectKey = "uploads/" + UUID.randomUUID() + "/" + fileName;
    OssPresignService.PresignResult signed = ossPresignService.presignPut(objectKey, contentType)
        .orElseGet(() -> new OssPresignService.PresignResult(
            uploadBaseUrl + "/" + objectKey,
            Instant.now().plusSeconds(900)));
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of(
            "uploadUrl", signed.uploadUrl(),
            "objectKey", objectKey,
            "expireAt", signed.expireAt().toString()));
  }

  @PostMapping("/proxy-put")
  public Map<String, Object> proxyPut(@RequestBody Map<String, Object> body) {
    String objectKey = String.valueOf(body.getOrDefault("objectKey", "")).trim();
    String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"));
    String base64Data = String.valueOf(body.getOrDefault("base64Data", ""));
    if (objectKey.isEmpty()) {
      throw new IllegalArgumentException("objectKey is required");
    }
    if (base64Data.isEmpty()) {
      throw new IllegalArgumentException("base64Data is required");
    }

    byte[] binary = Base64.getDecoder().decode(base64Data);
    ossPresignService.putObject(objectKey, contentType, binary);

    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("objectKey", objectKey, "size", binary.length));
  }
}
