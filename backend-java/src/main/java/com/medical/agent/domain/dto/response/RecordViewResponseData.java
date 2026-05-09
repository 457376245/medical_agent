package com.medical.agent.domain.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.UltrasoundFollowUpResult;
import java.util.List;

public record RecordViewResponseData(
    String recordId,
    String summary,
    String parseStatus,
    StructuredResultView structuredResult,
    List<RecordDetail.CombinationAnalysisItem> combinationAnalysis,
    UltrasoundFollowUpResult ultrasoundFollowUp,
    String defaultView) {

  public record StructuredResultView(String schemaVersion, int revision, JsonNode payload) {}
}
