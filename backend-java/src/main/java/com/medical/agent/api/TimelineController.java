package com.medical.agent.api;

import com.medical.agent.application.TimelineService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {
  private final TimelineService timelineService;

  public TimelineController(TimelineService timelineService) {
    this.timelineService = timelineService;
  }

  @GetMapping
  public Map<String, Object> timeline() {
    List<Map<String, Object>> batches = timelineService.listBatches();
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("batches", batches));
  }

  @GetMapping("/{batchId}")
  public Map<String, Object> batch(@PathVariable("batchId") String batchId) {
    List<Map<String, Object>> records = timelineService.listBatchRecords(batchId);
    String diseaseName = timelineService.diseaseNameByBatch(batchId);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("batchId", batchId, "diseaseName", diseaseName, "records", records));
  }
}
