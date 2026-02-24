package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.TimelineRecordSummary;
import java.util.List;

public record TimelineProfileDetailResponseData(String profileId, String diseaseName, List<TimelineRecordSummary> records) {}
