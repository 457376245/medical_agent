import { describe, expect, it } from "vitest";
import {
  buildMessagesFromTurns,
  createSseEventParser,
  getSessionDisplayTitle,
  normalizeSessionDetail,
  toRequestMetadata,
} from "./agent-utils";
import type { AgentSessionTurn } from "./types";

describe("buildMessagesFromTurns", () => {
  it("inserts a system message when turn metadata changes context", () => {
    const turns: AgentSessionTurn[] = [
      {
        threadId: "thread-1",
        turnId: "turn-1",
        turnIndex: 1,
        userMessage: "第一问",
        assistantMessage: "第一答",
        metadata: {
          disease_profile_id: "profile-1",
          disease_name: "糖尿病",
          record_id: "record-1",
          record_title: "化验单 A",
          entry: "agent_page",
        },
        traceEvents: [],
      },
      {
        threadId: "thread-1",
        turnId: "turn-2",
        turnIndex: 2,
        userMessage: "第二问",
        assistantMessage: "第二答",
        metadata: {
          disease_profile_id: "profile-1",
          disease_name: "糖尿病",
          record_id: "record-2",
          record_title: "化验单 B",
          entry: "agent_page",
        },
        traceEvents: [],
      },
    ];

    const messages = buildMessagesFromTurns(turns);

    expect(messages[0].role).toBe("system");
    expect(messages[3].role).toBe("system");
    expect(messages[3].content).toContain("化验单 B");
  });
});

describe("normalizeSessionDetail", () => {
  it("hydrates messages and trace events from turn payload", () => {
    const detail = normalizeSessionDetail({
      thread_id: "thread-2",
      title: "随访问答",
      turn_count: 1,
      turns: [
        {
          turn_id: "turn-1",
          turn_index: 1,
          thread_id: "thread-2",
          user_message: "帮我解释趋势",
          assistant_message: "趋势整体平稳。",
          metadata: {
            disease_profile_id: "profile-1",
            disease_name: "高血压",
            entry: "agent_page",
          },
          trace_events: [
            {
              event: "tool_call",
              tool: "parse_document",
              data: {
                input: { object_key: "records/a.pdf" },
              },
              created_at: "2026-04-02T12:00:00.000Z",
            },
          ],
        },
      ],
    });

    expect(detail.messages).toHaveLength(3);
    expect(detail.messages[1].role).toBe("user");
    expect(detail.messages[2].traceEvents?.[0].tool).toBe("parse_document");
  });
});

describe("createSseEventParser", () => {
  it("parses chunked SSE blocks incrementally", () => {
    const events: Array<{ event: string; data: Record<string, unknown> }> = [];
    const parser = createSseEventParser((event) => {
      events.push(event);
    });

    parser.push('event: session\ndata: {"thread_id":"abc"}\n\n');
    parser.push('event: token\ndata: {"content":"第一段"}\n');
    parser.push('\nevent: tool_result\ndata: {"tool":"parse_document","output":"完成"}\n\n');
    parser.flush();

    expect(events).toEqual([
      { event: "session", data: { thread_id: "abc" } },
      { event: "token", data: { content: "第一段" } },
      { event: "tool_result", data: { tool: "parse_document", output: "完成" } },
    ]);
  });
});

describe("toRequestMetadata", () => {
  it("keeps identifier-only metadata for agent context loading", () => {
    const metadata = toRequestMetadata({
      diseaseProfileId: "profile-1",
      diseaseName: "高血压",
      recordId: "record-1",
      recordTitle: "门诊检验",
    });

    expect(metadata).toEqual({
      disease_profile_id: "profile-1",
      disease_name: "高血压",
      record_id: "record-1",
      record_title: "门诊检验",
      entry: "agent_page",
    });
    expect((metadata as Record<string, unknown>).context_snapshot).toBeUndefined();
  });
});

describe("getSessionDisplayTitle", () => {
  it("prefers the first user question over backend summary text", () => {
    expect(
      getSessionDisplayTitle({
        title: "问题和回答混合摘要",
        lastUserMessage: "最近血常规指标变化如何\n请帮我解释一下",
      }),
    ).toBe("最近血常规指标变化如何 请帮我解释一下");
  });

  it("falls back to a trimmed session title when there is no user message", () => {
    const title = getSessionDisplayTitle({
      title: "  这是一个比较长的会话标题，会被裁剪成更适合侧边栏显示的形式并继续延长  ",
    });

    expect(title.endsWith("...")).toBe(true);
    expect(title.length).toBeLessThanOrEqual(33);
  });
});
