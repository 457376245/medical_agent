package com.medical.agent.domain.dto.response;

public record AgentRecordContextSummary(
    String id,
    String title,
    String recordDate,
    String sourceType,
    String parseStatus) {}

