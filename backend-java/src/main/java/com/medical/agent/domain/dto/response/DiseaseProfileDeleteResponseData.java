package com.medical.agent.domain.dto.response;

public record DiseaseProfileDeleteResponseData(
    String diseaseProfileId,
    boolean deleted,
    String reason,
    Integer linkedRecordCount,
    Integer deletedRecordCount,
    Integer deletedAssetCount) {}
