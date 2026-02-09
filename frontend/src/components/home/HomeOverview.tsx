"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { ConfirmDialog } from "../common/ConfirmDialog";

type HomeBatch = {
  batchId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
  latestRecordId?: string;
  latestRecordTitle?: string;
  latestParseStatus?: string;
};

type HomeOverviewProps = {
  batches: HomeBatch[];
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
const IN_PROGRESS_STATUS = new Set(["QUEUED", "PROCESSING", "RETRYING"]);
const ATTENTION_STATUS = new Set(["FAILED", "DEAD_LETTER"]);

function formatDate(value?: string): string {
  if (!value) {
    return "暂无";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleDateString("zh-CN");
}

function statusMeta(raw?: string): { label: string; className: string } {
  const status = (raw ?? "NOT_PARSED").toUpperCase();
  if (status === "SUCCESS") {
    return { label: "已解析", className: "status-success" };
  }
  if (IN_PROGRESS_STATUS.has(status)) {
    return { label: "解析中", className: "status-processing" };
  }
  if (ATTENTION_STATUS.has(status)) {
    return { label: "需处理", className: "status-error" };
  }
  return { label: "未解析", className: "" };
}

export function HomeOverview({ batches }: HomeOverviewProps) {
  const router = useRouter();
  const [deletingBatchId, setDeletingBatchId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState("");
  const [pendingDelete, setPendingDelete] = useState<{ batchId: string; diseaseName: string } | null>(null);

  const overview = useMemo(() => {
    let processing = 0;
    let parsed = 0;
    let needAttention = 0;

    for (const item of batches) {
      const status = (item.latestParseStatus ?? "NOT_PARSED").toUpperCase();
      if (status === "SUCCESS") {
        parsed += 1;
      } else if (IN_PROGRESS_STATUS.has(status)) {
        processing += 1;
      } else {
        needAttention += 1;
      }
    }

    return { processing, parsed, needAttention };
  }, [batches]);

  const openUploadDialog = (diseaseProfileId?: string, diseaseName?: string) => {
    window.dispatchEvent(
      new CustomEvent("open-upload-dialog", {
        detail: {
          diseaseProfileId,
          diseaseName,
        },
      }),
    );
  };

  const openDeleteDialog = (batchId: string, diseaseName: string) => {
    if (batchId === "unknown") {
      return;
    }
    setPendingDelete({ batchId, diseaseName });
  };

  const closeDeleteDialog = () => {
    if (deletingBatchId) {
      return;
    }
    setPendingDelete(null);
  };

  const deleteDisease = async () => {
    if (!pendingDelete) {
      return;
    }

    const { batchId } = pendingDelete;
    setDeletingBatchId(batchId);
    setDeleteError("");
    try {
      const response = await fetch(`${API_BASE}/disease-profiles/${batchId}`, { method: "DELETE" });
      if (!response.ok) {
        throw new Error("delete disease failed");
      }
      setPendingDelete(null);
      router.refresh();
    } catch {
      setDeleteError("删除疾病失败，请稍后重试。");
    } finally {
      setDeletingBatchId(null);
    }
  };

  return (
    <main className="timeline-home">
      <section className="timeline-section">
        <div className="timeline-section-head">
          <h3>疾病分类卡片</h3>
        </div>

        {batches.length === 0 ? (
          <p className="empty-tip">当前还没有任何疾病分类记录，点击上方“上传报告”添加第一份病历。</p>
        ) : (
          <div className="disease-focus-grid">
            {batches.map((item) => {
              const status = statusMeta(item.latestParseStatus);
              const canDelete = item.batchId !== "unknown";
              const isDeleting = deletingBatchId === item.batchId;
              return (
                <article className="disease-focus-card" key={item.batchId}>
                  <div className="disease-focus-head">
                    <h4>{item.diseaseName}</h4>
                    <span className={`status-chip ${status.className}`}>{status.label}</span>
                  </div>

                  <div className="disease-focus-meta">
                    <p className="muted">
                      报告数量：<strong>{item.recordCount}</strong>
                    </p>
                    <p className="muted">最近报告：{formatDate(item.latestRecordAt)}</p>
                    <p className="muted">最新标题：{item.latestRecordTitle ?? "暂无"}</p>
                  </div>

                  <div className="disease-focus-actions">
                    <Link
                      className="btn btn-primary"
                      href={`/timeline?batchId=${encodeURIComponent(item.batchId)}&diseaseName=${encodeURIComponent(item.diseaseName)}`}
                    >
                      进入疾病报告
                    </Link>
                    <button
                      className="btn btn-ghost"
                      type="button"
                      onClick={() => openUploadDialog(item.batchId, item.diseaseName)}
                    >
                      新增该疾病报告
                    </button>
                    <button
                      className="btn btn-danger"
                      type="button"
                      onClick={() => openDeleteDialog(item.batchId, item.diseaseName)}
                      disabled={!canDelete || isDeleting || deletingBatchId !== null}
                    >
                      {isDeleting ? "删除中..." : "删除疾病"}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
        {deleteError ? <p className="status-text error">{deleteError}</p> : null}
      </section>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="确认删除疾病分类"
        description={`确认删除“${pendingDelete?.diseaseName ?? ""}”吗？该疾病下全部报告与图片将被永久删除。`}
        confirmText="确认删除"
        tone="danger"
        loading={deletingBatchId !== null}
        onCancel={closeDeleteDialog}
        onConfirm={deleteDisease}
      />

      <section className="timeline-hero">
        <h3 className="timeline-management-title">我的疾病报告管理</h3>
        <p>以疾病分类为主线管理报告：先选择疾病，再进入该疾病下查看和维护所有历史记录。</p>

        <div className="hero-actions">
          <button className="btn btn-primary" type="button" onClick={() => openUploadDialog()}>
            上传报告
          </button>
        </div>

        <div className="hero-stats">
          <article>
            <span>解析中</span>
            <strong>{overview.processing}</strong>
          </article>
          <article>
            <span>已完成</span>
            <strong>{overview.parsed}</strong>
          </article>
          <article>
            <span>待处理</span>
            <strong>{overview.needAttention}</strong>
          </article>
        </div>
      </section>

    </main>
  );
}
