import Link from "next/link";
import { TimelineBatchList } from "../../components/timeline/TimelineBatchList";

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

export default async function TimelinePage() {
  let batches: Array<{ batchId: string; diseaseName: string; recordCount: number; latestRecordAt?: string }> = [];

  try {
    const res = await fetch(`${API_BASE}/timeline`, { cache: "no-store" });
    if (res.ok) {
      const payload = (await res.json()) as TimelineResponse;
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

  return (
    <main className="page-stack">
      <section className="panel reveal">
        <p className="hero-kicker">时间线总览</p>
        <h2 style={{ fontFamily: "var(--font-heading)", marginTop: 0 }}>全部疾病分组</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          这里展示系统内全部疾病记录分组，默认按最近报告日期排序。点击疾病名称可进入详细记录页。
        </p>
      </section>

      <section className="panel reveal reveal-delay-1">
        <h3 style={{ marginBottom: 10 }}>分组列表</h3>
        <div className="meta-row" style={{ marginTop: 0, marginBottom: 10 }}>
          <span className="badge">疾病分组：{batches.length}</span>
          <span className="badge">排序：最近报告优先</span>
        </div>
        <TimelineBatchList batches={batches} />

        {batches.length > 0 && (
          <div style={{ marginTop: 14 }}>
            <Link className="btn btn-ghost" href={`/timeline/${batches[0].batchId}`}>
              查看最新疾病分组
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}
