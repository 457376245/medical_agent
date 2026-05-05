package com.medical.agent.domain.dto.response;

import com.medical.agent.domain.vo.DiseaseProfileOverview;
import com.medical.agent.domain.vo.DiseaseProfileRecordSummary;
import java.util.List;

public record AgentDashboardResponseData(
    List<DiseaseProfileOverview> profiles,
    DiseaseProfileOverview selectedProfile,
    DiseaseProfileRecordSummary latestRecord,
    List<DiseaseProfileRecordSummary> records,
    PatientCareRiskOverviewResponseData riskOverview,
    List<PatientCareFollowUpTaskListResponseData.TaskSummary> followUpTasks,
    List<PatientCareSymptomLogListResponseData.SymptomLogItem> symptoms,
    List<PatientCareProfileResponseData.MedicationItem> currentMedications,
    List<String> careGoals,
    List<TrendHighlight> trendHighlights,
    List<String> sourceTypes) {

  public record TrendHighlight(
      String name,
      String currentValue,
      String previousValue,
      String unit,
      String direction,
      String resultState,
      String recordId,
      String recordDate) {}
}
