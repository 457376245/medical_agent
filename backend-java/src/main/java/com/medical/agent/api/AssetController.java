package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
  private final PersistenceService persistenceService;

  public AssetController(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @PostMapping("/complete")
  public Map<String, Object> complete(@RequestBody Map<String, Object> body) {
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
    String sourceType = String.valueOf(body.getOrDefault("sourceType", "UPLOAD"));
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
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("assetId", assetId.toString()));
  }
}
