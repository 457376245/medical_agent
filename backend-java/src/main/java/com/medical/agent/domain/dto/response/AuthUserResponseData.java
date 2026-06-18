package com.medical.agent.domain.dto.response;

public record AuthUserResponseData(
    String userId,
    String displayName,
    String defaultPatientId) {
}
