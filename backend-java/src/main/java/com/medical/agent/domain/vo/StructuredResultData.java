package com.medical.agent.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record StructuredResultData(String schemaVersion, int revision, JsonNode payload) {}
