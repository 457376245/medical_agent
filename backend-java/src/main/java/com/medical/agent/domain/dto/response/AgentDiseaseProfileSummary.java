package com.medical.agent.domain.dto.response;

public record AgentDiseaseProfileSummary(
    String id,
    String name,
    int recordCount,
    String latestRecordAt) {}

