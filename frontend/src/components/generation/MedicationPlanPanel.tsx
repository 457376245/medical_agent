export function MedicationPlanPanel({ content, disclaimer }: { content: string; disclaimer: string }) {
  return (
    <section className="panel">
      <h2>用药方案草稿</h2>
      <p style={{ lineHeight: 1.5 }}>{content}</p>
      <small className="status-chip" style={{ marginTop: 10, display: "inline-flex" }}>
        {disclaimer}
      </small>
    </section>
  );
}
