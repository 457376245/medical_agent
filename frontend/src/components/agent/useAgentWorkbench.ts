"use client";

import { startTransition, useEffect, useMemo, useRef, useState } from "react";
import { normalizeSessionDetail, normalizeSessionSummary, normalizeStructuredFields, toRequestMetadata } from "./agent-utils";
import type {
  AgentMessage,
  AgentProfile,
  AgentRecord,
  AgentRecordDetail,
  AgentSessionSummary,
  AgentTraceEvent,
  AgentTrendData,
  AgentWorkbenchProps,
} from "./types";
import { createSseEventParser } from "./agent-utils";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";
const AGENT_API_BASE = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://localhost:8090/api/v1";

function mergeSessionSummary(
  current: AgentSessionSummary[],
  next: AgentSessionSummary,
): AgentSessionSummary[] {
  const merged = [next, ...current.filter((item) => item.threadId !== next.threadId)];
  return merged.sort((left, right) => {
    const leftTime = new Date(left.updatedAt ?? left.createdAt ?? 0).getTime();
    const rightTime = new Date(right.updatedAt ?? right.createdAt ?? 0).getTime();
    return rightTime - leftTime;
  });
}

function updateAssistantMessage(
  messages: AgentMessage[],
  assistantId: string,
  updater: (message: AgentMessage) => AgentMessage,
): AgentMessage[] {
  return messages.map((message) => (message.id === assistantId ? updater(message) : message));
}

