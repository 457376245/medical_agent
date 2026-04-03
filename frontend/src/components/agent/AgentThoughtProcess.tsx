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
   if (event.event === 'tool_call') {
     const name = typeof event.data.name === 'string' ? event.data.name : '';
     if (name.toLowerCase().includes('search') || name.toLowerCase().includes('query')) return <Search className="w-4 h-4 text-primary" />;
     return <Brain className="w-4 h-4 text-primary" />;
   }
   if (event.event === 'tool_result') {
     return <CheckCircle2 className="w-4 h-4 text-ok" />;
   }
   return <Loader2 className="w-4 h-4 text-muted animate-spin" />;
}

export function AgentThoughtProcess({ events }: { events: AgentTraceEvent[] }) {
  const [expandedIndices, setExpandedIndices] = useState<Record<number, boolean>>({});

  const toggle = (index: number) => {
    setExpandedIndices(prev => ({ ...prev, [index]: !prev[index] }));
  };

  if (!events || events.length === 0) return null;

  return (
    <div className="flex flex-col gap-2 mt-2 mb-4 p-4 bg-[var(--surface-strong)] rounded-xl border border-[var(--line)] shadow-[var(--shadow-md)] transition-all text-sm">
       <div className="flex items-center gap-2 mb-1">
         <Brain className="w-5 h-5 text-[var(--agent)]" />
         <span className="font-semibold text-[var(--ink)]">智能分析过程</span>
       </div>
       {events.map((event, i) => {
          const body = traceBody(event);
          const shouldCollapse = event.event === "tool_result" && body.length > 60;
          const expanded = expandedIndices[i];
          const preview = tracePreview(event);
          return (
             <div key={i} className="flex flex-col gap-1">
               <div
                 className={`flex items-center gap-2 py-1.5 px-2 rounded-lg transition-colors ${shouldCollapse ? 'cursor-pointer hover:bg-[var(--bg-start)]' : ''}`}
                 onClick={() => shouldCollapse && toggle(i)}
               >
                 {shouldCollapse ? (
                   expanded ? <ChevronDown className="w-4 h-4 text-[var(--muted)] shrink-0" /> : <ChevronRight className="w-4 h-4 text-[var(--muted)] shrink-0" />
                 ) : (
                    <div className="w-4" />
                 )}
                 {getIconForTrace(event)}
                 <span className="font-medium text-[var(--ink)] opacity-80 flex-1">{preview}</span>
               </div>
               {expanded && shouldCollapse && (
                  <div className="ml-8 mr-2 mt-1 mb-2 p-3 bg-slate-50/50 rounded-md overflow-x-auto text-xs text-[var(--muted)] font-mono border border-[var(--line)] max-h-48 overflow-y-auto">
                    {body}
                  </div>
               )}
             </div>
          );
       })}
    </div>
  );
}
