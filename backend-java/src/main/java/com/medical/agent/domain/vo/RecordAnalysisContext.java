package com.medical.agent.domain.vo;

import java.util.List;

public record RecordAnalysisContext(
    String recordId,
    String title,
    String recordDate,
    String sourceType,
    String diseaseName,
    String parseStatus,
    StructuredResultData structuredResult,
    List<RecordDetail.CombinationAnalysisItem> combinationAnalysis,
    UltrasoundFollowUpResult ultrasoundFollowUp) {}
