package com.medical.agent.domain.dto.request;

public record CreateSymptomLogRequest(
    String label,
    String value,
    String unit,
    String alertLevel,
    String notes,
    String recordedAt,
    String diseaseProfileId) {}
