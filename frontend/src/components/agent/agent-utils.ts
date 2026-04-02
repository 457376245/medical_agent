import type {
  AgentMessage,
  AgentRequestMetadata,
  AgentSessionDetail,
  AgentSessionSummary,
  AgentSessionTurn,
  AgentSseEvent,
  AgentStructuredField,
  AgentTraceEvent,
} from "./types";

function toText(value: unknown): string {
  return typeof value === "string" ? value : String(value ?? "");
}

function toOptionalText(value: unknown): string | undefined {
  const rendered = toText(value).trim();
  return rendered || undefined;
}

function asObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function normalizeTraceEvent(raw: unknown): AgentTraceEvent {
  const payload = asObject(raw);
  const event = payload.event;
  return {
    event: event === "tool_call" || event === "tool_result" || event === "error" ? event : "error",
    tool: toOptionalText(payload.tool),
    data: asObject(payload.data),
    createdAt: toOptionalText(payload.created_at ?? payload.createdAt),
  };
}

function normalizeTurn(raw: unknown, fallbackThreadId: string): AgentSessionTurn {
  const payload = asObject(raw);
  const traceEvents = payload.trace_events ?? payload.traceEvents;
  return {
    turnId: toOptionalText(payload.turn_id ?? payload.turnId),
    threadId: toOptionalText(payload.thread_id ?? payload.threadId) ?? fallbackThreadId,
    turnIndex: Number(payload.turn_index ?? payload.turnIndex ?? 0),
    userMessage: toText(payload.user_message ?? payload.userMessage),
    assistantMessage: toText(payload.assistant_message ?? payload.assistantMessage),
    metadata: asObject(payload.metadata) as AgentRequestMetadata,
    traceEvents: Array.isArray(traceEvents) ? traceEvents.map(normalizeTraceEvent) : [],
    errorMessage: toOptionalText(payload.error_message ?? payload.errorMessage),
    createdAt: toOptionalText(payload.created_at ?? payload.createdAt),
  };
}

function contextSignatureForUi(metadata?: AgentRequestMetadata): string {
  if (!metadata?.disease_profile_id) {
    return "";
  }
  return `${metadata.disease_profile_id}:${metadata.record_id ?? ""}`;
}

type SessionShape = Pick<
  AgentSessionSummary,
  "threadId" | "diseaseProfileId" | "diseaseName" | "recordId" | "recordTitle" | "recordDate" | "sourceType" | "title" | "turnCount" | "createdAt" | "updatedAt"
>;

function normalizeSessionShape(raw: unknown): SessionShape {
  const payload = asObject(raw);
  return {
    threadId: toText(payload.thread_id ?? payload.threadId),
    diseaseProfileId: toOptionalText(payload.disease_profile_id ?? payload.diseaseProfileId),
    diseaseName: toOptionalText(payload.disease_name ?? payload.diseaseName),
    recordId: toOptionalText(payload.record_id ?? payload.recordId),
    recordTitle: toOptionalText(payload.record_title ?? payload.recordTitle),
    recordDate: toOptionalText(payload.record_date ?? payload.recordDate),
    sourceType: toOptionalText(payload.source_type ?? payload.sourceType),
    title: toOptionalText(payload.title) ?? "新对话",
    turnCount: Number(payload.turn_count ?? payload.turnCount ?? 0),
    createdAt: toOptionalText(payload.created_at ?? payload.createdAt),
    updatedAt: toOptionalText(payload.updated_at ?? payload.updatedAt),
  };
}

export function buildContextSwitchMessage(metadata?: AgentRequestMetadata): string {
  const diseaseName = metadata?.disease_name?.trim();
  const recordTitle = metadata?.record_title?.trim();
  const recordDate = metadata?.record_date?.trim();

  if (recordTitle && diseaseName) {
    return `对话上下文已切换到 ${diseaseName} / ${recordTitle}${recordDate ? `（${recordDate}）` : ""}。`;
  }
  if (diseaseName) {
    return `对话上下文已切换到 ${diseaseName}。`;
  }
  if (recordTitle) {
    return `对话上下文已切换到 ${recordTitle}${recordDate ? `（${recordDate}）` : ""}。`;
  }
  return "对话上下文已更新。";
}

export function buildMessagesFromTurns(turns: AgentSessionTurn[]): AgentMessage[] {
  const messages: AgentMessage[] = [];
  let previousContextSignature = "";

  for (const turn of turns) {
    const nextContextSignature = contextSignatureForUi(turn.metadata);
    if (nextContextSignature && nextContextSignature !== previousContextSignature) {
      messages.push({
        id: `system-${turn.turnId ?? turn.turnIndex}`,
        role: "system",
        content: buildContextSwitchMessage(turn.metadata),
        createdAt: turn.createdAt,
      });
      previousContextSignature = nextContextSignature;
    }

    messages.push({
      id: `user-${turn.turnId ?? turn.turnIndex}`,
      role: "user",
      content: turn.userMessage,
      turnId: turn.turnId,
      turnIndex: turn.turnIndex,
      createdAt: turn.createdAt,
    });
    messages.push({
      id: `assistant-${turn.turnId ?? turn.turnIndex}`,
      role: "assistant",
      content: turn.assistantMessage,
      turnId: turn.turnId,
      turnIndex: turn.turnIndex,
      createdAt: turn.createdAt,
      errorMessage: turn.errorMessage,
      traceEvents: turn.traceEvents,
    });
  }
  return messages;
}

