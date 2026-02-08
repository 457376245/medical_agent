type SourceEvidence = {
  sourceFile: string;
  page?: number;
  snippet?: string;
};

export function SourceEvidencePanel({ evidence }: { evidence: SourceEvidence }) {
  return (
    <section className="panel">
      <p className="hero-kicker">证据追踪</p>
      <h3 style={{ marginBottom: 8 }}>来源证据</h3>
      <p className="muted" style={{ margin: "4px 0" }}>
        文件：<span className="mono">{evidence.sourceFile}</span>
      </p>
      {evidence.page !== undefined && (
        <p className="muted" style={{ margin: "4px 0" }}>
          页码：{evidence.page}
        </p>
      )}
      {evidence.snippet && (
        <pre className="json-box" style={{ marginTop: 10, fontFamily: "var(--font-body)" }}>
          {evidence.snippet}
        </pre>
      )}
    </section>
  );
}
