package com.medical.agent.application.service;

import com.medical.agent.application.PersistenceService;
import com.medical.agent.domain.vo.RecordDetail;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.UpdateRecordSourceTypeResult;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecordService {
  private final PersistenceService persistenceService;

  public RecordService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public RecordDetail fetchRecord(UUID recordId) {
    return persistenceService.fetchRecord(recordId);
  }

  public RecordTrendData fetchTrend(UUID recordId, int limit) {
    return persistenceService.fetchRecordTrend(recordId, limit);
  }

  public boolean deleteRecord(UUID recordId) {
    return persistenceService.deleteRecord(recordId);
  }

  public UpdateRecordSourceTypeResult updateSourceType(UUID recordId, String sourceType) {
    return persistenceService.updateRecordSourceType(recordId, sourceType);
  }
}
