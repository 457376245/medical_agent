"use client";

import { useCallback, useEffect, useState } from "react";
import { authFetch } from "../../lib/api";
import { asObject, toOptionalText, toText } from "./agent-utils";
import type {
  AgentUrgencyLevel,
  CareMedication,
  CareProfile,
  CareSymptomItem,
  EvidenceRef,
  FollowUpTask,
  PatientMemoryEntry,
  RiskOverview,
  RiskSignal,
} from "./types";

type SaveCareProfileInput = {
  diagnosedConditions: string[];
  currentMedications: CareMedication[];
  allergies: string[];
  abnormalBaseline: string[];
  doctorInstructions?: string;
  careGoals: string[];
  redFlagNotes: string[];
  personalContext: string[];
};

type CreateTaskInput = {
  title: string;
  dueDate?: string;
  priority?: string;
  notes?: string;
  diseaseProfileId?: string;
  recordId?: string;
};

type UpdateTaskInput = {
  title?: string;
  dueDate?: string;
  priority?: string;
  status?: string;
  notes?: string;
};

type CreateSymptomInput = {
  label: string;
  value?: string;
  unit?: string;
  alertLevel?: string;
  notes?: string;
  recordedAt?: string;
  diseaseProfileId?: string;
};

const EMPTY_PROFILE: CareProfile = {
  patientBaseline: {
    diagnosedConditions: [],
    allergies: [],
    abnormalBaseline: [],
    doctorInstructions: undefined,
    recentSymptoms: [],
  },
  currentMedications: [],
  careGoals: [],
  redFlagNotes: [],
  personalContext: [],
  updatedAt: undefined,
};

const EMPTY_RISK: RiskOverview = {
  riskLevel: "routine",
  summary: "当前未发现明显高优先级风险。",
  signals: [],
  evidenceRefs: [],
};

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

