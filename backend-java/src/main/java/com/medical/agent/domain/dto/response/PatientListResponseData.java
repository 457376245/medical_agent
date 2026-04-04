package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientListResponseData(List<PatientSummary> patients) {
  public record PatientSummary(
      String id,
      String name,
      String relationship,
      String gender,
      String birthDate,
      String notes,
      boolean isDefault) {
  }
}
