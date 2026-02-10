package com.medical.agent.domain.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

public record RecordViewResponseData(
    String recordId,
    String summary,
    String parseStatus,
    StructuredResultView structuredResult,
    String defaultView) {

  public record StructuredResultView(String schemaVersion, int revision, JsonNode payload) {}
}