export function normalizeSessionSummary(raw: unknown): AgentSessionSummary {
  const payload = asObject(raw);
  const shape = normalizeSessionShape(payload);
  return {
    ...shape,
    lastUserMessage: toOptionalText(payload.last_user_message ?? payload.lastUserMessage),
    lastAssistantMessage: toOptionalText(payload.last_assistant_message ?? payload.lastAssistantMessage),
    lastMessagePreview: toOptionalText(payload.last_message_preview ?? payload.lastMessagePreview),
  };
}

export function normalizeSessionDetail(raw: unknown): AgentSessionDetail {
  const payload = asObject(raw);
  const shape = normalizeSessionShape(payload);
  const turns = Array.isArray(payload.turns) ? payload.turns.map((item) => normalizeTurn(item, shape.threadId)) : [];
  return {
    ...shape,
    turns,
    messages: buildMessagesFromTurns(turns),
    turnCount: shape.turnCount || turns.length,
  };
}

export function normalizeStructuredFields(rawPayload: unknown): AgentStructuredField[] {
  const fields = Array.isArray(asObject(rawPayload).fields) ? (asObject(rawPayload).fields as unknown[]) : [];
  const normalized: AgentStructuredField[] = [];
  for (const rawField of fields) {
    const field = asObject(rawField);
    const name = toOptionalText(field.name);
    const value = toOptionalText(field.value);
    if (!name || !value) {
      continue;
    }
    normalized.push({
      name,
      value,
      unit: toOptionalText(field.unit),
      referenceRange: toOptionalText(field.referenceRange ?? field.reference_range),
    });
  }
  return normalized;
}

export function toRequestMetadata(args: {
  diseaseProfileId?: string;
  diseaseName?: string;
  recordId?: string;
  recordTitle?: string;
}): AgentRequestMetadata {
  return {
    disease_profile_id: args.diseaseProfileId,
    disease_name: args.diseaseName,
    record_id: args.recordId,
    record_title: args.recordTitle,
    entry: "agent_page",
  };
}

export function quickPrompts(diseaseName?: string, recordTitle?: string): string[] {
  const scope = recordTitle || diseaseName || "当前病情";
  return [
    `请总结${scope}里最需要关注的异常点`,
    `基于${scope}给我一份复诊前准备清单`,
    `解释${scope}中的关键指标变化趋势`,
    `如果继续随访，建议重点观察哪些信号`,
  ];
}

export function formatRelativeDate(value?: string): string {
  if (!value) {
    return "刚刚";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const diffMinutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (diffMinutes < 1) {
    return "刚刚";
  }
  if (diffMinutes < 60) {
    return `${diffMinutes} 分钟前`;
  }
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours} 小时前`;
  }
  return date.toLocaleDateString("zh-CN");
}

export function tracePreview(event: AgentTraceEvent): string {
  if (event.event === "tool_call") {
    return `${event.tool ?? "工具"} 已开始执行`;
  }
  if (event.event === "tool_result") {
    return `${event.tool ?? "工具"} 已返回结果`;
  }
  return toOptionalText(event.data.message) ?? "Agent 执行异常";
}

export function createSseEventParser(onEvent: (event: AgentSseEvent) => void): {
  push: (chunk: string) => void;
  flush: () => void;
} {
  let buffer = "";

  const emitBlock = (block: string) => {
    const trimmed = block.trim();
    if (!trimmed) {
      return;
    }
    const lines = trimmed.split("\n");
    let eventName = "message";
    const dataLines: string[] = [];
    for (const line of lines) {
      const normalized = line.replace(/\r$/, "");
      if (normalized.startsWith("event:")) {
        eventName = normalized.slice("event:".length).trim() || "message";
      } else if (normalized.startsWith("data:")) {
        dataLines.push(normalized.slice("data:".length).trim());
      }
    }
    if (dataLines.length === 0) {
      return;
    }
    try {
      onEvent({ event: eventName, data: JSON.parse(dataLines.join("\n")) as Record<string, unknown> });
    } catch {
      onEvent({ event: "error", data: { message: "SSE 数据解析失败" } });
    }
  };

  return {
    push(chunk: string) {
      buffer += chunk.replace(/\r\n/g, "\n");
      while (buffer.includes("\n\n")) {
        const splitIndex = buffer.indexOf("\n\n");
        emitBlock(buffer.slice(0, splitIndex));
        buffer = buffer.slice(splitIndex + 2);
      }
    },
    flush() {
      if (buffer.trim()) {
        emitBlock(buffer);
      }
      buffer = "";
    },
  };
}

