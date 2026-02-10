package com.medical.agent.domain.dto.response;

public record RecordSourceTypeUpdateResponseData(
    String recordId,
    boolean updated,
    String sourceType,
    String title,
    String recordDate,
    String diseaseName) {}
