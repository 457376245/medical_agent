package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.DiseaseProfileOverview;
import java.util.List;

public record DiseaseProfileOverviewResponseData(List<DiseaseProfileOverview> profiles) {}
