package com.medical.agent.api;

import com.medical.agent.application.service.IngestionService;
import java.util.Map;
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
  public Map<String, Object> createPresign(@RequestBody Map<String, Object> body) {
    Map<String, Object> data = ingestionService.createPresign(body);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @PostMapping("/proxy-upload")
  public Map<String, Object> proxyUpload(@RequestBody Map<String, Object> body) {
    Map<String, Object> data = ingestionService.proxyUpload(body);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @PostMapping("/assets")
  public Map<String, Object> completeAsset(@RequestBody Map<String, Object> body) {
    Map<String, Object> data = ingestionService.completeAsset(body);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @PostMapping("/parse-jobs")
  public Map<String, Object> createParseJob(
      @RequestBody Map<String, Object> body,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Map<String, Object> data = ingestionService.createParseJob(body, idempotencyKey);
    return Map.of(
        "code", "OK",
        "message", "queued",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }
}
