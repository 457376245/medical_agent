package com.medical.agent.application.service;

import com.medical.agent.application.OssPresignService;
import com.medical.agent.application.PersistenceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
  @Value("${app.upload.base-url:http://localhost:8080/mock-upload}")
  private String uploadBaseUrl;

  private final OssPresignService ossPresignService;
  private final PersistenceService persistenceService;
  private final ParseJobService parseJobService;

  public IngestionService(
      OssPresignService ossPresignService,
      PersistenceService persistenceService,
      ParseJobService parseJobService) {
    this.ossPresignService = ossPresignService;
    this.persistenceService = persistenceService;
    this.parseJobService = parseJobService;
  }

  public Map<String, Object> createPresign(Map<String, Object> body) {
    String fileName = String.valueOf(body.getOrDefault("fileName", "upload.bin"));
    String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"));
    String objectKey = "uploads/" + UUID.randomUUID() + "/" + fileName;
    OssPresignService.PresignResult signed = ossPresignService.presignPut(objectKey, contentType)
        .orElseGet(() -> new OssPresignService.PresignResult(
            uploadBaseUrl + "/" + objectKey,
            Instant.now().plusSeconds(900)));
    return Map.of(
        "uploadUrl", signed.uploadUrl(),
        "objectKey", objectKey,
        "expireAt", signed.expireAt().toString());
  }

  public Map<String, Object> proxyUpload(Map<String, Object> body) {
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
    return Map.of("objectKey", objectKey, "size", binary.length);
  }

  public Map<String, Object> completeAsset(Map<String, Object> body) {
    UUID recordId = body.get("recordId") == null ? null : UUID.fromString(String.valueOf(body.get("recordId")));
    UUID diseaseProfileId = body.get("diseaseProfileId") == null
        ? null
        : UUID.fromString(String.valueOf(body.get("diseaseProfileId")));
    LocalDate reportDate = body.get("reportDate") == null
        ? null
        : LocalDate.parse(String.valueOf(body.get("reportDate")));
    String objectKey = String.valueOf(body.getOrDefault("objectKey", ""));
    String checksum = String.valueOf(body.getOrDefault("checksum", ""));
    String title = String.valueOf(body.getOrDefault("title", "Imported record"));
    String sourceType = String.valueOf(body.getOrDefault("sourceType", ""));
    String fileType = objectKey.endsWith(".pdf") ? "PDF" : "IMAGE";
    long fileSize = Long.parseLong(String.valueOf(body.getOrDefault("size", 1)));
    UUID assetId = persistenceService.createAsset(
        objectKey,
        checksum,
        recordId,
        fileType,
        fileSize,
        diseaseProfileId,
        reportDate,
        title,
        sourceType);
    return Map.of("assetId", assetId.toString());
  }

  public Map<String, Object> createParseJob(Map<String, Object> body, String idempotencyKey) {
    return parseJobService.create(body, idempotencyKey);
  }
}
