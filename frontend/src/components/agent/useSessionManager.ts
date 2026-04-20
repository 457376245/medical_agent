"use client";

import { startTransition, useEffect, useMemo, useState } from "react";
import { agentFetch } from "../../lib/api";
import { normalizeSessionDetail, normalizeSessionSummary } from "./agent-utils";
import type { AgentMessage, AgentRequestMetadata, AgentSessionSummary } from "./types";

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

export type UseSessionManagerResult = {
  sessions: AgentSessionSummary[];
  activeThreadId: string | null;
  activeSessionSummary: AgentSessionSummary | null;
  loadingSessions: boolean;
  loadingConversation: boolean;
  sessionError: string;
  setActiveThreadId: React.Dispatch<React.SetStateAction<string | null>>;
  setActiveSessionSummary: React.Dispatch<React.SetStateAction<AgentSessionSummary | null>>;
  setSessions: React.Dispatch<React.SetStateAction<AgentSessionSummary[]>>;
  mergeSummary: (summary: AgentSessionSummary) => void;
  selectSession: (threadId: string) => Promise<{
    diseaseProfileId?: string;
    recordId?: string;
    messages: AgentMessage[];
    lastTurnMetadata?: AgentRequestMetadata;
  } | null>;
  startDraftSession: () => void;
  reloadSessions: () => Promise<void>;
  deleteSession: (threadId: string) => Promise<void>;
  renameSession: (threadId: string, newTitle: string) => Promise<void>;
};

export function useSessionManager(
  profileId: string,
  isStreaming: boolean,
  patientId?: string,
): UseSessionManagerResult {
  const [sessions, setSessions] = useState<AgentSessionSummary[]>([]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [activeSessionSummary, setActiveSessionSummary] = useState<AgentSessionSummary | null>(null);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [sessionError, setSessionError] = useState("");

  // Clear sessions when patient changes
  useEffect(() => {
    setSessions([]);
    setActiveThreadId(null);
    setActiveSessionSummary(null);
    setSessionError("");
  }, [patientId]);

  const reloadSessions = async () => {
    if (!profileId) {
      setSessions([]);
      return;
    }

    setLoadingSessions(true);
    setSessionError("");
    try {
      const response = await agentFetch(`/sessions?disease_profile_id=${encodeURIComponent(profileId)}`);
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

  const startDraftSession = () => {
    if (isStreaming) return;
    setActiveThreadId(null);
    setActiveSessionSummary(null);
  };

  const selectSession = async (threadId: string): Promise<{
    diseaseProfileId?: string;
    recordId?: string;
    messages: AgentMessage[];
    lastTurnMetadata?: AgentRequestMetadata;
  } | null> => {
    setActiveThreadId(threadId);
    setLoadingConversation(true);
    try {
      const response = await agentFetch(`/sessions/${encodeURIComponent(threadId)}`);
      if (!response.ok) {
        throw new Error("加载会话详情失败，请稍后重试。");
      }
      const payload = await response.json();
      const detail = normalizeSessionDetail(payload);
      startTransition(() => {
        setActiveThreadId(detail.threadId);
        setActiveSessionSummary((prev) => ({
          ...(prev ?? {}),
          ...normalizeSessionSummary(payload),
          threadId: detail.threadId,
          title: detail.title,
          turnCount: detail.turnCount,
        }));
      });
      return {
        diseaseProfileId: detail.diseaseProfileId,
        recordId: detail.recordId,
        messages: detail.messages,
        lastTurnMetadata: detail.turns[detail.turns.length - 1]?.metadata,
      };
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "加载会话详情失败，请稍后重试。");
      return null;
    } finally {
      setLoadingConversation(false);
    }
  };

  const mergeSummary = (summary: AgentSessionSummary) => {
    setSessions((prev) => mergeSessionSummary(prev, summary));
  };

  const deleteSession = async (threadId: string) => {
    try {
      const response = await agentFetch(`/sessions/${encodeURIComponent(threadId)}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error("删除会话失败");
      setSessions((prev) => prev.filter((s) => s.threadId !== threadId));
      if (activeThreadId === threadId) {
        setActiveThreadId(null);
        setActiveSessionSummary(null);
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "删除会话失败");
    }
  };

  const renameSession = async (threadId: string, newTitle: string) => {
    try {
      const response = await agentFetch(`/sessions/${encodeURIComponent(threadId)}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title: newTitle }),
      });
      if (!response.ok) throw new Error("重命名会话失败");
      setSessions((prev) =>
        prev.map((s) => (s.threadId === threadId ? { ...s, title: newTitle } : s)),
      );
      if (activeThreadId === threadId) {
        setActiveSessionSummary((prev) => (prev ? { ...prev, title: newTitle } : prev));
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : "重命名会话失败");
    }
  };

  const visibleSessions = useMemo(() => {
    if (!activeSessionSummary) return sessions;
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
    sessions: visibleSessions,
    activeThreadId,
    activeSessionSummary,
    loadingSessions,
    loadingConversation,
    sessionError,
    setActiveThreadId,
    setActiveSessionSummary,
    setSessions,
    mergeSummary,
    selectSession,
    startDraftSession,
    reloadSessions,
    deleteSession,
    renameSession,
  };
}
