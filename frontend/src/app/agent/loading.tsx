export default function AgentLoading() {
  return (
    <div className="page-stack">
      <div className="page-loading-skeleton">
        <div className="skeleton-block skeleton-shimmer" style={{ height: 24, width: "30%", marginBottom: 20 }} />
        <div style={{ display: "grid", gridTemplateColumns: "240px 1fr 280px", gap: 20 }}>
          <div className="skeleton-card skeleton-shimmer" style={{ height: 400 }} />
          <div className="skeleton-card skeleton-shimmer" style={{ height: 400 }} />
          <div className="skeleton-card skeleton-shimmer" style={{ height: 400 }} />
        </div>
      </div>
    </div>
  );
}
