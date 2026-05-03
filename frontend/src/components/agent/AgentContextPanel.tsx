"use client";

import { Activity, FileText, RotateCcw, ShieldAlert, Stethoscope } from "lucide-react";
import { TrendComparisonPanel } from "../profiles/TrendComparisonPanel";
import { AppSelect, type AppSelectOption } from "../common/AppSelect";
import { severityLabel } from "./agent-utils";
import type { UseAgentWorkbenchResult } from "./useAgentWorkbench";

function parseStatusLabel(status?: string): string {
  switch ((status ?? "").toUpperCase()) {
    case "SUCCESS":
      return "已解析";
    case "PROCESSING":
      return "解析中";
    case "FAILED":
      return "解析失败";
    case "NOT_PARSED":
      return "未解析";
    default:
      return status || "未知状态";
  }
}

function previewText(value: string, maxLength = 180): string {
  const normalized = value.replace(/\s+/g, " ").trim();
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, maxLength).trimEnd()}...`;
}

export function AgentContextPanel({
  workbench,
  profileOptions,
  sourceTypeOptions,
  onCloseMobile,
}: {
  workbench: UseAgentWorkbenchResult;
  profileOptions: AppSelectOption[];
  sourceTypeOptions: AppSelectOption[];
  onCloseMobile?: () => void;
}) {
  const filteredRecords = workbench.sourceType
    ? workbench.records.filter((record) => record.sourceType === workbench.sourceType)
    : workbench.records;
  const selectedRecord = workbench.selectedRecord;
  const recordDetail = workbench.recordDetail;
  const visibleFields = recordDetail?.fields.slice(0, 6) ?? [];
  const hiddenFieldCount = Math.max(0, (recordDetail?.fields.length ?? 0) - visibleFields.length);
  const riskLabel = severityLabel(workbench.riskOverview.riskLevel);
  const canResetRecord = Boolean(workbench.recordId);

  return (
    <div className="agent-context-shell">
      <div className="agent-context-hero">
        <div>
          <p className="hero-kicker">病例上下文</p>
          <h3 className="panel-title-small">
            {workbench.selectedProfile?.diseaseName ?? "先选择疾病档案"}
          </h3>
        </div>
        <span className={`agent-risk-badge risk-${workbench.riskOverview.riskLevel}`}>
          {riskLabel}
        </span>
      </div>

      <section className="agent-context-section agent-context-controls" aria-label="选择病例上下文">
        <label className="agent-context-control">
          <span>疾病档案</span>
          <AppSelect
            ariaLabel="选择疾病档案"
            value={workbench.profileId}
            options={profileOptions}
            rootClassName="agent-custom-select"
            triggerClassName="agent-select-trigger"
            menuClassName="agent-select-menu"
            onChange={(nextValue) => {
              workbench.setProfileId(nextValue);
              onCloseMobile?.();
            }}
          />
        </label>
        <label className="agent-context-control">
          <span>报告分类</span>
          <AppSelect
            ariaLabel="选择报告分类"
            value={workbench.sourceType || ""}
            options={sourceTypeOptions}
            disabled={!workbench.profileId || workbench.loadingRecords}
            rootClassName="agent-custom-select"
            triggerClassName="agent-select-trigger"
            menuClassName="agent-select-menu"
            onChange={(nextValue) => {
              workbench.setSourceType(nextValue);
              onCloseMobile?.();
            }}
          />
        </label>
        <button
          className="agent-context-reset"
          type="button"
          disabled={!workbench.profileId || !canResetRecord}
          onClick={() => workbench.setRecordId("")}
        >
          <RotateCcw className="w-4 h-4" aria-hidden="true" />
          回到疾病全局
        </button>
        {workbench.loadingRecords ? <p className="agent-context-note">正在加载报告...</p> : null}
        {!workbench.profileId ? <p className="agent-context-note">请选择一个疾病档案后开始病情问答。</p> : null}
      </section>

      <section className="agent-context-section" aria-label="报告时间线">
        <div className="agent-context-section-head">
          <div>
            <p className="hero-kicker">报告时间线</p>
            <h4>当前可参考的报告</h4>
          </div>
          <span className="badge">{filteredRecords.length} 份</span>
        </div>
        {workbench.profileId && filteredRecords.length > 0 ? (
          <div className="agent-context-record-list" role="list">
            {filteredRecords.map((record) => (
              <button
                className={`agent-context-record ${workbench.recordId === record.id ? "active" : ""}`}
                key={record.id}
                type="button"
                role="listitem"
                onClick={() => {
                  workbench.setRecordId(record.id);
                  onCloseMobile?.();
                }}
              >
                <span className="agent-context-record-icon" aria-hidden="true">
                  <FileText className="w-4 h-4" />
                </span>
                <span className="agent-context-record-body">
                  <strong>{record.title}</strong>
                  <small>{[record.recordDate, record.sourceType].filter(Boolean).join(" / ") || "未标注日期"}</small>
                </span>
              </button>
            ))}
          </div>
        ) : (
          <p className="agent-care-empty">
            {workbench.profileId ? "当前筛选条件下还没有报告。" : "选择疾病档案后会显示可参考报告。"}
          </p>
        )}
      </section>

      <section className="agent-context-section" aria-label="报告摘要">
        <div className="agent-context-section-head">
          <div>
            <p className="hero-kicker">报告摘要</p>
            <h4>{selectedRecord?.title ?? "疾病全局视角"}</h4>
          </div>
          {recordDetail ? <span className="badge">{parseStatusLabel(recordDetail.parseStatus)}</span> : null}
        </div>

        {!workbench.recordId ? (
          <div className="agent-context-insight">
            <Stethoscope className="w-4 h-4" aria-hidden="true" />
            <p>当前问题会结合该疾病档案的长期画像、随访风险和可用历史报告进行回答。</p>
          </div>
        ) : workbench.contextLoading ? (
          <p className="agent-care-empty">正在整理这份报告的摘要和关键指标...</p>
        ) : workbench.contextError ? (
          <p className="status-text error">{workbench.contextError}</p>
        ) : recordDetail ? (
          <>
            {recordDetail.summary ? (
              <p className="agent-context-summary">{recordDetail.summary}</p>
            ) : (
              <p className="agent-care-empty">这份报告暂时没有可展示的文字摘要。</p>
            )}
            {visibleFields.length > 0 ? (
              <div className="agent-context-field-grid">
                {visibleFields.map((field) => (
                  <article className="agent-context-field" key={`${field.name}-${field.value}-${field.unit ?? ""}`}>
                    <strong>{field.name}</strong>
                    <span>{field.value}{field.unit ? ` ${field.unit}` : ""}</span>
                    {field.referenceRange ? <small>参考：{field.referenceRange}</small> : null}
                  </article>
                ))}
                {hiddenFieldCount > 0 ? <span className="agent-context-more">另有 {hiddenFieldCount} 项指标</span> : null}
              </div>
            ) : null}
          </>
        ) : (
          <p className="agent-care-empty">未能加载这份报告的结构化详情。</p>
        )}
      </section>

      {workbench.recordAnalysis ? (
        <section className="agent-context-section" aria-label="AI 解读摘要">
          <div className="agent-context-section-head">
            <div>
              <p className="hero-kicker">AI 解读摘要</p>
              <h4>可供对话引用</h4>
            </div>
            <Activity className="agent-context-head-icon" aria-hidden="true" />
          </div>
          <p className="agent-context-summary">{previewText(workbench.recordAnalysis)}</p>
        </section>
      ) : null}

      <section className="agent-context-section agent-context-risk-note" aria-label="患者提示">
        <ShieldAlert className="w-4 h-4" aria-hidden="true" />
        <p>这里的信息用于帮助你向 Agent 提问和理解报告，不能替代医生诊断或治疗决定。</p>
      </section>

      {workbench.recordId ? (
        <section className="agent-context-section agent-context-trend" aria-label="趋势快照">
          <TrendComparisonPanel
            loading={workbench.contextLoading}
            error={workbench.contextError}
            data={workbench.trendData ?? undefined}
          />
        </section>
      ) : null}
    </div>
  );
}
