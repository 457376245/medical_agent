export function MedicationPlanPanel({ content, disclaimer }: { content: string; disclaimer: string }) {
  return (
    <section className="panel">
      <h2 className="panel-title">用药方案草稿</h2>
      <p className="paragraph-relaxed">{content}</p>
      <small className="status-chip mt-10">
        {disclaimer}
      </small>
    </section>
  );
}
