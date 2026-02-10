package com.medical.agent.application;

import com.medical.agent.domain.vo.TimelineBatchSummary;
import com.medical.agent.domain.vo.TimelineRecordSummary;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {
  private final PersistenceService persistenceService;

  public TimelineService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public List<TimelineBatchSummary> listBatches() {
    return persistenceService.listTimelineBatches();
  }

  public List<TimelineRecordSummary> listBatchRecords(String batchId) {
    return persistenceService.listRecordsByBatch(batchId);
  }

  public String diseaseNameByBatch(String batchId) {
    return persistenceService.getDiseaseNameByBatch(batchId);
  }
}
