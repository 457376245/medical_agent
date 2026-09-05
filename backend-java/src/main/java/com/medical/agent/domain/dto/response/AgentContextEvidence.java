package com.medical.agent.domain.dto.response;

public record AgentContextEvidence(
    String evidenceId,
    String category,
    String summary,
    String sourceType,
    String sourceRef,
    String observedAt,
    String updatedAt,
    String verificationStatus) {}
