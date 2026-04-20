package com.medical.agent.domain.dto.request;

public record CreateFollowUpTaskRequest(
    String title,
    String dueDate,
    String priority,
    String notes,
    String diseaseProfileId,
    String recordId) {}