function appendSystemMessage(messages: AgentMessage[], content: string): AgentMessage[] {
  return [
    ...messages,
    {
      id: `system-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      role: "system",
      content,
      createdAt: new Date().toISOString(),
    },
  ];
}

type UseAgentWorkbenchResult = {
  profiles: AgentProfile[];
  profileId: string;
  selectedProfile?: AgentProfile;
  records: AgentRecord[];
  selectedRecord?: AgentRecord;
  recordId: string;
  sessions: AgentSessionSummary[];
  activeThreadId: string | null;
  activeSessionSummary: AgentSessionSummary | null;
  messages: AgentMessage[];
  draft: string;
  isStreaming: boolean;
  loadingRecords: boolean;
  loadingSessions: boolean;
  loadingConversation: boolean;
  contextLoading: boolean;
  sessionError: string;
  contextError: string;
  streamError: string;
  recordDetail: AgentRecordDetail | null;
  recordAnalysis: string | null;
  trendData: AgentTrendData | null;
  requestMetadata: ReturnType<typeof toRequestMetadata>;
  setDraft: (value: string) => void;
  setProfileId: (nextProfileId: string) => void;
  setRecordId: (nextRecordId: string) => void;
  selectSession: (threadId: string) => Promise<void>;
  startDraftSession: () => void;
  sendPrompt: (prompt?: string) => Promise<void>;
  stopStreaming: () => void;
  retryLastPrompt: () => Promise<void>;
  reloadSessions: () => Promise<void>;
};

export function useAgentWorkbench({
  profiles,
  initialProfileId,
  initialRecordId,
  initialRecords,
}: AgentWorkbenchProps): UseAgentWorkbenchResult {
  const [profileId, setProfileIdState] = useState(initialProfileId ?? "");
  const [recordId, setRecordIdState] = useState(initialRecordId ?? "");
  const [records, setRecords] = useState<AgentRecord[]>(initialRecords);
  const [sessions, setSessions] = useState<AgentSessionSummary[]>([]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [activeSessionSummary, setActiveSessionSummary] = useState<AgentSessionSummary | null>(null);
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [loadingRecords, setLoadingRecords] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [sessionError, setSessionError] = useState("");
  const [contextError, setContextError] = useState("");
  const [streamError, setStreamError] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [recordDetails, setRecordDetails] = useState<Record<string, AgentRecordDetail | null | undefined>>({});
  const [recordAnalyses, setRecordAnalyses] = useState<Record<string, string | null | undefined>>({});
  const [trendDataMap, setTrendDataMap] = useState<Record<string, AgentTrendData | null | undefined>>({});

  const abortRef = useRef<AbortController | null>(null);
  const lastPromptRef = useRef("");
  const hydratedInitialRecordsRef = useRef(Boolean(initialProfileId));

  const selectedProfile = useMemo(
    () => profiles.find((item) => item.profileId === profileId),
    [profileId, profiles],
  );
  const selectedRecord = useMemo(
    () => records.find((item) => item.id === recordId),
    [recordId, records],
  );
  const recordDetail = recordId ? recordDetails[recordId] ?? null : null;
  const recordAnalysis = recordId ? recordAnalyses[recordId] ?? null : null;
  const trendData = recordId ? trendDataMap[recordId] ?? null : null;

  const requestMetadata = useMemo(
    () =>
      toRequestMetadata({
        diseaseProfileId: profileId || undefined,
        diseaseName: selectedProfile?.diseaseName,
        recordId: recordId || undefined,
        recordTitle: selectedRecord?.title,
      }),
    [profileId, recordId, selectedProfile?.diseaseName, selectedRecord?.title],
  );

  const contextLoading = useMemo(() => {
    if (!recordId) {
      return false;
    }
    const detailPending = recordDetails[recordId] === undefined;
    const trendPending = trendDataMap[recordId] === undefined;
    const analysisPending =
      recordDetails[recordId] &&
      (recordDetails[recordId]?.fields.length ?? 0) > 0 &&
      recordAnalyses[recordId] === undefined;
    return Boolean(detailPending || trendPending || analysisPending);
  }, [recordAnalyses, recordDetails, recordId, trendDataMap]);

  const reloadSessions = async () => {
    if (!profileId) {
      setSessions([]);
      return;
    }

    setLoadingSessions(true);
    setSessionError("");
    try {
      const response = await fetch(`${AGENT_API_BASE}/sessions?disease_profile_id=${encodeURIComponent(profileId)}`, {
        cache: "no-store",
      });
      if (!response.ok) {
        throw new Error("加载会话列表失败，请稍后重试。");
      }
      const payload = await response.json();
      const nextSessions = Array.isArray(payload.sessions)
        ? payload.sessions.map(normalizeSessionSummary)
        : [];
      startTransition(() => {
        setSessions(nextSessions);
      });
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "加载会话列表失败，请稍后重试。");
    } finally {
      setLoadingSessions(false);
    }
  };

  useEffect(() => {
    void reloadSessions();
  }, [profileId]);

  useEffect(() => {
    let cancelled = false;
    if (!profileId) {
      setRecords([]);
      return;
    }

    if (hydratedInitialRecordsRef.current && profileId === initialProfileId) {
      hydratedInitialRecordsRef.current = false;
      setRecords(initialRecords);
      return;
    }

    const loadRecords = async () => {
      setLoadingRecords(true);
      try {
        const response = await fetch(`${API_BASE}/disease-profiles/${encodeURIComponent(profileId)}/records`, {
          cache: "no-store",
        });
        if (!response.ok) {
          throw new Error("加载疾病报告失败，请稍后重试。");
        }
        const payload = await response.json();
        const nextRecords: AgentRecord[] = Array.isArray(payload?.data?.records)
          ? payload.data.records.map((item: Record<string, unknown>) => ({
              id: String(item.id ?? ""),
              title: String(item.title ?? "未命名报告"),
              recordDate: String(item.recordDate ?? item.record_date ?? ""),
              sourceType: String(item.sourceType ?? item.source_type ?? "UPLOAD"),
            }))
          : [];
        if (!cancelled) {
          startTransition(() => {
            setRecords(nextRecords);
            if (!nextRecords.some((item) => item.id === recordId)) {
              setRecordIdState("");
            }
          });
        }
      } catch (error) {
        if (!cancelled) {
          setContextError(error instanceof Error ? error.message : "加载疾病报告失败，请稍后重试。");
        }
      } finally {
        if (!cancelled) {
          setLoadingRecords(false);
        }
      }
    };

    void loadRecords();
    return () => {
      cancelled = true;
    };
  }, [API_BASE, initialProfileId, initialRecords, profileId, recordId]);

  useEffect(() => {
    let cancelled = false;
    if (!recordId || recordDetails[recordId] !== undefined) {
      return;
    }
    const loadRecordDetail = async () => {
      setContextError("");
      try {
        const response = await fetch(`${API_BASE}/records/${encodeURIComponent(recordId)}`, { cache: "no-store" });
        if (!response.ok) {
          throw new Error("加载报告详情失败，请稍后重试。");
        }
        const payload = await response.json();
        const detail = payload?.data;
        const normalized: AgentRecordDetail = {
          recordId: String(detail?.recordId ?? recordId),
          summary: String(detail?.summary ?? ""),
          parseStatus: String(detail?.parseStatus ?? "NOT_PARSED"),
          fields: normalizeStructuredFields(detail?.structuredResult?.payload),
        };
        if (!cancelled) {
          setRecordDetails((prev) => ({ ...prev, [recordId]: normalized }));
        }
      } catch (error) {
        if (!cancelled) {
          setRecordDetails((prev) => ({ ...prev, [recordId]: null }));
          setContextError(error instanceof Error ? error.message : "加载报告详情失败，请稍后重试。");
        }
      }
    };
    void loadRecordDetail();
    return () => {
      cancelled = true;
    };
  }, [recordDetails, recordId]);

  useEffect(() => {
    let cancelled = false;
    if (!recordId || trendDataMap[recordId] !== undefined) {
      return;
    }
    const loadTrend = async () => {
      try {
        const response = await fetch(`${API_BASE}/records/${encodeURIComponent(recordId)}/trend?limit=3`, { cache: "no-store" });
        if (!response.ok) {
          throw new Error("加载趋势摘要失败，请稍后重试。");
        }
        const payload = await response.json();
        const data = payload?.data;
        const normalized: AgentTrendData = {
          recordId: String(data?.recordId ?? recordId),
          sourceType: String(data?.sourceType ?? ""),
          diseaseProfileId: String(data?.diseaseProfileId ?? ""),
          limit: Number(data?.limit ?? 3),
          snapshots: Array.isArray(data?.snapshots)
            ? data.snapshots.map((snapshot: Record<string, unknown>) => ({
                recordId: String(snapshot.recordId ?? ""),
                recordDate: String(snapshot.recordDate ?? ""),
                title: String(snapshot.title ?? "未命名报告"),
                sourceType: String(snapshot.sourceType ?? ""),
                fields: Array.isArray(snapshot.fields)
                  ? snapshot.fields
                      .map((field: Record<string, unknown>) => {
                        const name = String(field.name ?? "").trim();
                        const value = String(field.value ?? "").trim();
                        if (!name || !value) {
                          return null;
                        }
                        return {
                          name,
                          value,
                          unit: String(field.unit ?? "").trim() || undefined,
                          referenceRange: String(field.referenceRange ?? field.reference_range ?? "").trim() || undefined,
                        };
                      })
                      .filter((field): field is NonNullable<typeof field> => Boolean(field))
                  : [],
              }))
            : [],
        };
        if (!cancelled) {
          setTrendDataMap((prev) => ({ ...prev, [recordId]: normalized }));
        }
      } catch {
        if (!cancelled) {
          setTrendDataMap((prev) => ({ ...prev, [recordId]: null }));
        }
      }
    };
    void loadTrend();
    return () => {
      cancelled = true;
    };
  }, [recordId, trendDataMap]);

  useEffect(() => {
    let cancelled = false;
    const detail = recordId ? recordDetails[recordId] : null;
    if (!recordId || detail === undefined || recordAnalyses[recordId] !== undefined) {
      return;
    }
    if (!detail || detail.fields.length === 0 || detail.parseStatus.toUpperCase() !== "SUCCESS") {
      setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
      return;
    }

    const loadAnalysis = async () => {
      try {
        const response = await fetch(`${API_BASE}/records/${encodeURIComponent(recordId)}/analysis`, { cache: "no-store" });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
          if (response.status !== 409 && !cancelled) {
            setContextError(String(payload?.message ?? "加载 AI 分析失败，请稍后重试。"));
          }
          return;
        }
        const content = String(payload?.data?.content ?? "").trim();
        if (!cancelled) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: content || null }));
        }
      } catch {
        if (!cancelled) {
          setRecordAnalyses((prev) => ({ ...prev, [recordId]: null }));
        }
      }
    };
    void loadAnalysis();
    return () => {
      cancelled = true;
    };
  }, [recordAnalyses, recordDetails, recordId]);

  const startDraftSession = () => {
    if (isStreaming) {
      return;
    }
    setActiveThreadId(null);
    setActiveSessionSummary(null);
    setMessages([]);
    setStreamError("");
  };

  const setProfileId = (nextProfileId: string) => {
    if (nextProfileId === profileId) {
      return;
    }
    setProfileIdState(nextProfileId);
    setRecordIdState("");
    setStreamError("");
    if (messages.length > 0) {
      const nextProfile = profiles.find((item) => item.profileId === nextProfileId);
      setMessages((prev) => appendSystemMessage(prev, `对话上下文已切换到 ${nextProfile?.diseaseName ?? "新疾病档案"}。`));
    }
  };

  const setRecordId = (nextRecordId: string) => {
    if (nextRecordId === recordId) {
      return;
    }
    setRecordIdState(nextRecordId);
    setStreamError("");
    if (messages.length > 0) {
      const nextRecord = records.find((item) => item.id === nextRecordId);
      setMessages((prev) =>
        appendSystemMessage(
          prev,
          nextRecord
            ? `对话上下文已切换到 ${selectedProfile?.diseaseName ?? "当前疾病"} / ${nextRecord.title}${nextRecord.recordDate ? `（${nextRecord.recordDate}）` : ""}。`
            : "对话上下文已切换到疾病级别视角。",
        ),
      );
    }
  };

  const selectSession = async (threadId: string) => {
    setActiveThreadId(threadId);
    setLoadingConversation(true);
    setStreamError("");
    try {
      const response = await fetch(`${AGENT_API_BASE}/sessions/${encodeURIComponent(threadId)}`, {
        cache: "no-store",
      });
      if (!response.ok) {
        throw new Error("加载会话详情失败，请稍后重试。");
      }
      const payload = await response.json();
      const detail = normalizeSessionDetail(payload);
      startTransition(() => {
        setMessages(detail.messages);
        setActiveThreadId(detail.threadId);
        setActiveSessionSummary((prev) => ({
          ...(prev ?? {}),
          ...normalizeSessionSummary(payload),
          threadId: detail.threadId,
          title: detail.title,
          turnCount: detail.turnCount,
        }));
      });
      if (detail.diseaseProfileId && detail.diseaseProfileId !== profileId) {
        setProfileIdState(detail.diseaseProfileId);
      }
      if (detail.recordId) {
        setRecordIdState(detail.recordId);
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "加载会话详情失败，请稍后重试。");
    } finally {
      setLoadingConversation(false);
    }
  };

  const sendPrompt = async (explicitPrompt?: string) => {
    const prompt = (explicitPrompt ?? draft).trim();
    if (!prompt || isStreaming || !profileId) {
      return;
    }

    const userId = `user-${Date.now()}`;
    const assistantId = `assistant-${Date.now()}`;
    const sentAt = new Date().toISOString();
    const currentProfileId = profileId;
    const currentSummary = activeSessionSummary;
    let resolvedThreadId = activeThreadId;
    let assistantContent = "";

    lastPromptRef.current = prompt;
    setDraft("");
    setIsStreaming(true);
    setStreamError("");
    setMessages((prev) => [
      ...prev,
      {
        id: userId,
        role: "user",
        content: prompt,
        createdAt: sentAt,
      },
      {
        id: assistantId,
        role: "assistant",
        content: "",
        createdAt: sentAt,
        traceEvents: [],
        isStreaming: true,
      },
    ]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const response = await fetch(`${AGENT_API_BASE}/chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          thread_id: activeThreadId ?? undefined,
          message: prompt,
          metadata: requestMetadata,
        }),
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error("Agent 对话服务暂不可用，请稍后重试。");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parser = createSseEventParser((event) => {
        if (event.event === "session") {
          resolvedThreadId = typeof event.data.thread_id === "string" ? event.data.thread_id : resolvedThreadId;
          if (resolvedThreadId) {
            const nextSummary: AgentSessionSummary = {
              threadId: resolvedThreadId,
              diseaseProfileId: currentProfileId,
              diseaseName: selectedProfile?.diseaseName,
              recordId: selectedRecord?.id,
              recordTitle: selectedRecord?.title,
              recordDate: selectedRecord?.recordDate,
              sourceType: selectedRecord?.sourceType,
              title: currentSummary?.title || prompt.slice(0, 28) || "新对话",
              lastUserMessage: prompt,
              lastMessagePreview: "正在生成回复...",
              turnCount: (currentSummary?.turnCount ?? 0) + 1,
              createdAt: currentSummary?.createdAt ?? sentAt,
              updatedAt: new Date().toISOString(),
              isStreaming: true,
            };
            setActiveThreadId(resolvedThreadId);
            setActiveSessionSummary(nextSummary);
            setSessions((prev) => mergeSessionSummary(prev, nextSummary));
          }
          return;
        }

        if (event.event === "token") {
          const token = typeof event.data.content === "string" ? event.data.content : "";
          assistantContent += token;
          setMessages((prev) =>
            updateAssistantMessage(prev, assistantId, (message) => ({
              ...message,
              content: `${message.content}${token}`,
            })),
          );
          return;
        }

        if (event.event === "tool_call" || event.event === "tool_result" || event.event === "error") {
          const traceEvent: AgentTraceEvent = {
            event: event.event as AgentTraceEvent["event"],
            tool: typeof event.data.tool === "string" ? event.data.tool : undefined,
            data: { ...event.data },
            createdAt: new Date().toISOString(),
          };
          setMessages((prev) =>
            updateAssistantMessage(prev, assistantId, (message) => ({
              ...message,
              traceEvents: [...(message.traceEvents ?? []), traceEvent],
              errorMessage:
                traceEvent.event === "error" && typeof traceEvent.data.message === "string"
                  ? traceEvent.data.message
                  : message.errorMessage,
            })),
          );
          if (traceEvent.event === "error" && typeof traceEvent.data.message === "string") {
            setStreamError(traceEvent.data.message);
          }
          return;
        }

        if (event.event === "done") {
          const doneContent = typeof event.data.content === "string" ? event.data.content : undefined;
          assistantContent = doneContent ?? assistantContent;
          setMessages((prev) =>
            updateAssistantMessage(prev, assistantId, (message) => ({
              ...message,
              content: assistantContent || message.content,
              isStreaming: false,
            })),
          );
          const preview = assistantContent;
          if (resolvedThreadId) {
            const nextSummary: AgentSessionSummary = {
              threadId: resolvedThreadId,
              diseaseProfileId: currentProfileId,
              diseaseName: selectedProfile?.diseaseName,
              recordId: selectedRecord?.id,
              recordTitle: selectedRecord?.title,
              recordDate: selectedRecord?.recordDate,
              sourceType: selectedRecord?.sourceType,
              title: currentSummary?.title || prompt.slice(0, 28) || "新对话",
              lastUserMessage: prompt,
              lastAssistantMessage: preview,
              lastMessagePreview: preview || prompt,
              turnCount: (currentSummary?.turnCount ?? 0) + 1,
              createdAt: currentSummary?.createdAt ?? sentAt,
              updatedAt: new Date().toISOString(),
              isStreaming: false,
            };
            setActiveSessionSummary(nextSummary);
            setSessions((prev) => mergeSessionSummary(prev, nextSummary));
          }
        }
      });

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        parser.push(decoder.decode(value, { stream: true }));
      }
      parser.push(decoder.decode());
      parser.flush();
    } catch (error) {
      if ((error as Error).name !== "AbortError") {
        const message = error instanceof Error ? error.message : "发送消息失败，请稍后重试。";
        setStreamError(message);
        setMessages((prev) =>
          updateAssistantMessage(prev, assistantId, (item) => ({
            ...item,
            isStreaming: false,
            errorMessage: message,
            traceEvents: [
              ...(item.traceEvents ?? []),
              {
                event: "error",
                data: { message },
                createdAt: new Date().toISOString(),
              },
            ],
          })),
        );
      } else {
        setMessages((prev) =>
          updateAssistantMessage(prev, assistantId, (item) => ({
            ...item,
            isStreaming: false,
          })),
        );
      }
    } finally {
      abortRef.current = null;
      setIsStreaming(false);
      await reloadSessions();
    }
  };

  const stopStreaming = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    setActiveSessionSummary((prev) => (prev ? { ...prev, isStreaming: false } : prev));
  };

  const retryLastPrompt = async () => {
    if (!lastPromptRef.current || isStreaming) {
      return;
    }
    await sendPrompt(lastPromptRef.current);
  };

  const visibleSessions = useMemo(() => {
    if (!activeSessionSummary) {
      return sessions;
    }
    if (
      activeSessionSummary.diseaseProfileId &&
      profileId &&
      activeSessionSummary.diseaseProfileId !== profileId
    ) {
      return sessions;
    }
    return mergeSessionSummary(sessions, activeSessionSummary);
  }, [activeSessionSummary, profileId, sessions]);

  return {
    profiles,
    profileId,
    selectedProfile,
    records,
    selectedRecord,
    recordId,
    sessions: visibleSessions,
    activeThreadId,
    activeSessionSummary,
    messages,
    draft,
    isStreaming,
    loadingRecords,
    loadingSessions,
    loadingConversation,
    contextLoading,
    sessionError,
    contextError,
    streamError,
    recordDetail,
    recordAnalysis,
    trendData,
    requestMetadata,
    setDraft,
    setProfileId,
    setRecordId,
    selectSession,
    startDraftSession,
    sendPrompt,
    stopStreaming,
    retryLastPrompt,
    reloadSessions,
  };
}
