"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { TrendComparisonPanel } from "../profiles/TrendComparisonPanel";
import { formatRelativeDate, quickPrompts, tracePreview } from "./agent-utils";
import type { AgentTraceEvent, AgentWorkbenchProps } from "./types";
import { useAgentWorkbench } from "./useAgentWorkbench";

function traceBody(event: AgentTraceEvent): string {
  if (event.event === "tool_call") {
    return JSON.stringify(event.data.input ?? event.data, null, 2);
  }
  if (event.event === "tool_result") {
    return String(event.data.output ?? "");
  }
  return String(event.data.message ?? "Agent 执行异常");
}

export function AgentWorkbench(props: AgentWorkbenchProps) {
  const [mobilePanel, setMobilePanel] = useState<"sessions" | "context" | null>(null);
  const [expandedTraceKeys, setExpandedTraceKeys] = useState<Record<string, boolean>>({});
  const workbench = useAgentWorkbench(props);

  const promptChips = useMemo(
    () => quickPrompts(workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title),
    [workbench.selectedProfile?.diseaseName, workbench.selectedRecord?.title],
  );

  const toggleTrace = (key: string) => {
    setExpandedTraceKeys((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  return (
    <main className="page-stack">
      <section className="panel reveal">
        <div className="agent-page-head">
          <div>
            <p className="hero-kicker">医疗 Agent</p>
            <h2 className="panel-title">疾病档案对话工作台</h2>
            <p className="muted panel-subtitle">
              围绕疾病时间线、报告解析结果与趋势变化进行连续对话，过程轨迹默认展开。
            </p>
          </div>
          <div className="actions">
            <Link className="btn btn-ghost" href="/">
              返回首页
            </Link>
          </div>
        </div>

        <div className="agent-mobile-toolbar">
          <button className="btn btn-ghost btn-small" type="button" onClick={() => setMobilePanel("sessions")}>
            会话栏
          </button>
          <button className="btn btn-ghost btn-small" type="button" onClick={() => setMobilePanel("context")}>
            病例上下文
          </button>
        </div>
      </section>

      {mobilePanel ? <button className="agent-drawer-backdrop" type="button" aria-label="关闭抽屉" onClick={() => setMobilePanel(null)} /> : null}

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

          <label className="field">
            <span>当前疾病档案</span>
            <select
              className="agent-select"
              value={workbench.profileId}
              onChange={(event) => workbench.setProfileId(event.target.value)}
            >
              <option value="">请选择疾病档案</option>
              {workbench.profiles.map((profile) => (
                <option key={profile.profileId} value={profile.profileId}>
                  {profile.diseaseName} · {profile.recordCount} 份报告
                </option>
              ))}
            </select>
          </label>

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
                    <strong>{session.title}</strong>
                    <span>{formatRelativeDate(session.updatedAt)}</span>
                  </div>
                  <p>{session.lastMessagePreview || session.lastUserMessage || "等待第一条消息"}</p>
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
                <div className={`agent-message agent-message-${message.role}`} key={message.id}>
                  <div className="agent-message-label">
                    {message.role === "user" ? "你" : message.role === "assistant" ? "Agent" : "上下文提示"}
                  </div>

                  {message.role === "assistant" && message.traceEvents && message.traceEvents.length > 0 ? (
                    <div className="agent-trace-list">
                      {message.traceEvents.map((event, index) => {
                        const traceKey = `${message.id}-${index}`;
                        const body = traceBody(event);
                        const shouldCollapse = event.event === "tool_result" && body.length > 240;
                        const expanded = expandedTraceKeys[traceKey];
                        return (
                          <div className={`agent-trace-item agent-trace-${event.event}`} key={traceKey}>
                            <div className="agent-trace-head">
                              <strong>{tracePreview(event)}</strong>
                              {shouldCollapse ? (
                                <button className="agent-trace-toggle" type="button" onClick={() => toggleTrace(traceKey)}>
                                  {expanded ? "收起" : "展开"}
                                </button>
                              ) : null}
                            </div>
                            <pre className="agent-trace-body">
                              {shouldCollapse && !expanded ? `${body.slice(0, 240).trimEnd()}...` : body}
                            </pre>
                          </div>
                        );
                      })}
                    </div>
                  ) : null}

                  <div className="agent-message-bubble">
                    {message.content ? <p>{message.content}</p> : <p className="muted">正在生成回复...</p>}
                    {message.errorMessage ? <p className="status-text error mt-8">{message.errorMessage}</p> : null}
                  </div>
                </div>
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
                {workbench.activeSessionSummary?.title ? <span className="badge">当前会话：{workbench.activeSessionSummary.title}</span> : null}
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
              <h3 className="panel-title-small">报告与趋势</h3>
            </div>
            {workbench.selectedProfile ? <span className="badge">{workbench.selectedProfile.diseaseName}</span> : null}
          </div>

          {workbench.loadingRecords ? <p className="muted">正在加载报告列表...</p> : null}

          <div className="agent-context-card">
            <h4>已纳入上下文的报告</h4>
            {!workbench.profileId ? (
              <p className="muted">先选择疾病档案后，这里会展示同病种报告。</p>
            ) : workbench.records.length === 0 ? (
              <p className="muted">该疾病档案下暂无报告。</p>
            ) : (
              <div className="agent-record-list">
                <button
                  className={`agent-record-item ${!workbench.recordId ? "active" : ""}`}
                  type="button"
                  onClick={() => workbench.setRecordId("")}
                >
                  <strong>疾病级上下文</strong>
                  <span>仅按疾病时间线交流，不绑定单条报告</span>
                </button>
                {workbench.records.map((record) => (
                  <button
                    className={`agent-record-item ${workbench.recordId === record.id ? "active" : ""}`}
                    key={record.id}
                    type="button"
                    onClick={() => {
                      workbench.setRecordId(record.id);
                      setMobilePanel(null);
                    }}
                  >
                    <strong>{record.title}</strong>
                    <span>
                      {record.recordDate || "未知日期"} · {record.sourceType}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {workbench.contextError ? <p className="status-text error">{workbench.contextError}</p> : null}

          <div className="agent-context-card">
            <h4>当前上下文摘要</h4>
            {!workbench.recordId ? (
              <p className="muted">当前采用疾病级视角。若想得到更精确的分析，建议选中一份具体报告。</p>
            ) : workbench.contextLoading ? (
              <p className="muted">正在整理报告上下文...</p>
            ) : !workbench.recordDetail ? (
              <p className="muted">报告详情暂不可用。</p>
            ) : (
              <>
                <div className="meta-row">
                  <span className="badge">{workbench.recordDetail.parseStatus}</span>
                  {workbench.selectedRecord?.recordDate ? <span className="badge">{workbench.selectedRecord.recordDate}</span> : null}
                </div>
                {workbench.recordAnalysis ? (
                  <p className="paragraph-relaxed mt-10">{workbench.recordAnalysis}</p>
                ) : (
                  <p className="muted mt-10">{workbench.recordDetail.summary || "暂无自动分析摘要。"}</p>
                )}
                {workbench.recordDetail.fields.length > 0 ? (
                  <div className="agent-field-list mt-10">
                    {workbench.recordDetail.fields.slice(0, 8).map((field) => (
                      <div className="agent-field-chip" key={`${field.name}-${field.value}`}>
                        <strong>{field.name}</strong>
                        <span>
                          {field.value}
                          {field.unit ?? ""}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : null}
              </>
            )}
          </div>

          <div className="agent-context-card">
            <h4>趋势速览</h4>
            {workbench.recordId ? (
              <TrendComparisonPanel
                loading={workbench.contextLoading && !workbench.trendData}
                error=""
                data={workbench.trendData ?? undefined}
              />
            ) : (
              <p className="muted">选中具体报告后，这里会显示同病种同来源的最近趋势快照。</p>
            )}
          </div>

          <div className="agent-context-card agent-risk-note">
            <h4>使用提醒</h4>
            <ul className="guide-list">
              <li>Agent 输出默认是辅助分析，不替代医生诊断。</li>
              <li>若报告解析未完成，建议先回到时间线页确认结构化结果。</li>
              <li>切换报告后，下一轮消息会自动带上新的上下文摘要。</li>
            </ul>
          </div>
        </aside>
      </section>
    </main>
  );
}
