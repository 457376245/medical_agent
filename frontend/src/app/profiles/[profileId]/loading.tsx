export default function ProfileLoading() {
  return (
    <div className="page-stack">
      <div className="page-loading-skeleton">
        <div className="skeleton-block skeleton-shimmer" style={{ height: 28, width: "50%", marginBottom: 12 }} />
        <div className="skeleton-block skeleton-shimmer" style={{ height: 18, width: "35%", marginBottom: 24 }} />
        <div style={{ display: "flex", gap: 16 }}>
          <div className="skeleton-card skeleton-shimmer" style={{ flex: "0 0 220px", height: 320 }} />
          <div className="skeleton-card skeleton-shimmer" style={{ flex: 1, height: 320 }} />
        </div>
      </div>
    </div>
  );
}
