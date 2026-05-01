package com.medical.agent.domain.dto.request;

import java.util.List;

public record DeleteDiseaseProfileRecordsRequest(List<String> recordIds) {}
