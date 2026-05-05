import type { ComparisonType, ResultState } from "../parse/structuredFieldInterpretation";

export type AgentProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
  latestRecordId?: string;
  latestRecordTitle?: string;
  latestParseStatus?: string;
};

export type AgentRecord = {
  id: string;
  title: string;
  recordDate: string;
  sourceType: string;
};

export type AgentStructuredField = {
  name: string;
  value: string;
  unit?: string;
  referenceRange?: string;
  numericValue?: number;
  comparisonType?: ComparisonType;
  resultState?: ResultState;
  referenceLowerBound?: number;
  referenceUpperBound?: number;
  referenceLowerInclusive?: boolean;
  referenceUpperInclusive?: boolean;
};

export type AgentRecordDetail = {
  recordId: string;
  summary: string;
  parseStatus: string;
  fields: AgentStructuredField[];
};

export type AgentTrendSnapshot = {
  recordId: string;
  recordDate: string;
  title: string;
  sourceType: string;
  fields: AgentStructuredField[];
};

export type AgentTrendData = {
  recordId: string;
  sourceType: string;
  diseaseProfileId: string;
  limit: number;
  snapshots: AgentTrendSnapshot[];
};

export type AgentWorkflow = "report_interpretation" | "follow_up_prep" | "medication_review" | "abnormal_reasoning";
export type AgentAudience = "patient" | "caregiver" | "clinician";
export type AgentUrgencyLevel = "routine" | "watch" | "warning" | "alert";

export type AgentRequestMetadata = {
  disease_profile_id?: string;
  disease_name?: string;
  record_id?: string;
  record_title?: string;
  record_date?: string;
  source_type?: string;
  scenario?: string;
  workflow?: AgentWorkflow;
  urgency_level?: AgentUrgencyLevel;
  audience?: AgentAudience;
  entry: "agent_page";
};

export type AgentTraceEvent = {
  event: "tool_call" | "tool_result" | "error";
  tool?: string;
  data: Record<string, unknown>;
  createdAt?: string;
};

export type AgentMessage = {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  turnId?: string;
  turnIndex?: number;
  createdAt?: string;
  errorMessage?: string;
  traceEvents?: AgentTraceEvent[];
  isStreaming?: boolean;
};

export type AgentSessionSummary = {
  threadId: string;
  diseaseProfileId?: string;
  diseaseName?: string;
  recordId?: string;
  recordTitle?: string;
  recordDate?: string;
  sourceType?: string;
  title: string;
  lastUserMessage?: string;
  lastAssistantMessage?: string;
  lastMessagePreview?: string;
  turnCount: number;
  createdAt?: string;
  updatedAt?: string;
  isStreaming?: boolean;
};

export type AgentSessionTurn = {
  turnId?: string;
  threadId: string;
  turnIndex: number;
  userMessage: string;
  assistantMessage: string;
  metadata: AgentRequestMetadata;
  traceEvents: AgentTraceEvent[];
  errorMessage?: string;
  createdAt?: string;
};

export type AgentSessionDetail = {
  threadId: string;
  title: string;
  diseaseProfileId?: string;
  diseaseName?: string;
  recordId?: string;
  recordTitle?: string;
  recordDate?: string;
  sourceType?: string;
  turnCount: number;
  createdAt?: string;
  updatedAt?: string;
  turns: AgentSessionTurn[];
  messages: AgentMessage[];
};

export type AgentWorkbenchProps = {
  profiles: AgentProfile[];
  initialProfileId?: string;
  initialRecordId?: string;
  initialRecords: AgentRecord[];
  patientId?: string;
};

export type AgentSseEvent = {
  event: string;
  data: Record<string, unknown>;
};

export type CareMedication = {
  name: string;
  dosage?: string;
  frequency?: string;
  purpose?: string;
};

export type CareSymptomItem = {
  id: string;
  label: string;
  value?: string;
  unit?: string;
  alertLevel?: string;
  notes?: string;
  recordedAt?: string;
  diseaseProfileId?: string;
};

export type CareBaseline = {
  diagnosedConditions: string[];
  allergies: string[];
  abnormalBaseline: string[];
  doctorInstructions?: string;
  recentSymptoms: CareSymptomItem[];
};

export type CareProfile = {
  patientBaseline: CareBaseline;
  currentMedications: CareMedication[];
  careGoals: string[];
  redFlagNotes: string[];
  updatedAt?: string;
};

export type FollowUpTask = {
  id: string;
  title: string;
  dueDate?: string;
  priority?: string;
  status?: string;
  notes?: string;
  diseaseProfileId?: string;
  recordId?: string;
  createdAt?: string;
};

export type RiskSignal = {
  severity?: string;
  title: string;
  detail?: string;
  recommendedAction?: string;
};

export type EvidenceRef = {
  type?: string;
  title: string;
  detail?: string;
  source?: string;
  confidence?: string;
  nature?: string;
};

export type RiskOverview = {
  riskLevel: AgentUrgencyLevel;
  summary: string;
  signals: RiskSignal[];
  evidenceRefs: EvidenceRef[];
};

export type AgentTrendHighlight = {
  name: string;
  currentValue: string;
  previousValue?: string;
  unit?: string;
  direction?: "up" | "down" | "stable" | string;
  resultState?: string;
  recordId?: string;
  recordDate?: string;
};

export type AgentDashboardData = {
  profiles: AgentProfile[];
  selectedProfile?: AgentProfile;
  latestRecord?: AgentRecord;
  records: AgentRecord[];
  riskOverview: RiskOverview;
  followUpTasks: FollowUpTask[];
  symptoms: CareSymptomItem[];
  currentMedications: CareMedication[];
  careGoals: string[];
  trendHighlights: AgentTrendHighlight[];
  sourceTypes: string[];
};
