package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.DiseaseProfileSummary;
import java.util.List;

public record DiseaseProfileListResponseData(List<DiseaseProfileSummary> profiles) {}
