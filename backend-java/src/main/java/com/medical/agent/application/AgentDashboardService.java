package com.medical.agent.application;

import com.medical.agent.application.service.RecordService;
import com.medical.agent.domain.dto.response.AgentDashboardResponseData;
import com.medical.agent.domain.dto.response.PatientCareFollowUpTaskListResponseData;
import com.medical.agent.domain.dto.response.PatientCareProfileResponseData;
import com.medical.agent.domain.dto.response.PatientCareRiskOverviewResponseData;
import com.medical.agent.domain.dto.response.PatientCareSymptomLogListResponseData;
import com.medical.agent.domain.exception.BusinessException;
import com.medical.agent.domain.exception.ResourceNotFoundException;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import com.medical.agent.domain.vo.RecordTrendData;
import com.medical.agent.domain.vo.TrendField;
import com.medical.agent.domain.vo.TrendSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AgentDashboardService {
  private static final int DASHBOARD_TASK_LIMIT = 6;
  private static final int DASHBOARD_SYMPTOM_LIMIT = 6;
  private static final int TREND_LIMIT = 4;

  private final DiseaseProfileQueryService diseaseProfileQueryService;
  private final PatientCareService patientCareService;
  private final RecordService recordService;

  public AgentDashboardService(
      DiseaseProfileQueryService diseaseProfileQueryService,
      PatientCareService patientCareService,
      RecordService recordService) {
    this.diseaseProfileQueryService = diseaseProfileQueryService;
    this.patientCareService = patientCareService;
    this.recordService = recordService;
  }

  public AgentDashboardResponseData getDashboard(String profileId) {
    List<DiseaseProfileOverview> profiles = diseaseProfileQueryService.listProfiles();
    DiseaseProfileOverview selectedProfile = selectProfile(profiles, profileId);
    if (selectedProfile == null) {
      PatientCareProfileResponseData careProfile = patientCareService.getProfile();
      return new AgentDashboardResponseData(
          profiles,
          null,
          null,
          List.of(),
          patientCareService.getRiskOverview(null, null, careProfile),
          List.of(),
          List.of(),
          careProfile.currentMedications(),
          careProfile.careGoals(),
          List.of(),
          List.of());
    }

    DiseaseProfileQueryService.ProfileRecordsResult recordResult =
        diseaseProfileQueryService.listProfileRecords(selectedProfile.profileId());
    List<DiseaseProfileRecordSummary> records = recordResult.records();
    DiseaseProfileRecordSummary latestRecord = findLatestRecord(records, selectedProfile.latestRecordId());
    String latestRecordId = latestRecord == null ? null : latestRecord.id();

    PatientCareProfileResponseData careProfile = patientCareService.getProfile();
    PatientCareRiskOverviewResponseData riskOverview =
        patientCareService.getRiskOverview(selectedProfile.profileId(), latestRecordId, careProfile);
    PatientCareFollowUpTaskListResponseData tasks =
        patientCareService.listFollowUpTasks("OPEN", DASHBOARD_TASK_LIMIT, selectedProfile.profileId());
    PatientCareSymptomLogListResponseData symptoms =
        patientCareService.listSymptoms(DASHBOARD_SYMPTOM_LIMIT, selectedProfile.profileId());

    return new AgentDashboardResponseData(
        profiles,
        selectedProfile,
        latestRecord,
        records,
        riskOverview,
        tasks.tasks(),
        symptoms.logs(),
        careProfile.currentMedications(),
        careProfile.careGoals(),
        buildTrendHighlights(latestRecord),
        sourceTypes(records));
  }

  public RecordTrendData getTrendBySourceType(String profileId, String sourceType, int limit) {
    String normalizedSourceType = TextUtils.trimToNull(sourceType);
    if (normalizedSourceType == null) {
      throw new IllegalArgumentException("sourceType is required");
    }
    int normalizedLimit = Math.max(1, limit);

    List<DiseaseProfileOverview> profiles = diseaseProfileQueryService.listProfiles();
    DiseaseProfileOverview selectedProfile = selectProfile(profiles, profileId);
    if (selectedProfile == null) {
      return emptyTrend(normalizedSourceType, "unknown", normalizedLimit);
    }

    DiseaseProfileQueryService.ProfileRecordsResult recordResult =
        diseaseProfileQueryService.listProfileRecords(selectedProfile.profileId());
    for (DiseaseProfileRecordSummary record : recordResult.records()) {
      if (normalizedSourceType.equals(TextUtils.trimToNull(record.sourceType()))) {
        return recordService.fetchTrend(UUID.fromString(record.id()), normalizedLimit);
      }
    }
    return emptyTrend(normalizedSourceType, selectedProfile.profileId(), normalizedLimit);
  }

  private DiseaseProfileOverview selectProfile(List<DiseaseProfileOverview> profiles, String profileId) {
    String requested = TextUtils.trimToNull(profileId);
    if (requested == null) {
      return profiles.isEmpty() ? null : profiles.get(0);
    }
    try {
      UUID.fromString(requested);
    } catch (IllegalArgumentException error) {
      throw new BusinessException("INVALID_PROFILE_ID", "profileId is invalid");
    }
    return profiles.stream()
        .filter(profile -> requested.equals(profile.profileId()))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "disease profile not found"));
  }

  private DiseaseProfileRecordSummary findLatestRecord(List<DiseaseProfileRecordSummary> records, String latestRecordId) {
    if (records.isEmpty()) {
      return null;
    }
    String targetRecordId = TextUtils.trimToNull(latestRecordId);
    if (targetRecordId != null) {
      for (DiseaseProfileRecordSummary record : records) {
        if (targetRecordId.equals(record.id())) {
          return record;
        }
      }
    }
    return records.get(0);
  }

  private List<AgentDashboardResponseData.TrendHighlight> buildTrendHighlights(DiseaseProfileRecordSummary latestRecord) {
    if (latestRecord == null) {
      return List.of();
    }
    RecordTrendData trendData;
    try {
      trendData = recordService.fetchTrend(UUID.fromString(latestRecord.id()), 3);
    } catch (Exception error) {
      return List.of();
    }
    List<TrendSnapshot> snapshots = trendData.snapshots() == null ? List.of() : trendData.snapshots();
    if (snapshots == null || snapshots.size() < 2) {
      return List.of();
    }
    TrendSnapshot previous = snapshots.get(snapshots.size() - 2);
    TrendSnapshot current = snapshots.get(snapshots.size() - 1);
    List<TrendField> previousSnapshotFields = previous.fields() == null ? List.of() : previous.fields();
    List<TrendField> currentSnapshotFields = current.fields() == null ? List.of() : current.fields();
    Map<String, TrendField> previousFields = previousSnapshotFields.stream()
        .filter(field -> TextUtils.trimToNull(field.name()) != null)
        .collect(Collectors.toMap(
            field -> field.name().trim(),
            field -> field,
            (left, right) -> left));

    List<AgentDashboardResponseData.TrendHighlight> highlights = new ArrayList<>();
    for (TrendField field : currentSnapshotFields) {
      TrendField previousField = previousFields.get(TextUtils.trimToNull(field.name()));
      if (previousField == null) {
        continue;
      }
      String direction = trendDirection(previousField, field);
      if ("stable".equals(direction) && !isAbnormal(field.resultState())) {
        continue;
      }
      highlights.add(new AgentDashboardResponseData.TrendHighlight(
          field.name(),
          field.value(),
          previousField.value(),
          TextUtils.trimToNull(field.unit()),
          direction,
          TextUtils.trimToNull(field.resultState()),
          current.recordId(),
          current.recordDate()));
      if (highlights.size() >= TREND_LIMIT) {
        break;
      }
    }
    return highlights;
  }

  private String trendDirection(TrendField previous, TrendField current) {
    if (previous.numericValue() == null || current.numericValue() == null) {
      return "stable";
    }
    int compared = Double.compare(current.numericValue(), previous.numericValue());
    if (compared > 0) {
      return "up";
    }
    if (compared < 0) {
      return "down";
    }
    return "stable";
  }

  private boolean isAbnormal(String resultState) {
    String normalized = TextUtils.trimToNull(resultState);
    if (normalized == null) {
      return false;
    }
    return switch (normalized.toLowerCase()) {
      case "high", "low", "threshold" -> true;
      default -> false;
    };
  }

  private List<String> sourceTypes(List<DiseaseProfileRecordSummary> records) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    for (DiseaseProfileRecordSummary record : records) {
      String sourceType = TextUtils.trimToNull(record.sourceType());
      if (sourceType != null) {
        values.add(sourceType);
      }
    }
    return List.copyOf(values);
  }

  private RecordTrendData emptyTrend(String sourceType, String diseaseProfileId, int limit) {
    return new RecordTrendData("", sourceType, diseaseProfileId, limit, List.of());
  }
}
