package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientCareProfileResponseData(
    BaselineSummary patientBaseline,
    List<MedicationItem> currentMedications,
    List<String> careGoals,
    List<String> redFlagNotes,
    String updatedAt) {

  public record BaselineSummary(
      List<String> diagnosedConditions,
      List<String> allergies,
      List<String> abnormalBaseline,
      String doctorInstructions,
      List<RecentSymptomItem> recentSymptoms) {}

  public record MedicationItem(
      String name,
      String dosage,
      String frequency,
      String purpose) {}

  public record RecentSymptomItem(
      String id,
      String label,
      String value,
      String unit,
      String alertLevel,
      String notes,
      String recordedAt) {}
}
