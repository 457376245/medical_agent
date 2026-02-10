package com.medical.agent.domain.vo;

public record UpdateRecordSourceTypeResult(
    boolean updated,
    String sourceType,
    String title,
    String recordDate,
    String diseaseName) {}
