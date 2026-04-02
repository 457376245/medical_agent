package com.medical.agent.domain.dto.response;

public record AgentTrendSnapshotSummary(
    String recordId,
    String recordDate,
    String title,
    String summary) {}

