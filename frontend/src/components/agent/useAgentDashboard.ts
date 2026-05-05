"use client";

import { useCallback, useEffect, useState } from "react";
import { authFetch } from "../../lib/api";
import { asObject, toOptionalText, toText } from "./agent-utils";
import type {
  AgentDashboardData,
  AgentProfile,
  AgentRecord,
  AgentTrendHighlight,
  AgentUrgencyLevel,
  CareMedication,
  CareSymptomItem,
  FollowUpTask,
  RiskOverview,
  RiskSignal,
  EvidenceRef,
} from "./types";

const EMPTY_RISK: RiskOverview = {
  riskLevel: "routine",
  summary: "当前未发现明显高优先级风险，可按既定计划随访。",
  signals: [],
  evidenceRefs: [],
};

function normalizeUrgency(value: unknown): AgentUrgencyLevel {
  const normalized = toText(value).trim().toLowerCase();
  if (normalized === "watch" || normalized === "warning" || normalized === "alert") {
    return normalized;
  }
  return "routine";
}

function normalizeProfile(raw: unknown): AgentProfile | null {
  const payload = asObject(raw);
  const profileId = toOptionalText(payload.profileId ?? payload.profile_id);
  if (!profileId) return null;
  return {
    profileId,
    diseaseName: toOptionalText(payload.diseaseName ?? payload.disease_name) ?? "未分类疾病",
    recordCount: Number(payload.recordCount ?? payload.record_count ?? 0),
    latestRecordAt: toOptionalText(payload.latestRecordAt ?? payload.latest_record_at),
    latestRecordId: toOptionalText(payload.latestRecordId ?? payload.latest_record_id),
    latestRecordTitle: toOptionalText(payload.latestRecordTitle ?? payload.latest_record_title),
    latestParseStatus: toOptionalText(payload.latestParseStatus ?? payload.latest_parse_status),
  };
}

function normalizeRecord(raw: unknown): AgentRecord | null {
  const payload = asObject(raw);
  const id = toOptionalText(payload.id ?? payload.recordId ?? payload.record_id);
  if (!id) return null;
  return {
    id,
    title: toOptionalText(payload.title) ?? "未命名报告",
    recordDate: toOptionalText(payload.recordDate ?? payload.record_date) ?? "",
    sourceType: toOptionalText(payload.sourceType ?? payload.source_type) ?? "UPLOAD",
  };
}

function normalizeMedication(raw: unknown): CareMedication | null {
  const payload = asObject(raw);
  const name = toOptionalText(payload.name);
  if (!name) return null;
  return {
    name,
    dosage: toOptionalText(payload.dosage),
    frequency: toOptionalText(payload.frequency),
    purpose: toOptionalText(payload.purpose),
  };
}

function normalizeTask(raw: unknown): FollowUpTask | null {
  const payload = asObject(raw);
  const id = toOptionalText(payload.id);
  const title = toOptionalText(payload.title);
  if (!id || !title) return null;
  return {
    id,
    title,
    dueDate: toOptionalText(payload.dueDate ?? payload.due_date),
    priority: toOptionalText(payload.priority),
    status: toOptionalText(payload.status),
    notes: toOptionalText(payload.notes),
    diseaseProfileId: toOptionalText(payload.diseaseProfileId ?? payload.disease_profile_id),
    recordId: toOptionalText(payload.recordId ?? payload.record_id),
    createdAt: toOptionalText(payload.createdAt ?? payload.created_at),
  };
}

function normalizeSymptom(raw: unknown): CareSymptomItem | null {
  const payload = asObject(raw);
  const id = toOptionalText(payload.id);
  const label = toOptionalText(payload.label);
  if (!id || !label) return null;
  return {
    id,
    label,
    value: toOptionalText(payload.value),
    unit: toOptionalText(payload.unit),
    alertLevel: toOptionalText(payload.alertLevel ?? payload.alert_level),
    notes: toOptionalText(payload.notes),
    recordedAt: toOptionalText(payload.recordedAt ?? payload.recorded_at),
    diseaseProfileId: toOptionalText(payload.diseaseProfileId ?? payload.disease_profile_id),
  };
}

function normalizeSignal(raw: unknown): RiskSignal | null {
  const payload = asObject(raw);
  const title = toOptionalText(payload.title);
  if (!title) return null;
  return {
    severity: toOptionalText(payload.severity),
    title,
    detail: toOptionalText(payload.detail),
    recommendedAction: toOptionalText(payload.recommendedAction ?? payload.recommended_action),
  };
}

