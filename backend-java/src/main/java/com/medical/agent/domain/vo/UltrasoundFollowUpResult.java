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
    List<HistoryItem> history) {

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
}
