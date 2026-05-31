package com.medical.agent.domain.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record SubmitPatientMemoryEntriesRequest(
    String conversationThreadId,
    String turnId,
    String diseaseProfileId,
    String recordId,
    List<EntryInput> entries) {

  public record EntryInput(
      String memoryType,
      String fieldPath,
      String valueText,
      JsonNode value,
      String evidenceText,
      Double confidence,
      String riskLevel,
      String sourceRef) {}
}
