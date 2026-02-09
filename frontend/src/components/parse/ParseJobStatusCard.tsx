type ParseJobStatusCardProps = {
  status: string;
  progress: number;
};

export function ParseJobStatusCard({ status, progress }: ParseJobStatusCardProps) {
  const normalized = status.toUpperCase();
  const stateClass = normalized === "SUCCESS" ? "status-success" : normalized === "FAILED" ? "status-error" : "";
  const labelMap: Record<string, string> = {
    QUEUED: "排队中",
    PROCESSING: "解析中",
    RETRYING: "重试中",
    SUCCESS: "已完成",
    FAILED: "失败",
  };
  const statusLabel = labelMap[normalized] ?? normalized;

  return (
    <section className="panel">
      <p className="hero-kicker">处理进度</p>
      <h2 className="panel-title">解析任务状态</h2>
      <div className={`status-chip ${stateClass}`}>{statusLabel}</div>
      <p className="muted mt-10">
        进度：{progress}%
      </p>
      <div className="progress-wrap" aria-label="解析任务进度条">
        <div className="progress-bar" style={{ width: `${Math.max(0, Math.min(progress, 100))}%` }} />
      </div>
    </section>
  );
}
