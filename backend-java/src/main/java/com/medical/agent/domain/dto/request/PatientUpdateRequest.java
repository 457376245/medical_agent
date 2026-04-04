package com.medical.agent.domain.dto.request;

public record PatientUpdateRequest(
    String name,
    String relationship,
    String gender,
    String birthDate,
    String notes) {
}
