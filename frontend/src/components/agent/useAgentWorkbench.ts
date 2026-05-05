"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { agentFetch } from "../../lib/api";
import { createSseEventParser, toRequestMetadata, WORKFLOW_LABELS } from "./agent-utils";
import type {
  AgentAudience,
  AgentMessage,
  AgentProfile,
  AgentSessionSummary,
  AgentTraceEvent,
  AgentWorkflow,
  AgentWorkbenchProps,
} from "./types";
import { useRecordContext } from "./useRecordContext";
import { useCareSupport } from "./useCareSupport";
import { useSessionManager } from "./useSessionManager";

const DEFAULT_AUDIENCE: AgentAudience = "patient";

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

function updateAssistantMessage(
  messages: AgentMessage[],
  assistantId: string,
  updater: (message: AgentMessage) => AgentMessage,
): AgentMessage[] {
  return messages.map((message) => (message.id === assistantId ? updater(message) : message));
}

export type UseAgentWorkbenchResult = {
  profiles: AgentProfile[];
  profileId: string;
  selectedProfile?: AgentProfile;
  records: ReturnType<typeof useRecordContext>["records"];
  selectedRecord?: ReturnType<typeof useRecordContext>["records"][number];
  recordId: string;
  sourceType: string;
  sessions: ReturnType<typeof useSessionManager>["sessions"];
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
  recordDetail: ReturnType<typeof useRecordContext>["recordDetail"];
  recordAnalysis: ReturnType<typeof useRecordContext>["recordAnalysis"];
  trendData: ReturnType<typeof useRecordContext>["trendData"];
  requestMetadata: ReturnType<typeof toRequestMetadata>;
  workflow: AgentWorkflow;
  setDraft: (value: string) => void;
  setWorkflow: (nextWorkflow: AgentWorkflow) => void;
  setProfileId: (nextProfileId: string) => void;
  setRecordId: (nextRecordId: string) => void;
  setSourceType: (nextSourceType: string) => void;
  selectSession: (threadId: string) => Promise<void>;
  startDraftSession: () => void;
  sendPrompt: (prompt?: string) => Promise<void>;
  stopStreaming: () => void;
  retryLastPrompt: () => Promise<void>;
  reloadSessions: () => Promise<void>;
  deleteSession: (threadId: string) => Promise<void>;
  renameSession: (threadId: string, newTitle: string) => Promise<void>;
  careProfile: ReturnType<typeof useCareSupport>["careProfile"];
  followUpTasks: ReturnType<typeof useCareSupport>["followUpTasks"];
  symptoms: ReturnType<typeof useCareSupport>["symptoms"];
  riskOverview: ReturnType<typeof useCareSupport>["riskOverview"];
  loadingCare: boolean;
  loadingRisk: boolean;
  careError: string;
  saveCareProfile: ReturnType<typeof useCareSupport>["saveCareProfile"];
  createFollowUpTask: ReturnType<typeof useCareSupport>["createFollowUpTask"];
  updateFollowUpTask: ReturnType<typeof useCareSupport>["updateFollowUpTask"];
  createSymptomLog: ReturnType<typeof useCareSupport>["createSymptomLog"];
};

