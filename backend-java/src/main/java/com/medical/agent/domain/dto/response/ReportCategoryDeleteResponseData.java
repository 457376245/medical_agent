package com.medical.agent.domain.dto.response;

public record ReportCategoryDeleteResponseData(
    String reportCategoryId,
    boolean deleted,
    String reason,
    Integer linkedRecordCount) {}
