import Link from "next/link";

type TimelineBatch = {
  batchId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
};

export function TimelineBatchList({ batches }: { batches: TimelineBatch[] }) {
  if (batches.length === 0) {
    return <p className="muted">暂无疾病时间线分组，请先通过右上角“上传”按钮添加病历。</p>;
  }

  return (
    <ul className="timeline-list">
      {batches.map((batch) => (
        <li className="timeline-item" key={batch.batchId}>
          <div>
            <Link className="timeline-node-link" href={`/timeline/${batch.batchId}`}>
              <strong>{batch.diseaseName}</strong>
            </Link>
            <p className="muted" style={{ margin: "4px 0 0" }}>
              最近报告日期：{batch.latestRecordAt ?? "暂无"}
            </p>
            <p className="muted mono" style={{ margin: "4px 0 0" }}>
              分组编号：{batch.batchId}
            </p>
          </div>
          <span className="badge">{batch.recordCount} 条记录</span>
        </li>
      ))}
    </ul>
  );
}
