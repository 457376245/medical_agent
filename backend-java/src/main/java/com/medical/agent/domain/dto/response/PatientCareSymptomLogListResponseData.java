package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientCareSymptomLogListResponseData(List<SymptomLogItem> logs) {
  public record SymptomLogItem(
      String id,
      String label,
      String value,
      String unit,
      String alertLevel,
      String notes,
      String recordedAt,
      String diseaseProfileId) {}
}
