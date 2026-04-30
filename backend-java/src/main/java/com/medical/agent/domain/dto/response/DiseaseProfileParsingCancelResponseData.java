package com.medical.agent.domain.dto.response;

public record DiseaseProfileParsingCancelResponseData(
    String diseaseProfileId,
    int deletedRecordCount,
    int deletedAssetCount,
    int deletedParseJobCount) {}
