package com.medical.agent.api;

import com.medical.agent.application.PersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disease-profiles")
public class DiseaseProfileController {
  private final PersistenceService persistenceService;

  public DiseaseProfileController(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @GetMapping
  public Map<String, Object> list() {
    List<Map<String, Object>> profiles = persistenceService.listDiseaseProfiles();
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("profiles", profiles));
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    UUID profileId = persistenceService.createDiseaseProfile(name);
    return Map.of(
        "code", "OK",
        "message", "success",
        "requestId", RequestIdUtil.newRequestId(),
        "data", Map.of("diseaseProfileId", profileId.toString(), "name", name));
  }
}
