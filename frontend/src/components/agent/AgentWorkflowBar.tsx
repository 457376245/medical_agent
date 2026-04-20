"use client";

import { WORKFLOW_LABELS } from "./agent-utils";
import type { AgentWorkflow } from "./types";

const WORKFLOW_OPTIONS: Array<{ value: AgentWorkflow; label: string; hint: string }> = [
  { value: "report_interpretation", label: WORKFLOW_LABELS.report_interpretation, hint: "聚焦异常指标、规则结论和趋势含义" },
  { value: "follow_up_prep", label: WORKFLOW_LABELS.follow_up_prep, hint: "输出复查项、携带资料和行动清单" },
  { value: "medication_review", label: WORKFLOW_LABELS.medication_review, hint: "结合长期用药和当前指标做风险回顾" },
];

export function AgentWorkflowBar({
  workflow,
  onChange,
}: {
  workflow: AgentWorkflow;
  onChange: (workflow: AgentWorkflow) => void;
}) {
  return (
    <div className="agent-workflow-bar" role="tablist" aria-label="对话工作流">
      {WORKFLOW_OPTIONS.map((option) => (
        <button
          key={option.value}
          className={`agent-workflow-pill ${workflow === option.value ? "active" : ""}`}
          type="button"
          role="tab"
          aria-selected={workflow === option.value}
          onClick={() => onChange(option.value)}
        >
          <strong>{option.label}</strong>
          <span>{option.hint}</span>
        </button>
      ))}
    </div>
  );
}
