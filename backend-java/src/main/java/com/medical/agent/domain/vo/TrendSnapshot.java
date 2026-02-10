package com.medical.agent.domain.vo;

import java.util.List;

public record TrendSnapshot(
    String recordId,
    String recordDate,
    String title,
    String sourceType,
    List<TrendField> fields) {}