function normalizeEvidence(raw: unknown): EvidenceRef | null {
  const payload = asObject(raw);
  const title = toOptionalText(payload.title);
  if (!title) return null;
  return {
    type: toOptionalText(payload.type),
    title,
    detail: toOptionalText(payload.detail),
    source: toOptionalText(payload.source),
    confidence: toOptionalText(payload.confidence),
    nature: toOptionalText(payload.nature),
  };
}

function normalizeRisk(raw: unknown): RiskOverview {
  const payload = asObject(raw);
  const evidenceRaw = payload.evidenceRefs ?? payload.evidence_refs;
  return {
    riskLevel: normalizeUrgency(payload.riskLevel ?? payload.risk_level),
    summary: toOptionalText(payload.summary) ?? EMPTY_RISK.summary,
    signals: Array.isArray(payload.signals)
      ? payload.signals.map(normalizeSignal).filter((item): item is RiskSignal => Boolean(item))
      : [],
    evidenceRefs: Array.isArray(evidenceRaw)
      ? evidenceRaw.map(normalizeEvidence).filter((item): item is EvidenceRef => Boolean(item))
      : [],
  };
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function normalizeHighlight(raw: unknown): AgentTrendHighlight | null {
  const payload = asObject(raw);
  const name = toOptionalText(payload.name);
  const currentValue = toOptionalText(payload.currentValue ?? payload.current_value);
  if (!name || !currentValue) return null;
  return {
    name,
    currentValue,
    previousValue: toOptionalText(payload.previousValue ?? payload.previous_value),
    unit: toOptionalText(payload.unit),
    direction: toOptionalText(payload.direction),
    resultState: toOptionalText(payload.resultState ?? payload.result_state),
    recordId: toOptionalText(payload.recordId ?? payload.record_id),
    recordDate: toOptionalText(payload.recordDate ?? payload.record_date),
  };
}

function normalizeDashboard(raw: unknown): AgentDashboardData {
  const payload = asObject(raw);
  const selectedProfile = normalizeProfile(payload.selectedProfile ?? payload.selected_profile) ?? undefined;
  const latestRecord = normalizeRecord(payload.latestRecord ?? payload.latest_record) ?? undefined;
  const followUpTasks = asArray(payload.followUpTasks ?? payload.follow_up_tasks);
  const medications = asArray(payload.currentMedications ?? payload.current_medications);
  const careGoals = asArray(payload.careGoals ?? payload.care_goals);
  const trendHighlights = asArray(payload.trendHighlights ?? payload.trend_highlights);
  const sourceTypes = asArray(payload.sourceTypes ?? payload.source_types);
  return {
    profiles: Array.isArray(payload.profiles)
      ? payload.profiles.map(normalizeProfile).filter((item): item is AgentProfile => Boolean(item))
      : [],
    selectedProfile,
    latestRecord,
    records: Array.isArray(payload.records)
      ? payload.records.map(normalizeRecord).filter((item): item is AgentRecord => Boolean(item))
      : [],
    riskOverview: normalizeRisk(payload.riskOverview ?? payload.risk_overview),
    followUpTasks: followUpTasks.map(normalizeTask).filter((item): item is FollowUpTask => Boolean(item)),
    symptoms: Array.isArray(payload.symptoms)
      ? payload.symptoms.map(normalizeSymptom).filter((item): item is CareSymptomItem => Boolean(item))
      : [],
    currentMedications: medications.map(normalizeMedication).filter((item): item is CareMedication => Boolean(item)),
    careGoals: careGoals.map((item: unknown) => toText(item).trim()).filter(Boolean),
    trendHighlights: trendHighlights.map(normalizeHighlight).filter((item): item is AgentTrendHighlight => Boolean(item)),
    sourceTypes: sourceTypes.map((item: unknown) => toText(item).trim()).filter(Boolean),
  };
}

export function useAgentDashboard(profileId?: string, patientId?: string) {
  const [data, setData] = useState<AgentDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams();
      if (profileId) params.set("profileId", profileId);
      const response = await authFetch(`/agent/dashboard${params.size > 0 ? `?${params.toString()}` : ""}`);
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(toText(payload.message || "加载 Agent 总览失败，请稍后重试。"));
      }
      setData(normalizeDashboard(payload.data));
    } catch (loadError) {
      setData(null);
      setError(loadError instanceof Error ? loadError.message : "加载 Agent 总览失败，请稍后重试。");
    } finally {
      setLoading(false);
    }
  }, [profileId]);

  useEffect(() => {
    void reload();
  }, [reload, patientId]);

  return { data, loading, error, reload };
}