function normalizeSymptom(raw: unknown): CareSymptomItem | null {
  const payload = asObject(raw);
  const label = toOptionalText(payload.label);
  const id = toOptionalText(payload.id);
  if (!label || !id) return null;
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

function normalizeMemory(raw: unknown): PatientMemoryEntry | null {
  const payload = asObject(raw);
  const id = toOptionalText(payload.id);
  const fieldPath = toOptionalText(payload.fieldPath ?? payload.field_path);
  if (!id || !fieldPath) return null;
  const confidenceRaw = payload.confidence;
  return {
    id,
    memoryType: toOptionalText(payload.memoryType ?? payload.memory_type),
    fieldPath,
    valueText: toOptionalText(payload.valueText ?? payload.value_text),
    valueJson: toOptionalText(payload.valueJson ?? payload.value_json),
    evidenceText: toOptionalText(payload.evidenceText ?? payload.evidence_text),
    sourceType: toOptionalText(payload.sourceType ?? payload.source_type),
    sourceRef: toOptionalText(payload.sourceRef ?? payload.source_ref),
    confidence: typeof confidenceRaw === "number" ? confidenceRaw : undefined,
    riskLevel: toOptionalText(payload.riskLevel ?? payload.risk_level),
    status: toOptionalText(payload.status),
    diseaseProfileId: toOptionalText(payload.diseaseProfileId ?? payload.disease_profile_id),
    recordId: toOptionalText(payload.recordId ?? payload.record_id),
    conversationThreadId: toOptionalText(payload.conversationThreadId ?? payload.conversation_thread_id),
    turnId: toOptionalText(payload.turnId ?? payload.turn_id),
    rejectionReason: toOptionalText(payload.rejectionReason ?? payload.rejection_reason),
    confirmedAt: toOptionalText(payload.confirmedAt ?? payload.confirmed_at),
    createdAt: toOptionalText(payload.createdAt ?? payload.created_at),
    updatedAt: toOptionalText(payload.updatedAt ?? payload.updated_at),
  };
}

function normalizeUrgency(value: unknown): AgentUrgencyLevel {
  const normalized = toText(value).trim().toLowerCase();
  if (normalized === "watch" || normalized === "warning" || normalized === "alert") {
    return normalized;
  }
  return "routine";
}

function normalizeCareProfile(raw: unknown): CareProfile {
  const payload = asObject(raw);
  const baseline = asObject(payload.patientBaseline ?? payload.patient_baseline);
  const recentSymptomsRaw = baseline.recentSymptoms ?? baseline.recent_symptoms;
  const diagnosedConditionsRaw = baseline.diagnosedConditions ?? baseline.diagnosed_conditions;
  const abnormalBaselineRaw = baseline.abnormalBaseline ?? baseline.abnormal_baseline;
  const medicationsRaw = payload.currentMedications ?? payload.current_medications;
  const careGoalsRaw = payload.careGoals ?? payload.care_goals;
  const redFlagNotesRaw = payload.redFlagNotes ?? payload.red_flag_notes;
  const personalContextRaw = payload.personalContext ?? payload.personal_context;
  const recentSymptoms = Array.isArray(recentSymptomsRaw)
    ? recentSymptomsRaw.map(normalizeSymptom).filter((item): item is CareSymptomItem => Boolean(item))
    : [];

  return {
    patientBaseline: {
      diagnosedConditions: Array.isArray(diagnosedConditionsRaw)
        ? diagnosedConditionsRaw.map((item: unknown) => toText(item).trim()).filter(Boolean)
        : [],
      allergies: Array.isArray(baseline.allergies)
        ? baseline.allergies.map((item: unknown) => toText(item).trim()).filter(Boolean)
        : [],
      abnormalBaseline: Array.isArray(abnormalBaselineRaw)
        ? abnormalBaselineRaw.map((item: unknown) => toText(item).trim()).filter(Boolean)
        : [],
      doctorInstructions: toOptionalText(baseline.doctorInstructions ?? baseline.doctor_instructions),
      recentSymptoms,
    },
    currentMedications: Array.isArray(medicationsRaw)
      ? medicationsRaw.map(normalizeMedication).filter((item): item is CareMedication => Boolean(item))
      : [],
    careGoals: Array.isArray(careGoalsRaw)
      ? careGoalsRaw.map((item: unknown) => toText(item).trim()).filter(Boolean)
      : [],
    redFlagNotes: Array.isArray(redFlagNotesRaw)
      ? redFlagNotesRaw.map((item: unknown) => toText(item).trim()).filter(Boolean)
      : [],
    personalContext: Array.isArray(personalContextRaw)
      ? personalContextRaw.map((item: unknown) => toText(item).trim()).filter(Boolean)
      : [],
    updatedAt: toOptionalText(payload.updatedAt ?? payload.updated_at),
  };
}

function normalizeRiskOverview(raw: unknown): RiskOverview {
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

async function readApiData(path: string): Promise<Record<string, unknown>> {
  const response = await authFetch(path);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(toText(payload.message || "请求失败，请稍后重试。"));
  }
  return asObject(payload.data);
}

export type UseCareSupportResult = {
  careProfile: CareProfile;
  followUpTasks: FollowUpTask[];
  symptoms: CareSymptomItem[];
  pendingMemories: PatientMemoryEntry[];
  riskOverview: RiskOverview;
  loadingCare: boolean;
  loadingRisk: boolean;
  careError: string;
  saveCareProfile: (input: SaveCareProfileInput) => Promise<void>;
  createFollowUpTask: (input: CreateTaskInput) => Promise<void>;
  updateFollowUpTask: (taskId: string, input: UpdateTaskInput) => Promise<void>;
  createSymptomLog: (input: CreateSymptomInput) => Promise<void>;
  confirmMemory: (memoryId: string) => Promise<void>;
  rejectMemory: (memoryId: string) => Promise<void>;
  reloadCareSupport: () => Promise<void>;
};

export function useCareSupport(
  patientId?: string,
  profileId?: string,
  recordId?: string,
): UseCareSupportResult {
  const [careProfile, setCareProfile] = useState<CareProfile>(EMPTY_PROFILE);
  const [followUpTasks, setFollowUpTasks] = useState<FollowUpTask[]>([]);
  const [symptoms, setSymptoms] = useState<CareSymptomItem[]>([]);
  const [pendingMemories, setPendingMemories] = useState<PatientMemoryEntry[]>([]);
  const [riskOverview, setRiskOverview] = useState<RiskOverview>(EMPTY_RISK);
  const [loadingCare, setLoadingCare] = useState(false);
  const [loadingRisk, setLoadingRisk] = useState(false);
  const [careError, setCareError] = useState("");

  const loadMemories = useCallback(async () => {
    try {
      const data = await readApiData("/patient-care/memories?status=PROPOSED&limit=20");
      setPendingMemories(
        Array.isArray(data.memories)
          ? data.memories.map(normalizeMemory).filter((item): item is PatientMemoryEntry => Boolean(item))
          : [],
      );
    } catch {
      setPendingMemories([]);
    }
  }, []);

  const loadCare = useCallback(async () => {
    setLoadingCare(true);
    setCareError("");
    try {
      const scoped = profileId ? `&profileId=${encodeURIComponent(profileId)}` : "";
      const [profileData, taskData, symptomData] = await Promise.all([
        readApiData("/patient-care/profile"),
        readApiData(`/patient-care/follow-up-tasks?status=OPEN&limit=8${scoped}`),
        readApiData(`/patient-care/symptoms?limit=6${profileId ? `&profileId=${encodeURIComponent(profileId)}` : ""}`),
      ]);
      setCareProfile(normalizeCareProfile(profileData));
      setFollowUpTasks(
        Array.isArray(taskData.tasks)
          ? taskData.tasks.map(normalizeTask).filter((item): item is FollowUpTask => Boolean(item))
          : [],
      );
      setSymptoms(
        Array.isArray(symptomData.logs)
          ? symptomData.logs.map(normalizeSymptom).filter((item): item is CareSymptomItem => Boolean(item))
          : [],
      );
      await loadMemories();
    } catch (error) {
      setCareError(error instanceof Error ? error.message : "加载慢病驾驶舱失败，请稍后重试。");
      setCareProfile(EMPTY_PROFILE);
      setFollowUpTasks([]);
      setSymptoms([]);
      setPendingMemories([]);
    } finally {
      setLoadingCare(false);
    }
  }, [loadMemories, profileId]);

  const loadRisk = useCallback(async () => {
    setLoadingRisk(true);
    try {
      const params = new URLSearchParams();
      if (profileId) params.set("profileId", profileId);
      if (recordId) params.set("recordId", recordId);
      const path = `/patient-care/risk-overview${params.size > 0 ? `?${params.toString()}` : ""}`;
      const data = await readApiData(path);
      setRiskOverview(normalizeRiskOverview(data));
    } catch (error) {
      setRiskOverview(EMPTY_RISK);
      setCareError((prev) => prev || (error instanceof Error ? error.message : "加载风险概览失败，请稍后重试。"));
    } finally {
      setLoadingRisk(false);
    }
  }, [profileId, recordId]);

  const loadTasks = useCallback(async () => {
    try {
      const data = await readApiData(`/patient-care/follow-up-tasks?status=OPEN&limit=8${profileId ? `&profileId=${encodeURIComponent(profileId)}` : ""}`);
      setFollowUpTasks(
        Array.isArray(data.tasks)
          ? data.tasks.map(normalizeTask).filter((item): item is FollowUpTask => Boolean(item))
          : [],
      );
    } catch {
      setFollowUpTasks([]);
    }
  }, [profileId]);

  const loadSymptoms = useCallback(async () => {
    try {
      const data = await readApiData(`/patient-care/symptoms?limit=6${profileId ? `&profileId=${encodeURIComponent(profileId)}` : ""}`);
      setSymptoms(
        Array.isArray(data.logs)
          ? data.logs.map(normalizeSymptom).filter((item): item is CareSymptomItem => Boolean(item))
          : [],
      );
    } catch {
      setSymptoms([]);
    }
  }, [profileId]);

  const reloadCareSupport = useCallback(async () => {
    await Promise.all([loadCare(), loadRisk(), loadMemories()]);
  }, [loadCare, loadRisk, loadMemories]);

  useEffect(() => {
    void loadCare();
  }, [loadCare, patientId]);

  useEffect(() => {
    void loadRisk();
  }, [loadRisk, patientId]);

  const saveCareProfile = useCallback(async (input: SaveCareProfileInput) => {
    setCareError("");
    const response = await authFetch("/patient-care/profile", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        diagnosedConditions: input.diagnosedConditions,
        currentMedications: input.currentMedications,
        allergies: input.allergies,
        abnormalBaseline: input.abnormalBaseline,
        doctorInstructions: input.doctorInstructions,
        careGoals: input.careGoals,
        redFlagNotes: input.redFlagNotes,
        personalContext: input.personalContext,
      }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "保存慢病画像失败。"));
    }
    setCareProfile(normalizeCareProfile(payload.data));
    await loadRisk();
  }, [loadRisk]);

  const createFollowUpTask = useCallback(async (input: CreateTaskInput) => {
    const response = await authFetch("/patient-care/follow-up-tasks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "创建随访任务失败。"));
    }
    await Promise.all([loadTasks(), loadRisk()]);
  }, [loadTasks, loadRisk]);

  const updateFollowUpTask = useCallback(async (taskId: string, input: UpdateTaskInput) => {
    const response = await authFetch(`/patient-care/follow-up-tasks/${encodeURIComponent(taskId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "更新随访任务失败。"));
    }
    await Promise.all([loadTasks(), loadRisk()]);
  }, [loadTasks, loadRisk]);

  const createSymptomLog = useCallback(async (input: CreateSymptomInput) => {
    const response = await authFetch("/patient-care/symptoms", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "记录症状/体征失败。"));
    }
    await Promise.all([loadSymptoms(), loadRisk()]);
  }, [loadSymptoms, loadRisk]);

  const confirmMemory = useCallback(async (memoryId: string) => {
    const response = await authFetch(`/patient-care/memories/${encodeURIComponent(memoryId)}/confirm`, {
      method: "POST",
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "确认画像更新失败。"));
    }
    await reloadCareSupport();
  }, [reloadCareSupport]);

  const rejectMemory = useCallback(async (memoryId: string) => {
    const response = await authFetch(`/patient-care/memories/${encodeURIComponent(memoryId)}/reject`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: "用户拒绝" }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(toText(payload.message || "拒绝画像更新失败。"));
    }
    await loadMemories();
  }, [loadMemories]);

  return {
    careProfile,
    followUpTasks,
    symptoms,
    pendingMemories,
    riskOverview,
    loadingCare,
    loadingRisk,
    careError,
    saveCareProfile,
    createFollowUpTask,
    updateFollowUpTask,
    createSymptomLog,
    confirmMemory,
    rejectMemory,
    reloadCareSupport,
  };
}
