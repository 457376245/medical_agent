package com.medical.agent.domain.vo;

import java.util.List;

public record ParseRequestEvent(
    String jobId,
    String tenantId,
    String userId,
    List<AssetRef> assetRefs,
    String traceId,
    String schemaVersion,
    String idempotencyKey,
    String recordId,
    String sourceType,
    List<String> existingCategories) {}
