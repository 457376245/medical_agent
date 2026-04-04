"use client";

import { memo } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AgentThoughtProcess } from "./AgentThoughtProcess";
import type { AgentMessage } from "./types";
import { Loader2 } from "lucide-react";

export const AgentMessageBubble = memo(function AgentMessageBubble({ message }: { message: AgentMessage }) {
  const isUser = message.role === "user";
  const isAssistant = message.role === "assistant";

  return (
    <div className={`agent-message agent-message-${message.role} flex flex-col gap-1 mb-6`}>
       <div className="agent-message-label text-sm font-semibold text-[var(--muted)] mb-1 px-1">
         {isUser ? "你" : isAssistant ? "健康助理" : "上下文知识"}
       </div>

       {isAssistant && message.traceEvents && message.traceEvents.length > 0 && (
         <AgentThoughtProcess events={message.traceEvents} />
       )}

       <div className={`agent-message-bubble p-4 rounded-2xl ${isUser ? 'bg-[var(--primary-soft)] text-[var(--ink)] self-end' : 'bg-[var(--surface-strong)] border border-[var(--line)] shadow-[var(--shadow-md)] text-[var(--ink)] self-start'} max-w-[90%]`}>
         {message.content ? (
           isAssistant ? (
             <div className="markdown-body">
               <ReactMarkdown 
                 remarkPlugins={[remarkGfm]}
                 components={{
                   a: ({node, ...props}) => {
                      if (props.href && props.children && String(props.children).match(/^(\[)?\d+(\])?$/)) {
                          return (
                             <a {...props} target="_blank" rel="noreferrer" className="inline-flex items-center justify-center min-w-[20px] h-5 px-1 text-[11px] font-bold text-[var(--primary)] bg-[var(--primary-soft)] rounded-full mx-0.5 no-underline hover:bg-[var(--primary)] hover:text-white transition-colors align-super border border-[var(--primary)]/20">
                               {String(props.children).replace(/\[|\]/g, '')}
                             </a>
                          )
                      }
                      return <a {...props} target="_blank" rel="noreferrer" className="text-[var(--primary)] hover:underline font-medium" />
                   }
                 }}
               >
                 {message.content}
               </ReactMarkdown>
             </div>
           ) : (
             <p className="whitespace-pre-wrap leading-relaxed m-0">{message.content}</p>
           )
        ) : (
          <p className="agent-message-loading">
             <Loader2 className="agent-message-loading-icon btn-loading-icon" aria-hidden="true" />
              正在仔细为您分析...
           </p>
         )}
         {message.errorMessage ? (
           <p className="status-text error mt-4 p-2 bg-[var(--danger-soft)] rounded text-[var(--danger)]">{message.errorMessage}</p>
         ) : null}
       </div>
    </div>
  );
});
