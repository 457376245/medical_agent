import Link from "next/link";
import { StructuredResultTable } from "../../../components/parse/StructuredResultTable";
import { DeleteRecordButton } from "../../../components/timeline/DeleteRecordButton";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

type BatchDetailResponse = {
  data?: {
    records?: Array<{ id?: string; title?: string; record_date?: string; recordDate?: string }>;
  };
};

type RecordDetailResponse = {
  data?: {
    summary?: string;
    structuredResult?: { schemaVersion?: string; revision?: number; payload?: unknown };
  };
};

export default async function TimelineBatchDetailPage({
  params,
  searchParams,
}: {
  params: { batchId: string };
  searchParams?: { recordId?: string };
}) {
  let records: Array<{ id: string; title: string; recordDate: string }> = [];
  let selectedRecord: { id: string; summary: string; schemaVersion: string; revision: number; payload: unknown } | null = null;
  const selectedRecordId = searchParams?.recordId;

  try {
    const res = await fetch(`${API_BASE}/timeline/${params.batchId}`, { cache: "no-store" });
    if (res.ok) {
      const payload = (await res.json()) as BatchDetailResponse;
      records = (payload.data?.records ?? []).map((item) => ({
        id: item.id ?? "未知记录",
        title: item.title ?? "未命名报告",
        recordDate: item.recordDate ?? item.record_date ?? "暂无",
      }));

      const fetchRecordDetail = async (recordId: string) => {
        const detailResp = await fetch(`${API_BASE}/records/${recordId}`, { cache: "no-store" });
        if (!detailResp.ok) {
          return null;
        }
        const detailPayload = (await detailResp.json()) as RecordDetailResponse;
        return {
          id: recordId,
          summary: detailPayload.data?.summary ?? "暂无摘要。",
          schemaVersion: detailPayload.data?.structuredResult?.schemaVersion ?? "v1",
          revision: detailPayload.data?.structuredResult?.revision ?? 0,
          payload: detailPayload.data?.structuredResult?.payload ?? {},
        };
      };

      const firstRecordId = records[0]?.id;
      const targetRecordId = selectedRecordId ?? firstRecordId;
      if (targetRecordId) {
        selectedRecord = await fetchRecordDetail(targetRecordId);
      }
      if (!selectedRecord && selectedRecordId && firstRecordId && firstRecordId !== selectedRecordId) {
        selectedRecord = await fetchRecordDetail(firstRecordId);
      }
    }
  } catch {
    records = [];
    selectedRecord = null;
  }

  return (
    <main className="page-stack">
      <section className="panel reveal">
        <p className="hero-kicker">疾病详情</p>
        <h2 className="panel-title">疾病分组详情</h2>
        <p className="panel-text">
          当前疾病分组 <span className="mono">{params.batchId}</span> 按时间倒序展示报告记录，点击任一记录可查看解析摘要与结构化结果。
        </p>
      </section>

      <section className="split-layout reveal reveal-delay-1">
        <article className="panel">
          <h3 className="panel-title-small">该疾病下的报告列表</h3>
          {records.length === 0 ? (
            <p className="muted">该分组暂时没有可展示的记录。</p>
          ) : (
            <ul className="timeline-list">
              {records.map((record) => (
                <li
                  className={`timeline-item ${selectedRecord?.id === record.id ? "active" : ""}`}
                  key={record.id}
                >
                  <div>
                    <Link className="timeline-node-link" href={`/timeline/${params.batchId}?recordId=${record.id}`}>
                      <strong>{record.title}</strong>
                    </Link>
                    <p className="muted muted-tight">
                      记录 ID：<span className="mono">{record.id}</span>
                    </p>
                  </div>
                  <div className="timeline-item-side">
                    <span className="badge">{record.recordDate}</span>
                    <DeleteRecordButton
                      recordId={record.id}
                      batchId={params.batchId}
                      isSelected={selectedRecord?.id === record.id}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="panel">
          <h3 className="panel-title-small">解析结果详情</h3>
          {!selectedRecord ? (
            <p className="muted">请选择左侧任一报告节点查看解析内容。</p>
          ) : (
            <>
              <p className="muted panel-subtitle">
                记录 ID：<span className="mono">{selectedRecord.id}</span> | 结构版本 {selectedRecord.schemaVersion} |
                修订版本 {selectedRecord.revision}
              </p>
              <div className="summary-block">
                <h4 className="summary-heading">摘要</h4>
                <p className="paragraph-relaxed">{selectedRecord.summary}</p>
              </div>
              <h4 className="summary-heading">结构化解析结果</h4>
              <StructuredResultTable payload={selectedRecord.payload} />
            </>
          )}
        </article>
      </section>
    </main>
  );
}
