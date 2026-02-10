package com.medical.agent.application.service;

import com.medical.agent.application.OssPresignService;
import com.medical.agent.domain.dto.request.CompleteAssetRequest;
import com.medical.agent.domain.dto.request.CreateParseJobRequest;
import com.medical.agent.domain.dto.request.PresignRequest;
import com.medical.agent.domain.dto.request.ProxyUploadRequest;
import com.medical.agent.domain.dto.response.AssetCreatedResponseData;
import com.medical.agent.domain.dto.response.ParseJobResponseData;
import com.medical.agent.domain.dto.response.PresignResponseData;
import com.medical.agent.domain.dto.response.ProxyUploadResponseData;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
  @Value("${app.upload.base-url:http://localhost:8080/mock-upload}")
  private String uploadBaseUrl;

  private final OssPresignService ossPresignService;
  private final RecordService recordService;
  private final ParseJobService parseJobService;

  public IngestionService(
      OssPresignService ossPresignService,
      RecordService recordService,
      ParseJobService parseJobService) {
    this.ossPresignService = ossPresignService;
    this.recordService = recordService;
    this.parseJobService = parseJobService;
  }

  public PresignResponseData createPresign(PresignRequest request) {
    String fileName = request == null || request.fileName() == null || request.fileName().isBlank()
        ? "upload.bin"
        : request.fileName();
    String contentType = request == null || request.contentType() == null || request.contentType().isBlank()
        ? "application/octet-stream"
        : request.contentType();
    String objectKey = "uploads/" + UUID.randomUUID() + "/" + fileName;
    OssPresignService.PresignResult signed = ossPresignService.presignPut(objectKey, contentType)
        .orElseGet(() -> new OssPresignService.PresignResult(
            uploadBaseUrl + "/" + objectKey,
            Instant.now().plusSeconds(900)));
    return new PresignResponseData(signed.uploadUrl(), objectKey, signed.expireAt().toString());
  }

  public ProxyUploadResponseData proxyUpload(ProxyUploadRequest request) {
    String objectKey = request == null || request.objectKey() == null ? "" : request.objectKey().trim();
    String contentType = request == null || request.contentType() == null || request.contentType().isBlank()
        ? "application/octet-stream"
        : request.contentType();
    String base64Data = request == null || request.base64Data() == null ? "" : request.base64Data();
    if (objectKey.isEmpty()) {
      throw new IllegalArgumentException("objectKey is required");
    }
    if (base64Data.isEmpty()) {
      throw new IllegalArgumentException("base64Data is required");
    }

    byte[] binary = Base64.getDecoder().decode(base64Data);
    ossPresignService.putObject(objectKey, contentType, binary);
    return new ProxyUploadResponseData(objectKey, binary.length);
  }

  public AssetCreatedResponseData completeAsset(CompleteAssetRequest request) {
    UUID recordId = request == null || request.recordId() == null ? null : UUID.fromString(request.recordId());
    UUID diseaseProfileId = request == null || request.diseaseProfileId() == null
        ? null
        : UUID.fromString(request.diseaseProfileId());
    LocalDate reportDate = request == null || request.reportDate() == null
        ? null
        : LocalDate.parse(request.reportDate());
    String objectKey = request == null || request.objectKey() == null ? "" : request.objectKey();
    String checksum = request == null || request.checksum() == null ? "" : request.checksum();
    String title = request == null || request.title() == null || request.title().isBlank() ? "Imported record" : request.title();
    String sourceType = request == null || request.sourceType() == null ? "" : request.sourceType();
    String fileType = objectKey.endsWith(".pdf") ? "PDF" : "IMAGE";
    long fileSize = request == null || request.size() == null ? 1L : request.size();
    UUID assetId = recordService.createAsset(
        objectKey,
        checksum,
        recordId,
        fileType,
        fileSize,
        diseaseProfileId,
        reportDate,
        title,
        sourceType);
    return new AssetCreatedResponseData(assetId.toString());
  }

  public ParseJobResponseData createParseJob(CreateParseJobRequest request, String idempotencyKey) {
    return parseJobService.create(request, idempotencyKey);
  }
}
