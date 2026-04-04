"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState, useCallback, useEffect } from "react";
import { DiseaseCard, type HomeProfile } from "./DiseaseCard";
import { EmptyState } from "../shared/EmptyState";
import { authFetch } from "../../lib/api";

type HomeOverviewProps = {
  profiles: HomeProfile[];
};

const IN_PROGRESS_STATUS = new Set(["QUEUED", "PROCESSING", "RETRYING"]);

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

export function HomeOverview({ profiles }: HomeOverviewProps) {
  const router = useRouter();
  const [isAddingDisease, setIsAddingDisease] = useState(false);
  const [newDiseaseName, setNewDiseaseName] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const [deleteTarget, setDeleteTarget] = useState<HomeProfile | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (isAddingDisease && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isAddingDisease]);

  const handleCreateDisease = useCallback(async () => {
    const trimmed = newDiseaseName.trim();
    if (!trimmed) return;

    setIsCreating(true);
    setCreateError(null);

    try {
      const res = await authFetch("/disease-profiles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });

      if (!res.ok) {
        const body = await res.text();
        if (res.status === 409 || body.includes("already exists") || body.includes("Duplicate")) {
          setCreateError("该疾病分类已存在");
        } else {
          setCreateError("创建失败，请重试");
        }
        return;
      }

      setNewDiseaseName("");
      setIsAddingDisease(false);
      setCreateError(null);
      router.refresh();
    } catch {
      setCreateError("网络错误，请重试");
    } finally {
      setIsCreating(false);
    }
  }, [newDiseaseName, router]);

  const cancelAddDisease = useCallback(() => {
    setIsAddingDisease(false);
    setNewDiseaseName("");
    setCreateError(null);
  }, []);

  const handleDeleteDisease = useCallback(async () => {
    if (!deleteTarget) return;

    setIsDeleting(true);
    setDeleteError(null);

    try {
      const res = await authFetch(
        `/disease-profiles/${encodeURIComponent(deleteTarget.profileId)}`,
        { method: "DELETE" },
      );

      if (!res.ok) {
        setDeleteError("删除失败，请重试");
        return;
      }

      setDeleteTarget(null);
      setDeleteError(null);
      router.refresh();
    } catch {
      setDeleteError("网络错误，请重试");
    } finally {
      setIsDeleting(false);
    }
  }, [deleteTarget, router]);

  const cancelDelete = useCallback(() => {
    setDeleteTarget(null);
    setDeleteError(null);
  }, []);

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

  const overview = useMemo(() => {
    let processing = 0;
    let parsed = 0;
    let needAttention = 0;

    for (const item of profiles) {
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
  }, [profiles]);

  return (
    <main className="home-dashboard">
      <section className="home-agent-hero">
        <div className="home-agent-hero-copy">
          <h2 className="home-agent-hero-title">
            <svg className="home-agent-hero-icon" width="24" height="24" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            全局医疗 Agent 助理
          </h2>
          <p className="home-agent-hero-text">不需要选中特定疾病档案，直接在此向您的专属医疗 AI 助理发起提问或健康咨询。</p>
        </div>
        <Link href="/agent" className="home-agent-hero-link">
          立即对话
        </Link>
      </section>

      <div className="home-disease-grid">
        <article className="home-profile-card">
          <div className="home-profile-header">
            <h2>我的健康档案</h2>
            <div className="home-profile-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 12C14.21 12 16 10.21 16 8C16 5.79 14.21 4 12 4C9.79 4 8 5.79 8 8C8 10.21 9.79 12 12 12ZM12 14C9.33 14 4 15.34 4 18V20H20V18C20 15.34 14.67 14 12 14Z" fill="currentColor"/>
              </svg>
            </div>
          </div>
          <div className="home-profile-summary">
            <p className="summary-title">档案总览</p>
            <div className="summary-stats">
              <div className="stat-item">
                <p>已处理报告</p>
                <strong>{overview.parsed}</strong>
              </div>
              <div className="stat-item">
                <p>最近更新</p>
                <strong>{profiles.length > 0 ? formatDate(profiles.reduce((latest, item) => {
                  if (!latest) return item.latestRecordAt;
                  if (!item.latestRecordAt) return latest;
                  return new Date(item.latestRecordAt) > new Date(latest) ? item.latestRecordAt : latest;
                }, undefined as string | undefined)) : "暂无"}</strong>
              </div>
            </div>
          </div>
        </article>

        {profiles.map((item) => (
          <DiseaseCard
            key={item.profileId}
            profile={item}
            onDelete={setDeleteTarget}
            onUpload={openUploadDialog}
          />
        ))}

        <article
          className={`home-disease-card add-disease-card${isAddingDisease ? " add-disease-card--editing" : ""}`}
          onClick={() => { if (!isAddingDisease) setIsAddingDisease(true); }}
          onKeyDown={(e) => { if (!isAddingDisease && e.key === "Enter") setIsAddingDisease(true); }}
          role="button"
          tabIndex={isAddingDisease ? -1 : 0}
        >
          {!isAddingDisease ? (
            <div className="add-disease-content">
              <div className="add-icon-wrapper">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 4V20M4 12H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <h4>新增疾病分类</h4>
              <p>创建新的疾病类别</p>
            </div>
          ) : (
            <div className="add-disease-form">
              <div className="add-icon-wrapper add-icon-wrapper--small">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 4V20M4 12H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <p className="add-disease-form-label">输入疾病分类名称</p>
              <input
                ref={inputRef}
                className="add-disease-input"
                type="text"
                placeholder="例如：高血压、糖尿病"
                value={newDiseaseName}
                onChange={(e) => { setNewDiseaseName(e.target.value); setCreateError(null); }}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !isCreating) handleCreateDisease();
                  if (e.key === "Escape") cancelAddDisease();
                }}
                disabled={isCreating}
                maxLength={50}
              />
              {createError && <p className="add-disease-error">{createError}</p>}
              <div className="add-disease-form-actions">
                <button
                  className="add-disease-confirm-btn"
                  type="button"
                  onClick={handleCreateDisease}
                  disabled={isCreating || !newDiseaseName.trim()}
                >
                  {isCreating ? (
                    <>
                      <svg className="btn-loading-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21 12a9 9 0 1 1-6.219-8.56"></path>
                      </svg>
                      创建中...
                    </>
                  ) : "确认创建"}
                </button>
                <button
                  className="add-disease-cancel-btn"
                  type="button"
                  onClick={cancelAddDisease}
                  disabled={isCreating}
                >
                  取消
                </button>
              </div>
            </div>
          )}
        </article>
      </div>

      {profiles.length === 0 && (
        <EmptyState
          title="还没有疾病报告"
          description={'点击右上角"上传"按钮创建第一份记录。'}
        />
      )}

      {deleteTarget && (
        <div className="delete-dialog-overlay" onClick={cancelDelete}>
          <div className="delete-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="delete-dialog-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="12" cy="12" r="10" fill="#fcebe8" stroke="#f0c7bf" strokeWidth="1.5"/>
                <path d="M12 8V13M12 16H12.01" stroke="#b74b3b" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <h3 className="delete-dialog-title">确认删除</h3>
            <p className="delete-dialog-message">
              确定要删除疾病分类「{deleteTarget.diseaseName}」吗？
            </p>
            {deleteTarget.recordCount > 0 && (
              <p className="delete-dialog-warning">
                该分类下共有 <strong>{deleteTarget.recordCount}</strong> 份报告，删除后所有报告及分析数据将被永久清除，无法恢复。
              </p>
            )}
            {deleteError && <p className="delete-dialog-error">{deleteError}</p>}
            <div className="delete-dialog-actions">
              <button
                className="delete-dialog-cancel-btn"
                type="button"
                onClick={cancelDelete}
                disabled={isDeleting}
              >
                取消
              </button>
              <button
                className="delete-dialog-confirm-btn"
                type="button"
                onClick={handleDeleteDisease}
                disabled={isDeleting}
              >
                {isDeleting ? (
                  <>
                    <svg className="btn-loading-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 12a9 9 0 1 1-6.219-8.56"></path>
                    </svg>
                    删除中...
                  </>
                ) : "确认删除"}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
