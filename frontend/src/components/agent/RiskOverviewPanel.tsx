"use client";

import { severityLabel } from "./agent-utils";
import type { EvidenceRef, RiskOverview } from "./types";

function evidenceMeta(item: EvidenceRef): string {
  return [item.type, item.confidence, item.nature, item.source].filter(Boolean).join(" / ");
}

export function RiskOverviewPanel({
  riskOverview,
  loading,
}: {
  riskOverview: RiskOverview;
  loading: boolean;
}) {
  return (
    <section className="agent-care-card">
      <div className="agent-care-card-head">
        <div>
          <p className="hero-kicker">风险监测</p>
          <h4>当前随访风险</h4>
        </div>
        <span className={`agent-risk-badge risk-${riskOverview.riskLevel}`}>{severityLabel(riskOverview.riskLevel)}</span>
      </div>

      <p className="agent-care-summary">{loading ? "正在整理当前风险..." : riskOverview.summary}</p>

      {riskOverview.signals.length > 0 ? (
        <div className="agent-care-stack">
          {riskOverview.signals.map((signal, index) => (
            <article key={`${signal.title}-${index}`} className={`agent-risk-item risk-${signal.severity ?? "routine"}`}>
              <strong>{signal.title}</strong>
              {signal.detail ? <p>{signal.detail}</p> : null}
              {signal.recommendedAction ? <small>{signal.recommendedAction}</small> : null}
            </article>
          ))}
        </div>
      ) : (
        <p className="agent-care-empty">暂无明显红旗信号，当前以按计划随访为主。</p>
      )}

      <div className="agent-care-divider" />

      <div className="agent-care-subhead">
        <strong>证据来源</strong>
        <span>规则、趋势、长期画像</span>
      </div>
      {riskOverview.evidenceRefs.length > 0 ? (
        <div className="agent-care-stack compact">
          {riskOverview.evidenceRefs.map((item, index) => {
            const meta = evidenceMeta(item);
            return (
            <article key={`${item.title}-${index}`} className="agent-evidence-item">
              <strong>{item.title}</strong>
              {item.detail ? <p>{item.detail}</p> : null}
              {meta ? <small>{meta}</small> : null}
            </article>
            );
          })}
        </div>
      ) : (
        <p className="agent-care-empty">当前上下文下还没有可展示的额外证据。</p>
      )}
    </section>
  );
}
