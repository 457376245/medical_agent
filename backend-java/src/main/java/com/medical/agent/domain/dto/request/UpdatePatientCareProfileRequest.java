package com.medical.agent.domain.dto.request;

import java.util.List;

public record UpdatePatientCareProfileRequest(
    List<String> diagnosedConditions,
    List<MedicationItemInput> currentMedications,
    List<String> allergies,
    List<String> abnormalBaseline,
    String doctorInstructions,
    List<String> careGoals,
    List<String> redFlagNotes,
    List<String> personalContext) {

  public record MedicationItemInput(
      String name,
      String dosage,
      String frequency,
      String purpose) {}
}
