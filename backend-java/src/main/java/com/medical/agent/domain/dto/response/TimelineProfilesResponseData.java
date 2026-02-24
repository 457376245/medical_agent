package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.TimelineProfileSummary;
import java.util.List;

public record TimelineProfilesResponseData(List<TimelineProfileSummary> profiles) {}
