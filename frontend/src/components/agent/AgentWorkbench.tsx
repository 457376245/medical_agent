"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppSelect, type AppSelectOption } from "../common/AppSelect";
import { formatRelativeDate, getSessionDisplayTitle, quickPrompts, severityLabel, WORKFLOW_LABELS } from "./agent-utils";
import type { AgentWorkbenchProps } from "./types";
import { useAgentWorkbench } from "./useAgentWorkbench";
import { AgentMessageBubble } from "./AgentMessageBubble";
import { AgentWorkflowBar } from "./AgentWorkflowBar";
import { AgentContextPanel } from "./AgentContextPanel";
import { CareProfilePanel } from "./CareProfilePanel";
import { FollowUpTasksPanel } from "./FollowUpTasksPanel";
import { RiskOverviewPanel } from "./RiskOverviewPanel";
import { SymptomLogPanel } from "./SymptomLogPanel";
import { ArrowLeft, MessageSquare, ClipboardList, Search, MoreHorizontal, Pencil, Trash2, ShieldCheck } from "lucide-react";



function SkeletonLine({ width = "100%" }: { width?: string }) {
  return <div className="agent-skeleton-line" style={{ width }} />;
}

function SessionSkeleton() {
  return (
    <div className="agent-session-skeleton" aria-hidden="true">
      {[1, 2, 3].map((i) => (
        <div key={i} className="agent-session-skeleton-item">
          <SkeletonLine width="70%" />
          <SkeletonLine width="40%" />
        </div>
      ))}
    </div>
  );
}

function ChatSkeleton() {
  return (
    <div className="agent-chat-skeleton" role="status" aria-label="加载会话中">
      {[1, 2, 3].map((i) => (
        <div key={i} className={`agent-chat-skeleton-bubble ${i % 2 === 0 ? "right" : "left"}`}>
          <SkeletonLine width={i % 2 === 0 ? "60%" : "80%"} />
          <SkeletonLine width={i % 2 === 0 ? "40%" : "55%"} />
        </div>
      ))}
    </div>
  );
}

