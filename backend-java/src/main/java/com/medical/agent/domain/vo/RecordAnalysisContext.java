package com.medical.agent.domain.vo;

public record RecordAnalysisContext(
    String recordId,
    String title,
    String recordDate,
    String sourceType,
    String diseaseName,
    String parseStatus,
    StructuredResultData structuredResult) {}
