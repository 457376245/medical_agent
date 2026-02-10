package com.medical.agent.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record GeneratedOutputSnapshot(String recordId, String type, int version, String content, JsonNode modelMeta) {}
