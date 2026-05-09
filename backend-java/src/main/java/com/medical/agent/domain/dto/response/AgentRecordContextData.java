package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.UltrasoundFollowUpResult;
import java.util.List;

public record AgentRecordContextData(
    String summary,
    String analysis,
    List<AgentKeyFieldSummary> keyFields,
    UltrasoundFollowUpResult ultrasoundFollowUp) {}
