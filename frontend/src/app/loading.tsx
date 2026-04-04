export default function HomeLoading() {
  return (
    <div className="page-stack">
      <div className="page-loading-skeleton">
        <div className="skeleton-block skeleton-shimmer" style={{ height: 32, width: "40%", marginBottom: 16 }} />
        <div className="skeleton-grid">
          {[1, 2, 3].map((i) => (
            <div key={i} className="skeleton-card skeleton-shimmer" />
          ))}
        </div>
      </div>
    </div>
  );
}