export function useAgentWorkbench({
  profiles,
  initialProfileId,
  initialRecordId,
  initialRecords,
  patientId,
}: AgentWorkbenchProps): UseAgentWorkbenchResult {
  const [profileId, setProfileIdState] = useState(initialProfileId ?? "");
  const [recordId, setRecordIdState] = useState(initialRecordId ?? "");
  const [sourceType, setSourceTypeState] = useState("");
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [workflow, setWorkflowState] = useState<AgentWorkflow>("report_interpretation");
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamError, setStreamError] = useState("");

  const abortRef = useRef<AbortController | null>(null);
  const lastPromptRef = useRef("");

  // Clear state when patient changes
  useEffect(() => {
    setProfileIdState("");
    setRecordIdState("");
    setSourceTypeState("");
    setMessages([]);
    setDraft("");
    setWorkflowState("report_interpretation");
    setStreamError("");
  }, [patientId]);

  // Delegate record/context loading
  const context = useRecordContext(profileId, recordId, initialProfileId, initialRecords, patientId);
  const careSupport = useCareSupport(patientId, profileId || undefined, recordId || undefined);

  // Delegate session management
  const sessionMgr = useSessionManager(profileId, isStreaming, patientId);

  const selectedProfile = useMemo(
    () => profiles.find((item) => item.profileId === profileId),
    [profileId, profiles],
  );
  const selectedRecord = useMemo(
    () => context.records.find((item) => item.id === recordId),
    [recordId, context.records],
  );

  const requestMetadata = useMemo(
    () =>
      toRequestMetadata({
        diseaseProfileId: profileId || undefined,
        diseaseName: selectedProfile?.diseaseName,
        recordId: recordId || undefined,
        recordTitle: selectedRecord?.title,
        recordDate: selectedRecord?.recordDate,
        sourceType: sourceType || undefined,
        workflow,
        urgencyLevel: careSupport.riskOverview.riskLevel,
        audience: DEFAULT_AUDIENCE,
      }),
    [careSupport.riskOverview.riskLevel, profileId, recordId, selectedProfile?.diseaseName, selectedRecord?.recordDate, selectedRecord?.title, sourceType, workflow],
  );

  const setProfileId = (nextProfileId: string) => {
    if (nextProfileId === profileId) return;
    setProfileIdState(nextProfileId);
    setRecordIdState("");
    setStreamError("");
    if (messages.length > 0) {
      const nextProfile = profiles.find((item) => item.profileId === nextProfileId);
      setMessages((prev) => appendSystemMessage(prev, `对话上下文已切换到 ${nextProfile?.diseaseName ?? "新疾病档案"}。`));
    }
  };

  const setRecordId = (nextRecordId: string) => {
    if (nextRecordId === recordId) return;
    setRecordIdState(nextRecordId);
    setStreamError("");
    if (messages.length > 0) {
      const nextRecord = context.records.find((item) => item.id === nextRecordId);
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

  const setSourceType = (nextSourceType: string) => {
    if (nextSourceType === sourceType) return;
    setSourceTypeState(nextSourceType);
    setRecordIdState("");
    setStreamError("");
    if (messages.length > 0) {
      setMessages((prev) =>
        appendSystemMessage(
          prev,
          nextSourceType
            ? `对话上下文已定位到分类：${nextSourceType}。`
            : "对话上下文已切换为所有分类 / 疾病全局。",
        ),
      );
    }
  };

  const setWorkflow = (nextWorkflow: AgentWorkflow) => {
    if (nextWorkflow === workflow) return;
    setWorkflowState(nextWorkflow);
    setStreamError("");
    if (messages.length > 0) {
      setMessages((prev) => appendSystemMessage(prev, `对话工作流已切换到 ${WORKFLOW_LABELS[nextWorkflow]}。`));
    }
  };

  const selectSession = async (threadId: string) => {
    setStreamError("");
    const result = await sessionMgr.selectSession(threadId);
    if (!result) return;

    setMessages(result.messages);
    if (result.diseaseProfileId && result.diseaseProfileId !== profileId) {
      setProfileIdState(result.diseaseProfileId);
    }
    if (result.recordId) {
      setRecordIdState(result.recordId);
    }
    const lastTurnMetadata = result.lastTurnMetadata;
    const nextWorkflow = lastTurnMetadata?.workflow;
    if (
      nextWorkflow === "report_interpretation"
      || nextWorkflow === "follow_up_prep"
      || nextWorkflow === "medication_review"
      || nextWorkflow === "abnormal_reasoning"
    ) {
      setWorkflowState(nextWorkflow);
    }
  };

  const startDraftSession = () => {
    sessionMgr.startDraftSession();
    setMessages([]);
    setStreamError("");
  };

  const sendPrompt = async (explicitPrompt?: string) => {
    const prompt = (explicitPrompt ?? draft).trim();
    if (!prompt || isStreaming || !profileId) return;

    const userId = `user-${Date.now()}`;
    const assistantId = `assistant-${Date.now()}`;
    const sentAt = new Date().toISOString();
    const currentProfileId = profileId;
    const currentSummary = sessionMgr.activeSessionSummary;
    let resolvedThreadId = sessionMgr.activeThreadId;
    let assistantContent = "";

    lastPromptRef.current = prompt;
    setDraft("");
    setIsStreaming(true);
    setStreamError("");
    setMessages((prev) => [
      ...prev,
      { id: userId, role: "user", content: prompt, createdAt: sentAt },
      { id: assistantId, role: "assistant", content: "", createdAt: sentAt, traceEvents: [], isStreaming: true },
    ]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const response = await agentFetch("/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          thread_id: sessionMgr.activeThreadId ?? undefined,
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
            sessionMgr.setActiveThreadId(resolvedThreadId);
            sessionMgr.setActiveSessionSummary(nextSummary);
            sessionMgr.mergeSummary(nextSummary);
          }
          return;
        }

        if (event.event === "token") {
          const token = typeof event.data.content === "string" ? event.data.content : "";
          assistantContent += token;
          setMessages((prev) =>
            updateAssistantMessage(prev, assistantId, (m) => ({ ...m, content: `${m.content}${token}` })),
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
            updateAssistantMessage(prev, assistantId, (m) => ({
              ...m,
              traceEvents: [...(m.traceEvents ?? []), traceEvent],
              errorMessage:
                traceEvent.event === "error" && typeof traceEvent.data.message === "string"
                  ? traceEvent.data.message
                  : m.errorMessage,
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
            updateAssistantMessage(prev, assistantId, (m) => ({
              ...m,
              content: assistantContent || m.content,
              isStreaming: false,
            })),
          );
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
              lastAssistantMessage: assistantContent,
              lastMessagePreview: assistantContent || prompt,
              turnCount: (currentSummary?.turnCount ?? 0) + 1,
              createdAt: currentSummary?.createdAt ?? sentAt,
              updatedAt: new Date().toISOString(),
              isStreaming: false,
            };
            sessionMgr.setActiveSessionSummary(nextSummary);
            sessionMgr.mergeSummary(nextSummary);
          }
        }
      });

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
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
              { event: "error", data: { message }, createdAt: new Date().toISOString() },
            ],
          })),
        );
      } else {
        setMessages((prev) =>
          updateAssistantMessage(prev, assistantId, (item) => ({ ...item, isStreaming: false })),
        );
      }
    } finally {
      abortRef.current = null;
      setIsStreaming(false);
      await sessionMgr.reloadSessions();
    }
  };

  const stopStreaming = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    sessionMgr.setActiveSessionSummary((prev) => (prev ? { ...prev, isStreaming: false } : prev));
  };

  const retryLastPrompt = async () => {
    if (!lastPromptRef.current || isStreaming) return;
    await sendPrompt(lastPromptRef.current);
  };

  const deleteSession = async (threadId: string) => {
    const wasActive = sessionMgr.activeThreadId === threadId;
    await sessionMgr.deleteSession(threadId);
    if (wasActive) {
      setMessages([]);
      setStreamError("");
    }
  };

  return {
    profiles,
    profileId,
    selectedProfile,
    records: context.records,
    selectedRecord,
    recordId,
    sourceType,
    sessions: sessionMgr.sessions,
    activeThreadId: sessionMgr.activeThreadId,
    activeSessionSummary: sessionMgr.activeSessionSummary,
    messages,
    draft,
    isStreaming,
    loadingRecords: context.loadingRecords,
    loadingSessions: sessionMgr.loadingSessions,
    loadingConversation: sessionMgr.loadingConversation,
    contextLoading: context.contextLoading,
    sessionError: sessionMgr.sessionError,
    contextError: context.contextError,
    streamError,
    recordDetail: context.recordDetail,
    recordAnalysis: context.recordAnalysis,
    trendData: context.trendData,
    requestMetadata,
    workflow,
    setDraft,
    setWorkflow,
    setProfileId,
    setRecordId,
    setSourceType,
    selectSession,
    startDraftSession,
    sendPrompt,
    stopStreaming,
    retryLastPrompt,
    reloadSessions: sessionMgr.reloadSessions,
    deleteSession,
    renameSession: sessionMgr.renameSession,
    careProfile: careSupport.careProfile,
    followUpTasks: careSupport.followUpTasks,
    symptoms: careSupport.symptoms,
    riskOverview: careSupport.riskOverview,
    loadingCare: careSupport.loadingCare,
    loadingRisk: careSupport.loadingRisk,
    careError: careSupport.careError,
    saveCareProfile: careSupport.saveCareProfile,
    createFollowUpTask: careSupport.createFollowUpTask,
    updateFollowUpTask: careSupport.updateFollowUpTask,
    createSymptomLog: careSupport.createSymptomLog,
  };
}
