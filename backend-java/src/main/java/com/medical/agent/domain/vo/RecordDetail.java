package com.medical.agent.domain.vo;

import java.util.List;

public record RecordDetail(
    String recordId,
    String summary,
    String parseStatus,
    StructuredResultData structuredResult,
    List<CombinationAnalysisItem> combinationAnalysis,
    UltrasoundFollowUpResult ultrasoundFollowUp) {

  public record CombinationAnalysisItem(
      String ruleId,
      String name,
      String severity,
      String summary,
      String detail,
      String suggestion,
      List<String> involvedIndicators) {}
}
