"use client";

import Link from "next/link";
import type { Route } from "next";
import { useSearchParams } from "next/navigation";
import {
  AlertTriangle,
  ArrowRight,
  CalendarClock,
  FileText,
  HeartPulse,
  MessageSquare,
  Pill,
  TrendingUp,
} from "lucide-react";
import { AgentPageFrame } from "../../components/agent/AgentPageFrame";
import { formatRelativeDate, severityLabel } from "../../components/agent/agent-utils";
import { useAgentDashboard } from "../../components/agent/useAgentDashboard";
import { usePatient } from "../../components/auth/PatientProvider";

function scopedHref(path: string, profileId?: string) {
  return profileId ? `${path}?profileId=${encodeURIComponent(profileId)}` : path;
}

const ULTRASOUND_CHANGE_LABEL: Record<string, string> = {
  NO_HISTORY: "暂无历史",
  BASICALLY_STABLE: "基本稳定",
  POSSIBLE_WORSENED: "可能恶化",
  POSSIBLE_IMPROVED: "可能好转",
  INSUFFICIENT_INFO: "信息不足",
  LIMITED_QUALITY: "检查质量不足",
  CANNOT_JUDGE: "无法判断",
  WORSENED: "需关注变化",
  STABLE: "基本稳定",
  UNKNOWN: "无法判断",
};

function LoadingBlock() {
  return (
    <div className="agent-dashboard-loading" role="status">
      <div className="agent-skeleton-line" style={{ width: "36%" }} />
      <div className="agent-skeleton-line" style={{ width: "62%" }} />
      <div className="agent-skeleton-line" style={{ width: "48%" }} />
    </div>
  );
}

