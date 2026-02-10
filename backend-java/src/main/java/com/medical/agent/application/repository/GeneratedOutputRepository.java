package com.medical.agent.application.repository;

import com.medical.agent.domain.vo.GeneratedOutputSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedOutputRepository {
  int createGeneratedOutput(UUID recordId, String type, String content);

  int createGeneratedOutputWithMeta(UUID recordId, String type, String content, String modelMetaJson);

  Optional<GeneratedOutputSnapshot> fetchLatestGeneratedOutput(UUID recordId, String type);
}
