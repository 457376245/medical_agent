export default function UploadLoading() {
  return (
    <div className="page-stack">
      <div className="page-loading-skeleton">
        <div className="skeleton-block skeleton-shimmer" style={{ height: 28, width: "40%", marginBottom: 24 }} />
        <div className="skeleton-card skeleton-shimmer" style={{ height: 200 }} />
      </div>
    </div>
  );
}
