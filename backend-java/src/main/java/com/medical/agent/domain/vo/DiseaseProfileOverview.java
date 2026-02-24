package com.medical.agent.domain.vo;

public record DiseaseProfileOverview(
    String profileId,
    String diseaseName,
    int recordCount,
    String latestRecordAt,
    String latestRecordId,
    String latestRecordTitle,
    String latestParseStatus) {}
