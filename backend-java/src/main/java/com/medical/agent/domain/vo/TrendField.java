package com.medical.agent.domain.vo;

public record TrendField(
    String name,
    String value,
    String unit,
    String referenceRange,
    Double numericValue,
    String comparisonType,
    String resultState,
    Double referenceLowerBound,
    Double referenceUpperBound,
    Boolean referenceLowerInclusive,
    Boolean referenceUpperInclusive) {}
