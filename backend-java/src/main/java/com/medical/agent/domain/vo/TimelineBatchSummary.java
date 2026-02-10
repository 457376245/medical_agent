package com.medical.agent.domain.vo;

public record TimelineBatchSummary(
    String batchId,
    String diseaseName,
    int recordCount,
    String latestRecordAt,
    String latestRecordId,
    String latestRecordTitle,
    String latestParseStatus) {}
