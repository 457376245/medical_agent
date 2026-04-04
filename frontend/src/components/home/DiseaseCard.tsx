"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

export type HomeProfile = {
  profileId: string;
  diseaseName: string;
  recordCount: number;
  latestRecordAt?: string;
  latestRecordId?: string;
  latestRecordTitle?: string;
  latestParseStatus?: string;
};

const IN_PROGRESS_STATUS = new Set(["QUEUED", "PROCESSING", "RETRYING"]);
const ATTENTION_STATUS = new Set(["FAILED", "DEAD_LETTER"]);

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

type DiseaseCardProps = {
  profile: HomeProfile;
  onDelete: (profile: HomeProfile) => void;
  onUpload: (profileId: string, diseaseName: string) => void;
};

export function DiseaseCard({ profile, onDelete, onUpload }: DiseaseCardProps) {
  const router = useRouter();
  const status = statusMeta(profile.latestParseStatus);

  const handleCardClick = () => {
    router.push(`/profiles/${encodeURIComponent(profile.profileId)}`);
  };

  const stopNav = (e: React.MouseEvent) => {
    e.stopPropagation();
  };

  return (
    <article
      className="home-disease-card home-disease-card--clickable"
      onClick={handleCardClick}
      role="link"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === "Enter") handleCardClick(); }}
    >
      <div className="home-disease-card-top">
        <h4>{profile.diseaseName}</h4>
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
            onClick={(e) => { stopNav(e); onDelete(profile); }}
            aria-label={`删除 ${profile.diseaseName}`}
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
        <p>共 {profile.recordCount} 份报告</p>
      </div>

      <div className="home-disease-actions" onClick={stopNav}>
        {profile.recordCount > 0 && (
          <Link
            className="home-view-btn-full"
            href={`/agent?profileId=${encodeURIComponent(profile.profileId)}`}
            onClick={stopNav}
          >
            AI 智能对话
          </Link>
        )}
        <button
          className="home-upload-mini-btn"
          type="button"
          onClick={(e) => { stopNav(e); onUpload(profile.profileId, profile.diseaseName); }}
          aria-label={`为 ${profile.diseaseName} 上传报告`}
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
}
