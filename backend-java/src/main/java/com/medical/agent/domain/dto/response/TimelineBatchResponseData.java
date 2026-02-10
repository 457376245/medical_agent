package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.TimelineRecordSummary;
import java.util.List;

public record TimelineBatchResponseData(String batchId, String diseaseName, List<TimelineRecordSummary> records) {}
