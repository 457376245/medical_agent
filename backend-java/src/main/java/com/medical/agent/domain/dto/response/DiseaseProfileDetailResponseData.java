package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import java.util.List;

public record DiseaseProfileDetailResponseData(
    String profileId,
    String diseaseName,
    List<DiseaseProfileRecordSummary> records,
    int parsingCount) {}
