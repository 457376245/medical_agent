export type AgentProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
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

export type AgentRequestMetadata = {
  disease_profile_id?: string;
  disease_name?: string;
  record_id?: string;
  record_title?: string;
  record_date?: string;
  source_type?: string;
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
};

export type AgentSseEvent = {
  event: string;
  data: Record<string, unknown>;
};
