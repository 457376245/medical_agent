package com.medical.agent.domain.dto.response;

import java.util.List;

public record AgentDiseaseProfileContextResponse(
    AgentDiseaseProfileSummary profile,
    AgentRecordContextSummary selectedRecord,
    List<AgentRecordContextSummary> recentRecords,
    AgentRecordContextData recordSummary,
    List<AgentTrendSnapshotSummary> trendSummary,
    String contextStatus,
    List<String> warnings) {}

