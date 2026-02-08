package com.medical.agent.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {
  private final PersistenceService persistenceService;

  public TimelineService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public List<Map<String, Object>> listBatches() {
    return persistenceService.listTimelineBatches();
  }

  public List<Map<String, Object>> listBatchRecords(String batchId) {
    return persistenceService.listRecordsByBatch(batchId);
  }
}
