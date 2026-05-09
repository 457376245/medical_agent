"use client";

type UltrasoundEvidenceItem = {
  recordId: string;
  recordDate: string;
  label: string;
  text: string;
};

type UltrasoundHistoryItem = {
  recordId: string;
  recordDate: string;
  title: string;
  summary: string;
};

export type UltrasoundFollowUpResult = {
  mode: string;
  changeStatus: string;
  summary: string;
  actionLevel: string;
  actionSuggestion: string;
  currentEvidence: UltrasoundEvidenceItem[];
  previousEvidence: UltrasoundEvidenceItem[];
  history: UltrasoundHistoryItem[];
};

const MODE_LABEL: Record<string, string> = {
  SINGLE_REPORT: "单份解读",
  FOLLOW_UP: "随访对比",
};

const CHANGE_LABEL: Record<string, string> = {
  NO_HISTORY: "暂无历史",
  STABLE: "基本稳定",
  WORSENED: "需关注变化",
  IMPROVED: "有所改善",
  NEW: "新增描述",
  UNKNOWN: "无法判断",
};

const ACTION_LABEL: Record<string, string> = {
  OBSERVE: "观察",
  RECHECK_SOON: "尽快复查",
  SEEK_CARE_SOON: "尽快就医",
  IMMEDIATE_CARE: "立即就医",
};

export function UltrasoundFollowUpPanel({ value }: { value: UltrasoundFollowUpResult | null }) {
  if (!value) {
    return null;
  }

  return (
    <section className="ultrasound-follow-panel">
      <div className="ultrasound-follow-head">
        <h4 className="summary-heading">超声/彩超解读与随访</h4>
        <div className="ultrasound-follow-tags">
          <span className="ultrasound-follow-tag">{MODE_LABEL[value.mode] ?? value.mode}</span>
          <span className={`ultrasound-follow-tag action-${value.actionLevel.toLowerCase()}`}>
            {ACTION_LABEL[value.actionLevel] ?? value.actionLevel}
          </span>
        </div>
      </div>
      <p className="ultrasound-follow-summary">{value.summary}</p>
      {value.changeStatus ? (
        <p className="ultrasound-follow-meta">变化判断：{CHANGE_LABEL[value.changeStatus] ?? value.changeStatus}</p>
      ) : null}
      {value.actionSuggestion ? (
        <p className="ultrasound-follow-action">
          <strong>建议：</strong>
          {value.actionSuggestion}
        </p>
      ) : null}
      <EvidenceList title="本次依据" items={value.currentEvidence} />
      <EvidenceList title="上次依据" items={value.previousEvidence} />
      {value.history && value.history.length > 0 ? (
        <div className="ultrasound-history">
          <p className="ultrasound-evidence-title">近几次摘要</p>
          <div className="ultrasound-history-list">
            {value.history.map((item) => (
              <div className="ultrasound-history-item" key={item.recordId}>
                <span>{item.recordDate}</span>
                <strong>{item.title}</strong>
                {item.summary ? <p>{item.summary}</p> : null}
              </div>
            ))}
          </div>
        </div>
      ) : null}
      <p className="ultrasound-follow-disclaimer">以上为报告文本辅助解读，不替代医生诊断或影像阅片。</p>
    </section>
  );
}

function EvidenceList({ title, items }: { title: string; items: UltrasoundEvidenceItem[] }) {
  if (!items || items.length === 0) {
    return null;
  }
  return (
    <div className="ultrasound-evidence">
      <p className="ultrasound-evidence-title">{title}</p>
      <ul>
        {items.map((item) => (
          <li key={`${item.recordId}-${item.label}-${item.text}`}>
            <span>{[item.recordDate, item.label].filter(Boolean).join(" / ")}</span>
            <p>{item.text}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
