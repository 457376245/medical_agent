"use client";

type CombinationAnalysisItem = {
  ruleId: string;
  name: string;
  severity: string;
  summary: string;
  detail: string;
  suggestion: string;
  involvedIndicators: string[];
};

const SEVERITY_ORDER: Record<string, number> = {
  alert: 0,
  warning: 1,
  info: 2,
};

const SEVERITY_LABEL: Record<string, string> = {
  alert: "需关注",
  warning: "提示",
  info: "参考",
};

export function CombinationAnalysisPanel({ items }: { items: CombinationAnalysisItem[] }) {
  if (!items || items.length === 0) {
    return null;
  }

  const sorted = [...items].sort(
    (a, b) => (SEVERITY_ORDER[a.severity] ?? 9) - (SEVERITY_ORDER[b.severity] ?? 9),
  );

  return (
    <div className="combination-analysis-panel">
      <h4 className="summary-heading">指标联动分析</h4>
      <div className="combination-card-list">
        {sorted.map((item) => (
          <div key={item.ruleId} className={`combination-card combination-card-${item.severity}`}>
            <div className="combination-card-header">
              <span className={`combination-severity-chip combination-severity-${item.severity}`}>
                {SEVERITY_LABEL[item.severity] ?? item.severity}
              </span>
              <span className="combination-card-title">{item.name}</span>
            </div>
            <p className="combination-card-summary">{item.summary}</p>
            {item.detail && <p className="combination-card-detail">{item.detail}</p>}
            {item.suggestion && (
              <p className="combination-card-suggestion">
                <strong>建议：</strong>
                {item.suggestion}
              </p>
            )}
            {item.involvedIndicators && item.involvedIndicators.length > 0 && (
              <div className="combination-card-indicators">
                {item.involvedIndicators.map((code) => (
                  <span key={code} className="combination-indicator-tag">
                    {code}
                  </span>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
