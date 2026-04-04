package com.medical.agent.domain.dto.response;

public record LoginResponseData(
    String token,
    String type,
    String userId,
    String displayName,
    String defaultPatientId) {
}
