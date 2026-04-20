package com.medical.agent.domain.dto.response;

import java.util.List;

public record AgentDiseaseProfileContextResponse(
    AgentDiseaseProfileSummary profile,
    AgentRecordContextSummary selectedRecord,
    List<AgentRecordContextSummary> recentRecords,
    AgentRecordContextData recordSummary,
    List<AgentTrendSnapshotSummary> trendSummary,
    PatientCareProfileResponseData.BaselineSummary patientBaseline,
    List<PatientCareProfileResponseData.MedicationItem> currentMedications,
    List<String> careGoals,
    List<PatientCareFollowUpTaskListResponseData.TaskSummary> followUpTasks,
    List<PatientCareRiskOverviewResponseData.RiskSignal> redFlagSignals,
    List<PatientCareRiskOverviewResponseData.EvidenceItem> evidenceRefs,
    String contextStatus,
    List<String> warnings) {}

