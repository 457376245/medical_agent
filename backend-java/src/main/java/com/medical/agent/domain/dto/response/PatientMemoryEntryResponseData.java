package com.medical.agent.domain.dto.response;

public record PatientMemoryEntryResponseData(
    String id,
    String memoryType,
    String fieldPath,
    String valueText,
    String valueJson,
    String evidenceText,
    String sourceType,
    String sourceRef,
    Double confidence,
    String riskLevel,
    String status,
    String diseaseProfileId,
    String recordId,
    String conversationThreadId,
    String turnId,
    String rejectionReason,
    String supersedesMemoryId,
    String validFrom,
    String validTo,
    Boolean isCurrent,
    String confirmedAt,
    String createdAt,
    String updatedAt) {
}
