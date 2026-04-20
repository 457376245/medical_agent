package com.medical.agent.domain.dto.request;

public record UpdateFollowUpTaskRequest(
    String title,
    String dueDate,
    String priority,
    String status,
    String notes) {}
