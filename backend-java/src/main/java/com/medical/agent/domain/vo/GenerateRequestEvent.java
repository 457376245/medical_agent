package com.medical.agent.domain.vo;

public record GenerateRequestEvent(
    String taskId,
    String tenantId,
    String recordId,
    String type,
    String traceId,
    String schemaVersion,
    RecordAnalysisContext analysisContext,
    String idempotencyKey) {}
