package com.medical.agent.domain.vo;

import java.util.List;

public record DiseaseProfileExamNode(
    String examNodeId,
    String anchorDate,
    String dateRangeStart,
    String dateRangeEnd,
    String displayDate,
    List<DiseaseProfileRecordSummary> records) {}