export function AgentWorkbench(props: AgentWorkbenchProps) {
  const [mobilePanel, setMobilePanel] = useState<"sessions" | "context" | null>(null);
  const [sessionSearch, setSessionSearch] = useState("");
  const [menuThreadId, setMenuThreadId] = useState<string | null>(null);
  const [renamingThreadId, setRenamingThreadId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const workbench = useAgentWorkbench(props);
  const chatScrollRef = useRef<HTMLDivElement>(null);

  const sourceTypes = useMemo(() => {
    return Array.from(new Set(workbench.records.map((r) => r.sourceType))).filter(Boolean);
  }, [workbench.records]);

  const profileOptions = useMemo<AppSelectOption[]>(
    () => [
      { value: "", label: "请选择疾病..." },
      ...workbench.profiles.map((profile) => ({
        value: profile.profileId,
        label: `${profile.diseaseName} · ${profile.recordCount} 份报告`,
      })),
    ],
    [workbench.profiles],
  );

  const sourceTypeOptions = useMemo<AppSelectOption[]>(
    () => [
      { value: "", label: "所有分类 / 疾病全局上下文" },
      ...sourceTypes.map((sourceType) => ({
        value: sourceType,
        label: sourceType,
      })),
    ],
    [sourceTypes],
  );

  const mobileProfileOptions = useMemo<AppSelectOption[]>(
    () => [
      { value: "", label: "选择疾病..." },
      ...workbench.profiles.map((profile) => ({
        value: profile.profileId,
        label: profile.diseaseName,
      })),
    ],
    [workbench.profiles],
  );

  const mobileSourceTypeOptions = useMemo<AppSelectOption[]>(
    () => [
      { value: "", label: "全部分类" },
      ...sourceTypes.map((sourceType) => ({
        value: sourceType,
        label: sourceType,
      })),
    ],
    [sourceTypes],
  );

  const promptChips = useMemo(
    () => quickPrompts(workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title),
    [workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title],
  );

  const filteredSessions = useMemo(() => {
    if (!sessionSearch.trim()) return workbench.sessions;
    const query = sessionSearch.trim().toLowerCase();
    return workbench.sessions.filter((s) => {
      const title = getSessionDisplayTitle(s).toLowerCase();
      const disease = (s.diseaseName ?? "").toLowerCase();
      return title.includes(query) || disease.includes(query);
    });
  }, [workbench.sessions, sessionSearch]);

  const riskLabel = useMemo(
    () => severityLabel(workbench.riskOverview.riskLevel),
    [workbench.riskOverview.riskLevel],
  );

  // Auto-scroll to bottom when new messages arrive or streaming
  const scrollToBottom = useCallback(() => {
    if (chatScrollRef.current) {
      chatScrollRef.current.scrollTop = chatScrollRef.current.scrollHeight;
    }
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [workbench.messages.length, workbench.messages[workbench.messages.length - 1]?.content, scrollToBottom]);

  // Close session menu on outside click
  useEffect(() => {
    if (!menuThreadId) return;
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuThreadId(null);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [menuThreadId]);

  const handleRenameSubmit = (threadId: string) => {
    const trimmed = renameValue.trim();
    if (trimmed) {
      void workbench.renameSession(threadId, trimmed);
    }
    setRenamingThreadId(null);
    setRenameValue("");
  };

  const handleDeleteConfirm = (threadId: string) => {
    void workbench.deleteSession(threadId);
    setConfirmDeleteId(null);
  };

  return (
    <main className="page-stack">
      {mobilePanel ? <button className="agent-drawer-backdrop" type="button" aria-label="关闭抽屉" onClick={() => setMobilePanel(null)} /> : null}

      <div className="mb-2">
        <Link href="/" className="inline-flex items-center text-[var(--muted)] hover:text-[var(--primary)] transition-colors text-[14px] font-medium py-1 px-2 -ml-2 rounded-md hover:bg-[var(--primary-soft)]">
          <ArrowLeft className="w-4 h-4 mr-1.5" />
          返回主页
        </Link>
      </div>

      <div className="agent-mobile-toolbar">
        <button className="btn btn-ghost btn-small" type="button" onClick={() => setMobilePanel(mobilePanel === "sessions" ? null : "sessions")}>
          <MessageSquare className="w-4 h-4 mr-1" />
          会话
        </button>
        <AppSelect
          ariaLabel="选择疾病档案"
          value={workbench.profileId}
          options={mobileProfileOptions}
          rootClassName="agent-mobile-select-shell"
          triggerClassName="agent-mobile-select-trigger"
          menuClassName="agent-select-menu"
          onChange={workbench.setProfileId}
        />
        <AppSelect
          ariaLabel="选择报告分类"
          value={workbench.sourceType || ""}
          options={mobileSourceTypeOptions}
          disabled={!workbench.profileId || workbench.loadingRecords}
          rootClassName="agent-mobile-select-shell"
          triggerClassName="agent-mobile-select-trigger"
          menuClassName="agent-select-menu"
          onChange={workbench.setSourceType}
        />
        <button className="btn btn-ghost btn-small" type="button" onClick={() => setMobilePanel(mobilePanel === "context" ? null : "context")}>
          <ClipboardList className="w-4 h-4 mr-1" />
          详情
        </button>
      </div>

      <section className="agent-workbench">
        <aside className={`panel agent-sidebar agent-sidebar-left ${mobilePanel === "sessions" ? "agent-drawer-open" : ""}`} aria-label="会话列表">
          <div className="agent-sidebar-head">
            <div>
              <p className="hero-kicker">疾病档案</p>
              <h3 className="panel-title-small">会话管理</h3>
            </div>
            <button className="btn btn-primary btn-small" type="button" onClick={workbench.startDraftSession}>
              新建会话
            </button>
          </div>

          {workbench.profileId && workbench.sessions.length > 3 && (
            <div className="agent-session-search">
              <Search className="agent-session-search-icon" aria-hidden="true" />
              <input
                className="agent-session-search-input"
                type="text"
                placeholder="搜索会话..."
                value={sessionSearch}
                onChange={(e) => setSessionSearch(e.target.value)}
                aria-label="搜索会话"
              />
            </div>
          )}

          {workbench.sessionError ? <p className="status-text error" role="alert">{workbench.sessionError}</p> : null}

          {!workbench.profileId ? (
            <div className="agent-empty-card">
              <h4>先选择一个疾病档案</h4>
              <p>选择后会按该疾病过滤会话，并在右侧同步显示可用报告上下文。</p>
            </div>
          ) : workbench.loadingSessions ? (
            <SessionSkeleton />
          ) : filteredSessions.length === 0 ? (
            <div className="agent-empty-card">
              <h4>{sessionSearch.trim() ? "未找到匹配的会话" : "还没有对话记录"}</h4>
              <p>{sessionSearch.trim() ? "尝试缩短搜索词或清空搜索。" : "可以先选中一份报告，再发起第一次病情问答。"}</p>
            </div>
          ) : (
            <div className="agent-session-list" role="list" aria-label="会话列表">
              {filteredSessions.map((session) => (
                <div
                  className={`agent-session-item ${workbench.activeThreadId === session.threadId ? "active" : ""}`}
                  key={session.threadId}
                  role="listitem"
                >
                  {confirmDeleteId === session.threadId ? (
                    <div className="agent-session-confirm">
                      <p>确定删除此会话？</p>
                      <div className="agent-session-confirm-actions">
                        <button className="btn btn-danger btn-small" type="button" onClick={() => handleDeleteConfirm(session.threadId)}>
                          删除
                        </button>
                        <button className="btn btn-ghost btn-small" type="button" onClick={() => setConfirmDeleteId(null)}>
                          取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <button
                        className="agent-session-item-body"
                        type="button"
                        onClick={() => {
                          void workbench.selectSession(session.threadId);
                          setMobilePanel(null);
                        }}
                      >
                        <div className="agent-session-meta">
                          {renamingThreadId === session.threadId ? (
                            <input
                              className="agent-session-rename-input"
                              type="text"
                              value={renameValue}
                              autoFocus
                              onClick={(e) => e.stopPropagation()}
                              onChange={(e) => setRenameValue(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === "Enter") {
                                  e.preventDefault();
                                  handleRenameSubmit(session.threadId);
                                }
                                if (e.key === "Escape") {
                                  setRenamingThreadId(null);
                                  setRenameValue("");
                                }
                              }}
                              onBlur={() => handleRenameSubmit(session.threadId)}
                            />
                          ) : (
                            <strong className="agent-session-title">{getSessionDisplayTitle(session)}</strong>
                          )}
                          <span>{formatRelativeDate(session.updatedAt)}</span>
                        </div>
                        <div className="agent-session-foot">
                          <span className="badge">{session.turnCount} 轮</span>
                          {session.isStreaming ? <span className="status-chip status-processing">生成中</span> : null}
                        </div>
                      </button>

                      <div className="agent-session-actions" ref={menuThreadId === session.threadId ? menuRef : undefined}>
                        <button
                          className="agent-session-more-btn"
                          type="button"
                          aria-label="会话操作"
                          onClick={(e) => {
                            e.stopPropagation();
                            setMenuThreadId(menuThreadId === session.threadId ? null : session.threadId);
                          }}
                        >
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {menuThreadId === session.threadId && (
                          <div className="agent-session-menu">
                            <button
                              className="agent-session-menu-item"
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setRenamingThreadId(session.threadId);
                                setRenameValue(getSessionDisplayTitle(session));
                                setMenuThreadId(null);
                              }}
                            >
                              <Pencil className="w-3.5 h-3.5" />
                              重命名
                            </button>
                            <button
                              className="agent-session-menu-item danger"
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setConfirmDeleteId(session.threadId);
                                setMenuThreadId(null);
                              }}
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                              删除
                            </button>
                          </div>
                        )}
                      </div>
                    </>
                  )}
                </div>
              ))}
            </div>
          )}
        </aside>

        <article className="panel agent-chat-panel" role="main" aria-label="对话区域">
          <div className="agent-disclaimer" role="status">
            <ShieldCheck className="w-4 h-4" aria-hidden="true" />
            <span>仅供理解病情和整理问题，诊疗决定请由专业医师确认</span>
          </div>

          <div className="agent-chat-head">
            <div className="agent-chat-title-block">
              <p className="hero-kicker">病情问答</p>
              <h3 className="panel-title-small">
                {workbench.selectedProfile ? workbench.selectedProfile.diseaseName : "请选择疾病档案后开始"}
              </h3>
            </div>
            <div className="agent-chat-context-pills">
              {workbench.selectedProfile ? <span className="badge">{workbench.selectedProfile.recordCount} 份报告</span> : null}
              {workbench.selectedRecord ? <span className="badge">{workbench.selectedRecord.title}</span> : <span className="badge">疾病全局</span>}
              <span className={`badge badge-risk-${workbench.riskOverview.riskLevel}`}>风险：{riskLabel}</span>
            </div>
          </div>

          <AgentWorkflowBar workflow={workbench.workflow} onChange={workbench.setWorkflow} />

          {workbench.messages.length === 0 && (
            <div className="agent-prompt-row">
              {promptChips.map((prompt) => (
                <button
                  className="agent-prompt-chip"
                  key={prompt}
                  type="button"
                  disabled={!workbench.profileId || workbench.isStreaming}
                  onClick={() => void workbench.sendPrompt(prompt)}
                >
                  {prompt}
                </button>
              ))}
            </div>
          )}

          <div className="agent-chat-scroll" ref={chatScrollRef} role="log" aria-live="polite" aria-label="对话消息">
            {!workbench.profileId ? (
              <div className="agent-center-empty">
                <h4>先从左侧选一个疾病档案</h4>
                <p>页面不会默认替你强选档案，避免误把不同疾病的历史上下文混在一起。</p>
              </div>
            ) : workbench.loadingConversation ? (
              <ChatSkeleton />
            ) : workbench.messages.length === 0 ? (
              <div className="agent-center-empty">
                <h4>可以开始第一轮病情对话</h4>
                <p>建议先在右侧选定需要聚焦的报告，再询问异常指标、复诊准备或趋势解读。</p>
              </div>
            ) : (
              workbench.messages.map((message) => (
                <AgentMessageBubble key={message.id} message={message} />
              ))
            )}
          </div>

          <div className="agent-composer" role="form" aria-label="发送消息">
            {workbench.streamError ? <p className="status-text error" role="alert">{workbench.streamError}</p> : null}
            <textarea
              className="agent-textarea"
              placeholder={
                workbench.profileId
                  ? "输入你的问题，例如：这些指标变化意味着什么？复诊前需要准备什么？"
                  : "请先从左侧选择疾病档案"
              }
              value={workbench.draft}
              disabled={!workbench.profileId || workbench.isStreaming}
              onChange={(event) => workbench.setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void workbench.sendPrompt();
                }
              }}
            />
            <div className="agent-composer-actions">
              <div className="meta-row">
                {workbench.activeSessionSummary?.title ? (
                  <span className="badge">当前会话：{getSessionDisplayTitle(workbench.activeSessionSummary)}</span>
                ) : null}
                {workbench.requestMetadata.record_title ? <span className="badge">当前报告：{workbench.requestMetadata.record_title}</span> : null}
                <span className="badge">工作流：{WORKFLOW_LABELS[workbench.workflow]}</span>
              </div>
              <div className="actions">
                {workbench.isStreaming ? (
                  <button className="btn btn-danger" type="button" onClick={workbench.stopStreaming}>
                    停止生成
                  </button>
                ) : null}
                {!workbench.isStreaming && workbench.streamError ? (
                  <button className="btn btn-ghost" type="button" onClick={() => void workbench.retryLastPrompt()}>
                    重试上一问
                  </button>
                ) : null}
                <button
                  className="btn btn-primary"
                  type="button"
                  disabled={!workbench.profileId || workbench.isStreaming || !workbench.draft.trim()}
                  onClick={() => void workbench.sendPrompt()}
                >
                  发送
                </button>
              </div>
            </div>
          </div>
        </article>

        <aside className={`panel agent-sidebar agent-sidebar-right ${mobilePanel === "context" ? "agent-drawer-open" : ""}`} aria-label="病例上下文">
          <AgentContextPanel
            workbench={workbench}
            profileOptions={profileOptions}
            sourceTypeOptions={sourceTypeOptions}
            onCloseMobile={() => setMobilePanel(null)}
          />

          {workbench.careError ? <p className="status-text error">{workbench.careError}</p> : null}

          <RiskOverviewPanel riskOverview={workbench.riskOverview} loading={workbench.loadingRisk} />

          <CareProfilePanel
            careProfile={workbench.careProfile}
            pendingMemories={workbench.pendingMemories}
            onSave={workbench.saveCareProfile}
            onConfirmMemory={workbench.confirmMemory}
            onRejectMemory={workbench.rejectMemory}
          />

          <FollowUpTasksPanel
            tasks={workbench.followUpTasks}
            profileId={workbench.profileId || undefined}
            recordId={workbench.recordId || undefined}
            onCreateTask={workbench.createFollowUpTask}
            onToggleTask={(task) =>
              workbench.updateFollowUpTask(task.id, {
                status: (task.status ?? "OPEN") === "DONE" ? "OPEN" : "DONE",
              })}
          />

          <SymptomLogPanel
            symptoms={workbench.symptoms}
            profileId={workbench.profileId || undefined}
            onCreateSymptom={workbench.createSymptomLog}
          />
        </aside>
      </section>
    </main>
  );
}
