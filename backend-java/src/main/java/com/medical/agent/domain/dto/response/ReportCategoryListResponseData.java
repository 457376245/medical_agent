package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.ReportCategorySummary;
import java.util.List;

public record ReportCategoryListResponseData(List<ReportCategorySummary> categories) {}
