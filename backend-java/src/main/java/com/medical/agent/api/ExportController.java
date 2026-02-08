package com.medical.agent.api;

import com.medical.agent.application.DataRightsRequestService;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records/{recordId}/export-requests")
public class ExportController {
  private final DataRightsRequestService dataRightsRequestService;

  public ExportController(DataRightsRequestService dataRightsRequestService) {
    this.dataRightsRequestService = dataRightsRequestService;
  }

  @PostMapping
  public Map<String, Object> create(@PathVariable("recordId") String recordId) {
    Map<String, Object> data = dataRightsRequestService.createRequest(recordId, "EXPORT");
    return Map.of("code", "OK", "message", "accepted", "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @GetMapping("/{requestId}")
  public Map<String, Object> status(@PathVariable("recordId") String recordId, @PathVariable("requestId") String requestId) {
    Map<String, Object> data = dataRightsRequestService.getStatus(requestId);
    return Map.of("code", "OK", "message", "success", "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @GetMapping("/{requestId}/download")
  public Map<String, Object> download(@PathVariable("recordId") String recordId, @PathVariable("requestId") String requestId) {
    Map<String, Object> download = dataRightsRequestService.exportDownload(requestId);
    return Map.of("code", "OK", "message", "success", "requestId", RequestIdUtil.newRequestId(),
        "data", download);
  }
}
