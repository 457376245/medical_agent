"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo } from "react";

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
              </div>

              <div className="home-disease-meta-simple">
                <p>共 {item.recordCount} 份报告</p>
              </div>

              <div className="home-disease-actions">
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
              </div>
            </article>
          );
        })}
      </div>

      {profiles.length === 0 && (
        <div style={{ marginTop: '20px', textAlign: 'center', color: '#607784' }}>
          <p>当前还没有疾病报告，点击右上角“上传”按钮创建第一份记录。</p>
        </div>
      )}
    </main>
  );
}
