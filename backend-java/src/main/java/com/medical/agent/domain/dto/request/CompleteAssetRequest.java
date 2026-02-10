package com.medical.agent.domain.dto.request;

public record CompleteAssetRequest(
    String recordId,
    String diseaseProfileId,
    String reportDate,
    String objectKey,
    String checksum,
    String title,
    String sourceType,
    Long size) {}
