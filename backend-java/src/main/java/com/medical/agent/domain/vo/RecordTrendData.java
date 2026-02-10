package com.medical.agent.domain.vo;

import java.util.List;

public record RecordTrendData(
    String recordId,
    String sourceType,
    String diseaseProfileId,
    int limit,
    List<TrendSnapshot> snapshots) {}
