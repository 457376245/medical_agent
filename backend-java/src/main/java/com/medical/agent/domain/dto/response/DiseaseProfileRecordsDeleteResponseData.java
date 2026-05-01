package com.medical.agent.domain.dto.response;

import java.util.List;

public record DiseaseProfileRecordsDeleteResponseData(
    String diseaseProfileId,
    boolean deleted,
    int requestedRecordCount,
    int deletedRecordCount,
    int deletedAssetCount,
    int deletedParseJobCount,
    List<String> rejectedRecordIds) {}
