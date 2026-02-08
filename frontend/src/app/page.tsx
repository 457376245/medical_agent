import Link from "next/link";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

type TimelineResponse = {
  data?: {
    batches?: Array<{
      batch_id?: string;
      batchId?: string;
      disease_name?: string;
      diseaseName?: string;
      record_count?: number;
      recordCount?: number;
      latest_record_at?: string;
      latestRecordAt?: string;
    }>;
  };
};

export default async function HomePage() {
  let batches: Array<{ batchId: string; diseaseName: string; recordCount: number; latestRecordAt?: string }> = [];

  try {
    const response = await fetch(`${API_BASE}/timeline`, { cache: "no-store" });
    if (response.ok) {
      const payload = (await response.json()) as TimelineResponse;
      batches = (payload.data?.batches ?? []).map((item) => ({
        batchId: item.batchId ?? item.batch_id ?? "unknown-batch",
        diseaseName: item.diseaseName ?? item.disease_name ?? "未分类疾病",
        recordCount: item.recordCount ?? item.record_count ?? 0,
        latestRecordAt: item.latestRecordAt ?? item.latest_record_at,
      }));
    }
  } catch {
    batches = [];
  }

  const totalRecords = batches.reduce((sum, batch) => sum + batch.recordCount, 0);
  const latestUpdate = batches[0]?.latestRecordAt ?? "暂无";

  return (
    <main className="timeline-home">
      <section className="timeline-hero">
        <p className="hero-tag">主页面</p>
        <h2 style={{ fontFamily: "var(--font-heading)" }}>用户疾病记录时间线</h2>
        <p>按疾病维度查看所有病历记录，重点关注最近更新，支持快速进入单个疾病分组查看详细报告。</p>

        <div className="hero-stats">
          <article>
            <span>疾病分组</span>
            <strong>{batches.length}</strong>
          </article>
          <article>
            <span>累计记录</span>
            <strong>{totalRecords}</strong>
          </article>
          <article>
            <span>最近更新</span>
            <strong>{latestUpdate}</strong>
          </article>
        </div>
      </section>

      <section className="timeline-section">
        <div className="timeline-section-head">
          <h3>按疾病排序的时间线</h3>
          <Link className="timeline-more-link" href="/timeline">
            查看完整列表
          </Link>
        </div>

        {batches.length === 0 ? (
          <p className="empty-tip">当前还没有任何疾病记录，请点击右上角“上传”按钮添加第一份病历。</p>
        ) : (
          <ul className="disease-timeline">
            {batches.map((batch) => (
              <li key={batch.batchId} className="disease-item">
                <span className="disease-marker" aria-hidden="true" />
                <article className="disease-card">
                  <div className="disease-card-head">
                    <div>
                      <h4>{batch.diseaseName}</h4>
                      <p className="muted">最近报告日期：{batch.latestRecordAt ?? "暂无"}</p>
                    </div>
                    <span className="mini-chip">{batch.recordCount} 条记录</span>
                  </div>
                  <p className="mono">分组编号：{batch.batchId}</p>
                  <Link className="timeline-detail-link" href={`/timeline/${batch.batchId}`}>
                    进入该疾病详情
                  </Link>
                </article>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="timeline-section guide-panel">
        <h3>使用说明</h3>
        <ul className="guide-list">
          <li>右上角“上传”按钮用于新增病历并指定疾病分类，支持直接新增新疾病。</li>
          <li>右上角“Agent”按钮为后续医疗对话分析入口，当前版本已预留页面。</li>
          <li>时间线默认按最近报告日期排序，方便快速定位最新变化。</li>
        </ul>
      </section>
    </main>
  );
}
