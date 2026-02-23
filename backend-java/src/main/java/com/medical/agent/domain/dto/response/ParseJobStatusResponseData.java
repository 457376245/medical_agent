package com.medical.agent.domain.dto.response;

public record ParseJobStatusResponseData(
    String jobId,
    String status,
    Integer progress,
    String errorCode,
    String updatedAt) {}
