package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientCareFollowUpTaskListResponseData(List<TaskSummary> tasks) {
  public record TaskSummary(
      String id,
      String title,
      String dueDate,
      String priority,
      String status,
      String notes,
      String diseaseProfileId,
      String recordId,
      String createdAt) {}
}
