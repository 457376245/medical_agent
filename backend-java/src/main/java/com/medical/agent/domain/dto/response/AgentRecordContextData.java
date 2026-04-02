package com.medical.agent.domain.dto.response;

import java.util.List;

public record AgentRecordContextData(
    String summary,
    String analysis,
    List<AgentKeyFieldSummary> keyFields) {}

