"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState, useCallback, useEffect } from "react";

type HomeProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
  latestRecordId?: string;
  latestRecordTitle?: string;
  latestParseStatus?: string;
};

type HomeOverviewProps = {
  profiles: HomeProfile[];
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
      const res = await fetch(`${API_BASE}/disease-profiles`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
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
      const res = await fetch(
        `${API_BASE}/disease-profiles/${encodeURIComponent(deleteTarget.profileId)}`,
        { method: "DELETE", credentials: "include" },
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

        {profiles.map((item) => {
          const status = statusMeta(item.latestParseStatus);
          
          return (
            <article className="home-disease-card" key={item.profileId}>
              <div className="home-disease-card-top">
                <h4>{item.diseaseName}</h4>
                <div className="home-disease-card-top-actions">
                  <div className="home-disease-card-status" title={status.label}>
                    {status.label === "已解析" && (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <circle cx="12" cy="12" r="10" fill="#e8f8ef" stroke="#b9e2ce" strokeWidth="1.5"/>
                        <path d="M8 12.5L10.5 15L16 9" stroke="#1f7a53" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                    {status.label === "解析中" && (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <circle cx="12" cy="12" r="10" fill="#fff4e2" stroke="#eed3a8" strokeWidth="1.5"/>
                        <path d="M12 8V12L14.5 14.5" stroke="#9a611f" strokeWidth="2" strokeLinecap="round"/>
                      </svg>
                    )}
                    {(status.label === "需处理" || status.label === "未解析") && (
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <circle cx="12" cy="12" r="10" fill="#fcebe8" stroke="#f0c7bf" strokeWidth="1.5"/>
                        <path d="M12 8V13M12 16H12.01" stroke="#b74b3b" strokeWidth="2" strokeLinecap="round"/>
                      </svg>
                    )}
                  </div>
                  <button
                    className="home-disease-delete-btn"
                    type="button"
                    onClick={() => setDeleteTarget(item)}
                    aria-label={`删除 ${item.diseaseName}`}
                    title="删除该疾病分类"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M3 6H5H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6H19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M10 11V17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M14 11V17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </button>
                </div>
              </div>

              <div className="home-disease-meta-simple">
                <p>共 {item.recordCount} 份报告</p>
              </div>

              <div className="home-disease-actions">
                {item.recordCount > 0 && (
                  <Link
                    className="home-view-btn-full"
                    href={`/profiles/${encodeURIComponent(item.profileId)}`}
                    onClick={(e) => {
                      const el = e.currentTarget;
                      el.innerHTML = `
                        <svg class="btn-loading-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M21 12a9 9 0 1 1-6.219-8.56"></path>
                        </svg>
                        加载中...
                      `;
                      el.style.pointerEvents = 'none';
                      el.style.opacity = '0.7';
                    }}
                  >
                    查看分析
                  </Link>
                )}
                <button
                  className="home-upload-mini-btn"
                  type="button"
                  onClick={() => openUploadDialog(item.profileId, item.diseaseName)}
                  aria-label={`为 ${item.diseaseName} 上传报告`}
                  title="上传同类报告"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 4V16M12 4L8 8M12 4L16 8M4 20H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  <span>上传报告</span>
                </button>
              </div>
            </article>
          );
        })}

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
        <div style={{ marginTop: '20px', textAlign: 'center', color: '#607784' }}>
          <p>当前还没有疾病报告，点击右上角"上传"按钮创建第一份记录。</p>
        </div>
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
