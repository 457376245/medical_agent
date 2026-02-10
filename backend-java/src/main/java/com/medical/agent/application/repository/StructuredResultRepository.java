package com.medical.agent.application.repository;

import com.medical.agent.domain.vo.RecordAnalysisContext;
import java.util.Optional;
import java.util.UUID;

public interface StructuredResultRepository {
  Optional<RecordAnalysisContext> fetchRecordAnalysisContext(UUID recordId);
}
