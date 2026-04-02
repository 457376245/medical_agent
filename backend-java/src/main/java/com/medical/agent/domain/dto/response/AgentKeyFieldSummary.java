package com.medical.agent.domain.dto.response;

public record AgentKeyFieldSummary(
    String name,
    String value,
    String unit,
    String referenceRange) {}

