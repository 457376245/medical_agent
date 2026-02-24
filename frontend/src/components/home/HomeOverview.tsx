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

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";
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
    <main className="home-dashboard">
      <section className="home-hero-card reveal">
        <div className="home-hero-top">
          <div className="home-hero-copy">
            <p className="home-hero-kicker">健康档案总览</p>
            <h2 className="home-hero-title">我的健康档案</h2>
            <p className="home-hero-desc">按疾病追踪报告变化，快速定位需要关注的检查结果。</p>
          </div>
          <div className="home-hero-actions">
            <button className="home-pill-btn home-pill-btn-primary" type="button" onClick={() => openUploadDialog()}>
              上传报告
            </button>
            <Link className="home-pill-btn home-pill-btn-ghost" href="/agent">
              AI 智能分析
            </Link>
          </div>
        </div>

        <div className="home-status-grid">
          <article className="home-status-card home-status-processing">
            <div className="home-status-icon" aria-hidden="true">
              ⟳
            </div>
            <div className="home-status-content">
              <p>处理中</p>
              <strong>{overview.processing}</strong>
            </div>
          </article>
          <article className="home-status-card home-status-done">
            <div className="home-status-icon" aria-hidden="true">
              ✓
            </div>
            <div className="home-status-content">
              <p>已完成</p>
              <strong>{overview.parsed}</strong>
            </div>
          </article>
          <article className="home-status-card home-status-pending">
            <div className="home-status-icon" aria-hidden="true">
              !
            </div>
            <div className="home-status-content">
              <p>待处理</p>
              <strong>{overview.needAttention}</strong>
            </div>
          </article>
        </div>
      </section>

      <section className="home-disease-section reveal reveal-delay-1">
        <div className="home-disease-headline">
          <h3>疾病分类</h3>
          <p>选择分类可查看该疾病下全部报告与趋势详情。</p>
        </div>

        {batches.length === 0 ? (
          <p className="empty-tip">当前还没有疾病报告，点击上方“上传报告”创建第一份记录。</p>
        ) : (
          <div className="home-disease-grid">
            {batches.map((item) => {
              const status = statusMeta(item.latestParseStatus);
              const canDelete = item.batchId !== "unknown";
              const isDeleting = deletingBatchId === item.batchId;
              return (
                <article className="home-disease-card" key={item.batchId}>
                  <div className="home-disease-card-top">
                    <div>
                      <h4>{item.diseaseName}</h4>
                      <p className="home-disease-count">{item.recordCount} 份报告</p>
                    </div>
                    <div className="home-disease-card-top-right">
                      <span className={`status-chip ${status.className}`}>{status.label}</span>
                      <button
                        className="home-delete-chip"
                        type="button"
                        onClick={() => openDeleteDialog(item.batchId, item.diseaseName)}
                        disabled={!canDelete || isDeleting || deletingBatchId !== null}
                        aria-label={`删除疾病 ${item.diseaseName}`}
                        title={canDelete ? `删除 ${item.diseaseName}` : `${item.diseaseName} 下有报告，不能删除`}
                      >
                        {isDeleting ? "删除中" : "删除"}
                      </button>
                    </div>
                  </div>

                  <div className="home-disease-meta">
                    <p className="home-meta-label">最近报告日期</p>
                    <p className="home-meta-date">{formatDate(item.latestRecordAt)}</p>
                    <p className="home-meta-title">最新标题：{item.latestRecordTitle ?? "暂无"}</p>
                  </div>

                  <div className="home-disease-actions">
                    <Link
                      className="home-view-btn"
                      href={`/timeline?batchId=${encodeURIComponent(item.batchId)}&diseaseName=${encodeURIComponent(item.diseaseName)}`}
                    >
                      查看详情
                      <span aria-hidden="true">→</span>
                    </Link>
                    <button
                      className="home-upload-mini-btn"
                      type="button"
                      onClick={() => openUploadDialog(item.batchId, item.diseaseName)}
                    >
                      上传同类报告
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {deleteError ? <p className="status-text error">{deleteError}</p> : null}

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
    </main>
  );
}
