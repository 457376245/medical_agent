"use client";

import { ChevronDown, ChevronRight, Loader2, Search, Brain, CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { tracePreview } from "./agent-utils";
import type { AgentTraceEvent } from "./types";

function traceBody(event: AgentTraceEvent): string {
  if (event.event === "tool_call") {
    return JSON.stringify(event.data.input ?? event.data, null, 2);
  }
  if (event.event === "tool_result") {
    return String(event.data.output ?? "");
  }
  return String(event.data.message ?? "处理异常");
}

function getIconForTrace(event: AgentTraceEvent) {
  if (event.event === "tool_call") {
    const name = typeof event.data.name === "string" ? event.data.name : "";
    if (name.toLowerCase().includes("search") || name.toLowerCase().includes("query")) {
      return <Search className="agent-thought-process-icon agent-thought-process-icon-primary" aria-hidden="true" />;
    }
    return <Brain className="agent-thought-process-icon agent-thought-process-icon-primary" aria-hidden="true" />;
  }
  if (event.event === "tool_result") {
    return <CheckCircle2 className="agent-thought-process-icon agent-thought-process-icon-success" aria-hidden="true" />;
  }
  return <Loader2 className="agent-thought-process-icon agent-thought-process-icon-muted btn-loading-icon" aria-hidden="true" />;
}

export function AgentThoughtProcess({ events }: { events: AgentTraceEvent[] }) {
  const [expandedIndices, setExpandedIndices] = useState<Record<number, boolean>>({});

  const toggle = (index: number) => {
    setExpandedIndices(prev => ({ ...prev, [index]: !prev[index] }));
  };

  if (!events || events.length === 0) return null;

  return (
    <div className="agent-thought-process">
      <div className="agent-thought-process-head">
        <Brain className="agent-thought-process-heading-icon" aria-hidden="true" />
        <span className="agent-thought-process-title">智能分析过程</span>
      </div>
      {events.map((event, i) => {
        const body = traceBody(event);
        const shouldCollapse = event.event === "tool_result" && body.length > 60;
        const expanded = expandedIndices[i];
        const preview = tracePreview(event);
        return (
          <div key={i} className="agent-thought-process-entry">
            <div
              className={`agent-thought-process-row ${shouldCollapse ? "agent-thought-process-row-clickable" : ""}`}
              onClick={() => shouldCollapse && toggle(i)}
            >
              {shouldCollapse ? (
                expanded ? (
                  <ChevronDown className="agent-thought-process-toggle" aria-hidden="true" />
                ) : (
                  <ChevronRight className="agent-thought-process-toggle" aria-hidden="true" />
                )
              ) : (
                <span className="agent-thought-process-spacer" aria-hidden="true" />
              )}
              {getIconForTrace(event)}
              <span className="agent-thought-process-preview">{preview}</span>
            </div>
            {expanded && shouldCollapse && <div className="agent-thought-process-body">{body}</div>}
          </div>
        );
      })}
    </div>
  );
}
