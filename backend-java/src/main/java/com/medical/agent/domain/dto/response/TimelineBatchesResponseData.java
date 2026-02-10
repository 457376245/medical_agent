package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.TimelineBatchSummary;
import java.util.List;

public record TimelineBatchesResponseData(List<TimelineBatchSummary> batches) {}
