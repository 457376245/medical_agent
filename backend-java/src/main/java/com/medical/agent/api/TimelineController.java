package com.medical.agent.api;

import com.medical.agent.application.TimelineService;
import com.medical.agent.domain.dto.ApiResponse;
import com.medical.agent.domain.dto.response.TimelineBatchResponseData;
import com.medical.agent.domain.dto.response.TimelineBatchesResponseData;
import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {
  private final TimelineService timelineService;

  public TimelineController(TimelineService timelineService) {
    this.timelineService = timelineService;
  }

  @GetMapping
  public ApiResponse<TimelineBatchesResponseData> timeline() {
    List<TimelineBatchSummary> batches = timelineService.listBatches();
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new TimelineBatchesResponseData(batches));
  }

  @GetMapping("/{batchId}")
  public ApiResponse<TimelineBatchResponseData> batch(@PathVariable("batchId") String batchId) {
    List<TimelineRecordSummary> records = timelineService.listBatchRecords(batchId);
    String diseaseName = timelineService.diseaseNameByBatch(batchId);
    return new ApiResponse<>(
        "OK",
        "success",
        RequestIdUtil.newRequestId(),
        new TimelineBatchResponseData(batchId, diseaseName, records));
  }
}
