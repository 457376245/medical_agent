package com.medical.agent.domain.vo;

import java.util.List;

public record UltrasoundFollowUpResult(
    String mode,
    String changeStatus,
    String summary,
    String actionLevel,
    String actionSuggestion,
    List<EvidenceItem> currentEvidence,
    List<EvidenceItem> previousEvidence,
    List<HistoryItem> history,
    String patientSummary,
    String clinicalSummary,
    String confidenceLevel,
    List<FindingRow> findingRows,
    List<RiskModule> riskModules,
    List<MissingInput> missingInputs,
    List<String> nextQuestionsForDoctor) {

  public record EvidenceItem(
      String recordId,
      String recordDate,
      String label,
      String text) {}

  public record HistoryItem(
      String recordId,
      String recordDate,
      String title,
      String summary) {}

  public record FindingRow(
      String module,
      String currentValue,
      String previousValue,
      String currentStatus,
      String previousStatus,
      String trendStatus,
      String evidenceLevel,
      String explanation,
      List<EvidenceItem> evidenceRefs) {}

  public record RiskModule(
      String name,
      String level,
      String summary,
      List<String> evidence,
      List<String> missingInputs) {}

  public record MissingInput(
      String name,
      String reason,
      String category) {}
}
