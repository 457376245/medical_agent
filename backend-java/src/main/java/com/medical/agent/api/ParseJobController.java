package com.medical.agent.api;

import com.medical.agent.application.service.ParseJobService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parse-jobs")
public class ParseJobController {
  private final ParseJobService parseJobService;

  public ParseJobController(ParseJobService parseJobService) {
    this.parseJobService = parseJobService;
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> body,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Map<String, Object> data = parseJobService.create(body, idempotencyKey);

    return Map.of(
        "code", "OK",
        "message", "queued",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @GetMapping("/{jobId}")
  public Map<String, Object> status(@PathVariable("jobId") String jobId) {
    Map<String, Object> data = parseJobService.status(UUID.fromString(jobId));
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }
}
