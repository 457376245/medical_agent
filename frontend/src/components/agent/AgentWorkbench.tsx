"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { TrendComparisonPanel } from "../profiles/TrendComparisonPanel";
import { formatRelativeDate, getSessionDisplayTitle, quickPrompts } from "./agent-utils";
import type { AgentWorkbenchProps } from "./types";
import { useAgentWorkbench } from "./useAgentWorkbench";
import { AgentMessageBubble } from "./AgentMessageBubble";
import { ArrowLeft } from "lucide-react";



export function AgentWorkbench(props: AgentWorkbenchProps) {
  const [mobilePanel, setMobilePanel] = useState<"sessions" | "context" | null>(null);
  const workbench = useAgentWorkbench(props);

  const sourceTypes = useMemo(() => {
    return Array.from(new Set(workbench.records.map((r) => r.sourceType))).filter(Boolean);
  }, [workbench.records]);

  const promptChips = useMemo(
    () => quickPrompts(workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title),
    [workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title],
  );

  return (
    <main className="page-stack">
      {mobilePanel ? <button className="agent-drawer-backdrop" type="button" aria-label="关闭抽屉" onClick={() => setMobilePanel(null)} /> : null}

      <div className="mb-2">
        <Link href="/" className="inline-flex items-center text-[var(--muted)] hover:text-[var(--primary)] transition-colors text-[14px] font-medium py-1 px-2 -ml-2 rounded-md hover:bg-[var(--primary-soft)]">
          <ArrowLeft className="w-4 h-4 mr-1.5" />
          返回主页
        </Link>
      </div>

      <section className="agent-workbench">
        <aside className={`panel agent-sidebar agent-sidebar-left ${mobilePanel === "sessions" ? "agent-drawer-open" : ""}`}>
          <div className="agent-sidebar-head">
            <div>
              <p className="hero-kicker">疾病档案</p>
              <h3 className="panel-title-small">会话管理</h3>
            </div>
            <button className="btn btn-primary btn-small" type="button" onClick={workbench.startDraftSession}>
              新建会话
            </button>
          </div>



          {workbench.sessionError ? <p className="status-text error">{workbench.sessionError}</p> : null}

          {!workbench.profileId ? (
            <div className="agent-empty-card">
              <h4>先选择一个疾病档案</h4>
              <p>选择后会按该疾病过滤会话，并在右侧同步显示可用报告上下文。</p>
            </div>
          ) : workbench.loadingSessions ? (
            <p className="muted">正在加载会话列表...</p>
          ) : workbench.sessions.length === 0 ? (
            <div className="agent-empty-card">
              <h4>还没有对话记录</h4>
              <p>可以先选中一份报告，再发起第一次病情问答。</p>
            </div>
          ) : (
            <div className="agent-session-list">
              {workbench.sessions.map((session) => (
                <button
                  className={`agent-session-item ${workbench.activeThreadId === session.threadId ? "active" : ""}`}
                  key={session.threadId}
                  type="button"
                  onClick={() => {
                    void workbench.selectSession(session.threadId);
                    setMobilePanel(null);
                  }}
                >
                  <div className="agent-session-meta">
                    <strong className="agent-session-title">{getSessionDisplayTitle(session)}</strong>
                    <span>{formatRelativeDate(session.updatedAt)}</span>
                  </div>
                  <div className="agent-session-foot">
                    <span className="badge">{session.turnCount} 轮</span>
                    {session.isStreaming ? <span className="status-chip status-processing">生成中</span> : null}
                  </div>
                </button>
              ))}
            </div>
          )}
        </aside>

        <article className="panel agent-chat-panel">
          <div className="agent-chat-head">
            <div>
              <p className="hero-kicker">对话中心</p>
              <h3 className="panel-title-small">
                {workbench.selectedProfile ? workbench.selectedProfile.diseaseName : "请选择疾病档案后开始"}
              </h3>
              <div className="meta-row mt-8">
                {workbench.selectedProfile ? <span className="badge">{workbench.selectedProfile.recordCount} 份报告</span> : null}
                {workbench.selectedRecord ? <span className="badge">{workbench.selectedRecord.title}</span> : null}
                <span className="status-chip">仅供医疗辅助参考，诊疗请由专业医师确认</span>
              </div>
            </div>
          </div>

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

          <div className="agent-chat-scroll">
            {!workbench.profileId ? (
              <div className="agent-center-empty">
                <h4>先从左侧选一个疾病档案</h4>
                <p>页面不会默认替你强选档案，避免误把不同疾病的历史上下文混在一起。</p>
              </div>
            ) : workbench.loadingConversation ? (
              <p className="muted">正在恢复会话内容...</p>
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

          <div className="agent-composer">
            {workbench.streamError ? <p className="status-text error">{workbench.streamError}</p> : null}
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

        <aside className={`panel agent-sidebar agent-sidebar-right ${mobilePanel === "context" ? "agent-drawer-open" : ""}`}>
          <div className="agent-sidebar-head">
            <div>
              <p className="hero-kicker">病例上下文</p>
              <h3 className="panel-title-small">档案与报告</h3>
            </div>
          </div>

          <div className="agent-context-card mb-4 border-b border-line pb-4">
            <label className="field">
              <span className="font-semibold text-ink">选择疾病档案</span>
              <select
                className="mt-2 block w-full px-3 py-2 text-[14px] text-[var(--ink)] bg-white border border-[var(--line)] rounded-lg shadow-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)] focus:border-transparent transition-shadow cursor-pointer"
                value={workbench.profileId}
                onChange={(event) => workbench.setProfileId(event.target.value)}
              >
                <option value="">请选择疾病...</option>
                {workbench.profiles.map((profile) => (
                  <option key={profile.profileId} value={profile.profileId}>
                    {profile.diseaseName} · {profile.recordCount} 份报告
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="agent-context-card mb-4">
            <label className="field">
              <span className="font-semibold text-ink">选择报告分类</span>
              <select
                className="mt-2 block w-full px-3 py-2 text-[14px] text-[var(--ink)] bg-white border border-[var(--line)] rounded-lg shadow-sm focus:outline-none focus:ring-2 focus:ring-[var(--primary)] focus:border-transparent transition-shadow cursor-pointer"
                value={workbench.sourceType || ""}
                onChange={(event) => {
                  workbench.setSourceType(event.target.value);
                  setMobilePanel(null);
                }}
                disabled={!workbench.profileId || workbench.loadingRecords}
              >
                <option value="">所有分类 / 疾病全局上下文</option>
                {sourceTypes.map((st) => (
                  <option key={st} value={st}>
                    {st}
                  </option>
                ))}
              </select>
            </label>
            {workbench.loadingRecords ? <p className="muted text-sm mt-2">加载中...</p> : null}
            {!workbench.profileId ? <p className="muted text-sm mt-2">请先选择上方的疾病档案。</p> : null}
          </div>
        </aside>
      </section>
    </main>
  );
}
