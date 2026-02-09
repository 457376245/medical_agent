type SourceEvidence = {
  sourceFile: string;
  page?: number;
  snippet?: string;
};

export function SourceEvidencePanel({ evidence }: { evidence: SourceEvidence }) {
  return (
    <section className="panel">
      <p className="hero-kicker">证据追踪</p>
      <h3 className="panel-title-small">来源证据</h3>
      <p className="muted muted-tight">
        文件：<span className="mono">{evidence.sourceFile}</span>
      </p>
      {evidence.page !== undefined && (
        <p className="muted muted-tight">
          页码：{evidence.page}
        </p>
      )}
      {evidence.snippet && (
        <pre className="json-box json-box-readable mt-10">
          {evidence.snippet}
        </pre>
      )}
    </section>
  );
}
