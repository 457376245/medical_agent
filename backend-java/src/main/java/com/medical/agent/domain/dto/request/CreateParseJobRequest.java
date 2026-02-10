package com.medical.agent.domain.dto.request;

import java.util.List;

public record CreateParseJobRequest(String recordId, List<String> assetIds) {}
