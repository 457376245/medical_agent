package com.medical.agent.domain.vo;

public record TimelineProfileSummary(
    String profileId,
    String diseaseName,
    int recordCount,
    String latestRecordAt,
    String latestRecordId,
    String latestRecordTitle,
    String latestParseStatus) {}
