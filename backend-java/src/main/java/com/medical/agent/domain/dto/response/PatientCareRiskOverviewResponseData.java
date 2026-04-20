package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientCareRiskOverviewResponseData(
    String riskLevel,
    String summary,
    List<RiskSignal> signals,
    List<EvidenceItem> evidenceRefs) {

  public record RiskSignal(
      String severity,
      String title,
      String detail,
      String recommendedAction) {}

  public record EvidenceItem(
      String type,
      String title,
      String detail,
      String source,
      String confidence,
      String nature) {}
}
