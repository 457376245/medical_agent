package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.domain.vo.DiseaseProfileExamNode;
import java.util.List;

public record DiseaseProfileDetailResponseData(
    String profileId,
    String diseaseName,
    List<DiseaseProfileRecordSummary> records,
    List<DiseaseProfileExamNode> examNodes,
    int parsingCount) {}