export default function AgentDashboardPage() {
  const searchParams = useSearchParams();
  const { currentPatient } = usePatient();
  const profileId = searchParams.get("profileId")?.trim() || undefined;
  const { data, loading, error } = useAgentDashboard(profileId, currentPatient?.id);
  const selectedProfileId = data?.selectedProfile?.profileId;

  if (loading) {
    return (
      <AgentPageFrame profiles={[]} selectedProfile={undefined}>
        <LoadingBlock />
      </AgentPageFrame>
    );
  }

  if (error) {
    return (
      <AgentPageFrame profiles={data?.profiles ?? []} selectedProfile={data?.selectedProfile}>
        <section className="agent-empty-state">
          <AlertTriangle className="w-6 h-6" aria-hidden="true" />
          <h2>暂时无法加载 Agent 总览</h2>
          <p>{error}</p>
        </section>
      </AgentPageFrame>
    );
  }

  if (!data || !data.selectedProfile) {
    return (
      <AgentPageFrame profiles={data?.profiles ?? []} selectedProfile={undefined}>
        <section className="agent-empty-state">
          <HeartPulse className="w-6 h-6" aria-hidden="true" />
          <h2>还没有疾病档案</h2>
          <p>先上传报告并建立疾病档案后，这里会展示风险、趋势、症状和随访任务。</p>
          <Link className="btn btn-primary" href="/records/upload">上传报告</Link>
        </section>
      </AgentPageFrame>
    );
  }

  const riskLevel = data.riskOverview.riskLevel;
  const latestRecord = data.latestRecord;
  const latestUltrasound = data.latestUltrasoundFollowUp;

  return (
    <AgentPageFrame profiles={data.profiles} selectedProfile={data.selectedProfile}>
      {/* ===== Primary row: Status hero + Quick actions ===== */}
      <section className="agent-dash-primary">
        {/* Status hero — compact but rich */}
        <article className={`agent-hero-status risk-${riskLevel}`}>
          <div className="agent-hero-status-top">
            <div className="agent-hero-status-info">
              <span className="agent-hero-kicker">当前状态</span>
              <h2 className="agent-hero-risk-label">{severityLabel(riskLevel)}</h2>
            </div>
            <div className="agent-hero-status-icon">
              <HeartPulse className="w-6 h-6" aria-hidden="true" />
            </div>
          </div>
          <p className="agent-hero-summary">{data.riskOverview.summary}</p>
          <div className="agent-hero-metrics">
            <div className="agent-metric-pill">
              <FileText className="w-3.5 h-3.5" />
              <span>{data.selectedProfile.recordCount} 份报告</span>
            </div>
            <div className="agent-metric-pill">
              <CalendarClock className="w-3.5 h-3.5" />
              <span>{data.followUpTasks.length} 项待办</span>
            </div>
            <div className="agent-metric-pill">
              <HeartPulse className="w-3.5 h-3.5" />
              <span>{data.symptoms.length} 条记录</span>
            </div>
          </div>
        </article>

        {/* Quick-action cards */}
        <div className="agent-quick-actions">
          <Link
            className="agent-action-card agent-action-chat"
            href={scopedHref("/agent/chat", selectedProfileId) as Route}
          >
            <div className="agent-action-icon-wrap agent-action-icon-chat">
              <MessageSquare className="w-5 h-5" />
            </div>
            <div className="agent-action-text">
              <strong>AI 病情咨询</strong>
              <span>与 AI 对话，解读报告和指标</span>
            </div>
            <ArrowRight className="w-4 h-4 agent-action-arrow" />
          </Link>

          <Link
            className="agent-action-card agent-action-trends"
            href={scopedHref("/agent/trends", selectedProfileId) as Route}
          >
            <div className="agent-action-icon-wrap agent-action-icon-trends">
              <TrendingUp className="w-5 h-5" />
            </div>
            <div className="agent-action-text">
              <strong>趋势分析</strong>
              <span>追踪指标变化和趋势</span>
            </div>
            <ArrowRight className="w-4 h-4 agent-action-arrow" />
          </Link>

          <Link
            className="agent-action-card agent-action-tasks"
            href={scopedHref("/agent/tasks", selectedProfileId) as Route}
          >
            <div className="agent-action-icon-wrap agent-action-icon-tasks">
              <CalendarClock className="w-5 h-5" />
            </div>
            <div className="agent-action-text">
              <strong>随访管理</strong>
              <span>{data.followUpTasks.length} 项待办事项</span>
            </div>
            <ArrowRight className="w-4 h-4 agent-action-arrow" />
          </Link>
        </div>
      </section>

      {/* ===== Main content: 2-column overview ===== */}
      <section className="agent-dash-overview">
        {/* Left column — consolidated info cards */}
        <div className="agent-dash-col-main">
          {/* Latest report card */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap">
                <FileText className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">最新报告</span>
                <h3>{latestRecord ? latestRecord.title : "暂无报告"}</h3>
              </div>
            </div>
            {latestRecord ? (
              <div className="agent-overview-card-body">
                <p>{latestRecord.recordDate || "未记录日期"} · {latestRecord.sourceType}</p>
                <Link className="agent-card-link" href={scopedHref("/agent/chat", selectedProfileId) as Route}>
                  解读这份报告 <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            ) : (
              <p className="agent-overview-card-empty">上传同一疾病下的报告后，可以在这里跟踪变化和发起解读。</p>
            )}
          </article>

          {latestUltrasound ? (
            <article className="agent-overview-card agent-ultrasound-card">
              <div className="agent-overview-card-head">
                <div className="agent-overview-icon-wrap agent-overview-icon-trend">
                  <TrendingUp className="w-4.5 h-4.5" />
                </div>
                <div>
                  <span className="agent-overview-kicker">彩超病程</span>
                  <h3>{ULTRASOUND_CHANGE_LABEL[latestUltrasound.changeStatus] ?? latestUltrasound.changeStatus}</h3>
                </div>
              </div>
              <p className="agent-overview-card-empty">
                {latestUltrasound.patientSummary || latestUltrasound.summary}
              </p>
              {latestUltrasound.findingRows && latestUltrasound.findingRows.length > 0 ? (
                <div className="agent-mini-list agent-ultrasound-findings">
                  {latestUltrasound.findingRows.slice(0, 4).map((row) => (
                    <span key={`${row.module}-${row.trendStatus}`}>
                      {row.module}：{ULTRASOUND_CHANGE_LABEL[row.trendStatus] ?? row.trendStatus}
                    </span>
                  ))}
                </div>
              ) : null}
              {latestUltrasound.missingInputs && latestUltrasound.missingInputs.length > 0 ? (
                <p className="agent-ultrasound-missing">
                  缺少：{latestUltrasound.missingInputs.slice(0, 6).map((item) => item.name).join("、")}
                </p>
              ) : null}
              <Link className="agent-card-link" href={scopedHref("/agent/chat", selectedProfileId) as Route}>
                围绕彩超追问 <ArrowRight className="w-4 h-4" />
              </Link>
            </article>
          ) : null}

          {/* Risk signals */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap agent-overview-icon-risk">
                <AlertTriangle className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">风险提醒</span>
                <h3>需要关注的信号</h3>
              </div>
            </div>
            {data.riskOverview.signals.length > 0 ? (
              <div className="agent-risk-list">
                {data.riskOverview.signals.map((signal, index) => (
                  <div className={`agent-risk-row risk-${signal.severity ?? "routine"}`} key={`${signal.title}-${index}`}>
                    <strong>{signal.title}</strong>
                    {signal.detail ? <p>{signal.detail}</p> : null}
                  </div>
                ))}
              </div>
            ) : (
              <p className="agent-overview-card-empty">当前没有明显红旗信号，按计划记录和复查即可。</p>
            )}
          </article>

          {/* Trend highlights */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap agent-overview-icon-trend">
                <TrendingUp className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">趋势亮点</span>
                <h3>{data.trendHighlights.length > 0 ? "近期变化" : "等待更多数据"}</h3>
              </div>
            </div>
            {data.trendHighlights.length > 0 ? (
              <div className="agent-mini-list">
                {data.trendHighlights.slice(0, 4).map((item) => (
                  <span key={`${item.name}-${item.recordDate}`}>
                    {item.name}：{item.previousValue ? `${item.previousValue} -> ` : ""}{item.currentValue}{item.unit ?? ""}
                  </span>
                ))}
              </div>
            ) : (
              <p className="agent-overview-card-empty">需要至少两份同分类报告，才会形成可比较的趋势。</p>
            )}
            <Link className="agent-card-link" href={scopedHref("/agent/trends", selectedProfileId) as Route}>
              查看趋势 <ArrowRight className="w-4 h-4" />
            </Link>
          </article>
        </div>

        {/* Right column — follow-ups, symptoms, medications */}
        <div className="agent-dash-col-side">
          {/* Follow-up tasks */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap agent-overview-icon-task">
                <CalendarClock className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">行动待办</span>
                <h3>随访任务</h3>
              </div>
            </div>
            {data.followUpTasks.length > 0 ? (
              <div className="agent-mini-list">
                {data.followUpTasks.slice(0, 4).map((task) => (
                  <span key={task.id}>{task.title}{task.dueDate ? ` · ${task.dueDate}` : ""}</span>
                ))}
              </div>
            ) : (
              <p className="agent-overview-card-empty">还没有该疾病的随访任务，可以添加复查、复诊或观察事项。</p>
            )}
            <Link className="agent-card-link" href={scopedHref("/agent/tasks", selectedProfileId) as Route}>
              管理随访 <ArrowRight className="w-4 h-4" />
            </Link>
          </article>

          {/* Symptoms */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap">
                <HeartPulse className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">近期记录</span>
                <h3>症状 / 体征</h3>
              </div>
            </div>
            {data.symptoms.length > 0 ? (
              <div className="agent-mini-list">
                {data.symptoms.slice(0, 4).map((item) => (
                  <span key={item.id}>
                    {item.label}{item.value ? ` · ${item.value}${item.unit ?? ""}` : ""}{item.recordedAt ? ` · ${formatRelativeDate(item.recordedAt)}` : ""}
                  </span>
                ))}
              </div>
            ) : (
              <p className="agent-overview-card-empty">该疾病下还没有症状或家庭测量记录。</p>
            )}
          </article>

          {/* Medications & care goals */}
          <article className="agent-overview-card">
            <div className="agent-overview-card-head">
              <div className="agent-overview-icon-wrap agent-overview-icon-med">
                <Pill className="w-4.5 h-4.5" />
              </div>
              <div>
                <span className="agent-overview-kicker">当前患者用药</span>
                <h3>用药与目标</h3>
              </div>
            </div>
            {data.currentMedications.length > 0 || data.careGoals.length > 0 ? (
              <div className="agent-mini-list">
                {data.currentMedications.slice(0, 3).map((item) => (
                  <span key={`${item.name}-${item.dosage ?? ""}`}>{[item.name, item.dosage, item.frequency].filter(Boolean).join(" / ")}</span>
                ))}
                {data.careGoals.slice(0, 2).map((goal) => <span key={goal}>目标：{goal}</span>)}
              </div>
            ) : (
              <p className="agent-overview-card-empty">补充用药和健康目标后，AI 咨询会更容易结合长期背景。</p>
            )}
          </article>
        </div>
      </section>
    </AgentPageFrame>
  );
}
