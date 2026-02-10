package com.medical.agent.api;

import com.medical.agent.application.service.DataRightsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records/{recordId}/delete-requests")
public class DeleteController {
  private final DataRightsService dataRightsService;

  public DeleteController(DataRightsService dataRightsService) {
    this.dataRightsService = dataRightsService;
  }

  @PostMapping
  public Map<String, Object> create(@PathVariable("recordId") String recordId) {
    Map<String, Object> data = dataRightsService.createRequest(recordId, "DELETE");
    return Map.of("code", "OK", "message", "accepted", "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }

  @GetMapping("/{requestId}")
  public Map<String, Object> status(@PathVariable("recordId") String recordId, @PathVariable("requestId") String requestId) {
    Map<String, Object> data = dataRightsService.getStatus(requestId);
    return Map.of("code", "OK", "message", "success", "requestId", RequestIdUtil.newRequestId(),
        "data", data);
  }
}
