package com.medical.agent.application.service;

import com.medical.agent.application.PersistenceService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecordService {
  private final PersistenceService persistenceService;

  public RecordService(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public Map<String, Object> fetchRecord(UUID recordId) {
    return persistenceService.fetchRecord(recordId);
  }

  public Map<String, Object> fetchTrend(UUID recordId, int limit) {
    return persistenceService.fetchRecordTrend(recordId, limit);
  }

  public boolean deleteRecord(UUID recordId) {
    return persistenceService.deleteRecord(recordId);
  }

  public Map<String, Object> updateSourceType(UUID recordId, String sourceType) {
    return persistenceService.updateRecordSourceType(recordId, sourceType);
  }
}
