package com.medical.agent.domain.dto.response;

import java.util.List;

public record PatientCareEvidenceResponseData(
    List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs) {}
