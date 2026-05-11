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

type UltrasoundFindingRow = {
  module: string;
  currentValue: string;
  previousValue: string;
  currentStatus: string;
  previousStatus: string;
  trendStatus: string;
  evidenceLevel: string;
  explanation: string;
  evidenceRefs: UltrasoundEvidenceItem[];
};

type UltrasoundRiskModule = {
  name: string;
  level: string;
  summary: string;
  evidence: string[];
  missingInputs: string[];
};

type UltrasoundMissingInput = {
  name: string;
  reason: string;
  category: string;
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
  patientSummary?: string;
  clinicalSummary?: string;
  confidenceLevel?: string;
  findingRows?: UltrasoundFindingRow[];
  riskModules?: UltrasoundRiskModule[];
  missingInputs?: UltrasoundMissingInput[];
  nextQuestionsForDoctor?: string[];
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
  BASICALLY_STABLE: "基本稳定",
  POSSIBLE_WORSENED: "可能恶化",
  POSSIBLE_IMPROVED: "可能好转",
  CANNOT_JUDGE: "无法判断",
  INSUFFICIENT_INFO: "信息不足",
  LIMITED_QUALITY: "检查质量不足",
};

const ACTION_LABEL: Record<string, string> = {
  OBSERVE: "观察",
  RECHECK_SOON: "尽快复查",
  SEEK_CARE_SOON: "尽快就医",
  IMMEDIATE_CARE: "立即就医",
};

const STATUS_LABEL: Record<string, string> = {
  PRESENT: "存在",
  ABSENT: "未见",
  NOT_MENTIONED: "未提及",
  UNCLEAR: "显示不清",
  NOT_EXAMINED: "未检查",
  SUSPICIOUS: "可疑异常",
};

const CONFIDENCE_LABEL: Record<string, string> = {
  MEDIUM: "中",
  MEDIUM_LOW: "中低",
  LOW: "低",
};

const RISK_LABEL: Record<string, string> = {
  routine: "常规",
  watch: "需补充",
  warning: "需关注",
  alert: "高优先级",
};

export function UltrasoundFollowUpPanel({ value }: { value: UltrasoundFollowUpResult | null }) {
  if (!value) {
    return null;
  }
  const summary = value.patientSummary || value.summary;
  const findingRows = value.findingRows ?? [];
  const riskModules = value.riskModules ?? [];
  const missingInputs = value.missingInputs ?? [];
  const questions = value.nextQuestionsForDoctor ?? [];

  return (
    <section className="ultrasound-follow-panel">
      <div className="ultrasound-follow-head">
        <h4 className="summary-heading">超声/彩超解读与随访</h4>
        <div className="ultrasound-follow-tags">
          <span className="ultrasound-follow-tag">{MODE_LABEL[value.mode] ?? value.mode}</span>
          <span className={`ultrasound-follow-tag action-${value.actionLevel.toLowerCase()}`}>
            {ACTION_LABEL[value.actionLevel] ?? value.actionLevel}
          </span>
          {value.confidenceLevel ? (
            <span className="ultrasound-follow-tag">
              可信度：{CONFIDENCE_LABEL[value.confidenceLevel] ?? value.confidenceLevel}
            </span>
          ) : null}
        </div>
      </div>
      <p className="ultrasound-follow-summary">{summary}</p>
      {value.changeStatus ? (
        <p className="ultrasound-follow-meta">变化判断：{CHANGE_LABEL[value.changeStatus] ?? value.changeStatus}</p>
      ) : null}
      {value.actionSuggestion ? (
        <p className="ultrasound-follow-action">
          <strong>建议：</strong>
          {value.actionSuggestion}
        </p>
      ) : null}
      {findingRows.length > 0 ? <FindingTable rows={findingRows} /> : null}
      {riskModules.length > 0 ? <RiskModules items={riskModules} /> : null}
      {missingInputs.length > 0 ? <MissingInputs items={missingInputs} /> : null}
      {questions.length > 0 ? <DoctorQuestions items={questions} /> : null}
      {value.clinicalSummary ? (
        <div className="ultrasound-clinical-summary">
          <p className="ultrasound-evidence-title">临床要点</p>
          <p>{value.clinicalSummary}</p>
        </div>
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

function FindingTable({ rows }: { rows: UltrasoundFindingRow[] }) {
  return (
    <div className="ultrasound-findings">
      <p className="ultrasound-evidence-title">结构化趋势</p>
      <div className="ultrasound-findings-table-wrap">
        <table className="ultrasound-findings-table">
          <thead>
            <tr>
              <th>模块</th>
              <th>本次</th>
              <th>上次</th>
              <th>趋势</th>
              <th>解释</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={`${row.module}-${row.currentValue}-${row.previousValue}`}>
                <td>{row.module}</td>
                <td>
                  <strong>{STATUS_LABEL[row.currentStatus] ?? row.currentStatus}</strong>
                  <span>{row.currentValue}</span>
                </td>
                <td>
                  <strong>{STATUS_LABEL[row.previousStatus] ?? row.previousStatus}</strong>
                  <span>{row.previousValue}</span>
                </td>
                <td>
                  <span className={`ultrasound-trend-pill trend-${row.trendStatus.toLowerCase()}`}>
                    {CHANGE_LABEL[row.trendStatus] ?? row.trendStatus}
                  </span>
                </td>
                <td>{row.explanation}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function RiskModules({ items }: { items: UltrasoundRiskModule[] }) {
  return (
    <div className="ultrasound-risk-grid">
      {items.map((item) => (
        <article className={`ultrasound-risk-card risk-${item.level}`} key={item.name}>
          <div>
            <span>{RISK_LABEL[item.level] ?? item.level}</span>
            <strong>{item.name}</strong>
          </div>
          <p>{item.summary}</p>
          {item.evidence.length > 0 ? <small>依据：{item.evidence.join("；")}</small> : null}
          {item.missingInputs.length > 0 ? <small>缺少：{item.missingInputs.join("、")}</small> : null}
        </article>
      ))}
    </div>
  );
}

function MissingInputs({ items }: { items: UltrasoundMissingInput[] }) {
  return (
    <div className="ultrasound-missing">
      <p className="ultrasound-evidence-title">关键缺失信息</p>
      <div className="ultrasound-missing-list">
        {items.map((item) => (
          <span key={`${item.category}-${item.name}`} title={item.reason}>
            {item.name}
          </span>
        ))}
      </div>
    </div>
  );
}

function DoctorQuestions({ items }: { items: string[] }) {
  return (
    <div className="ultrasound-questions">
      <p className="ultrasound-evidence-title">复诊时建议确认</p>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
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
