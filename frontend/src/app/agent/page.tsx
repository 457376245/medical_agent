import Link from "next/link";

export default function AgentPage() {
  return (
    <main className="page-stack">
      <section className="panel">
        <p className="hero-kicker">医疗 Agent</p>
        <h2 style={{ fontFamily: "var(--font-heading)", marginTop: 0 }}>病情聊天分析入口（预留）</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          该页面用于后续接入医疗 Agent 对话能力，支持围绕病历记录进行问答、趋势分析与就医建议辅助。
        </p>

        <div className="agent-placeholder">
          <p>即将支持：</p>
          <ul className="guide-list" style={{ marginTop: 8 }}>
            <li>结合疾病时间线进行上下文问答</li>
            <li>自动提取关键指标变化并给出提示</li>
            <li>对话式生成复诊前准备清单</li>
          </ul>
        </div>

        <div className="actions" style={{ marginTop: 14 }}>
          <Link className="btn btn-ghost" href="/">
            返回时间线首页
          </Link>
        </div>
      </section>
    </main>
  );
}
