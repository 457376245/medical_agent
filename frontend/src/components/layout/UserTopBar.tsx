"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ConfirmDialog } from "../common/ConfirmDialog";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type DiseaseProfile = {
  id: string;
  name: string;
  recordCount: number;
};

type ReportCategory = {
  id: string;
  name: string;
  recordCount: number;
};

type OpenUploadDialogDetail = {
  diseaseProfileId?: string;
  diseaseName?: string;
};

type NoticeState = {
  tone: "neutral" | "success" | "error";
  text: string;
};

export function UserTopBar() {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [reportDate, setReportDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [selectedDiseaseId, setSelectedDiseaseId] = useState("");
  const [prefilledDiseaseName, setPrefilledDiseaseName] = useState("");
  const [reportCategory, setReportCategory] = useState("");
  const [newDiseaseName, setNewDiseaseName] = useState("");
  const [newReportCategoryName, setNewReportCategoryName] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<NoticeState>({ tone: "neutral", text: "" });
  const [uploadStage, setUploadStage] = useState<string>("");
  const [diseaseMenuOpen, setDiseaseMenuOpen] = useState(false);
  const [reportMenuOpen, setReportMenuOpen] = useState(false);
  const [isInlineCreatingDisease, setIsInlineCreatingDisease] = useState(false);
  const [isInlineCreatingReportCategory, setIsInlineCreatingReportCategory] = useState(false);
  const [isCreatingDisease, setIsCreatingDisease] = useState(false);
  const [isCreatingReportCategory, setIsCreatingReportCategory] = useState(false);
  const [deletingDiseaseId, setDeletingDiseaseId] = useState<string | null>(null);
  const [deletingReportCategoryId, setDeletingReportCategoryId] = useState<string | null>(null);
  const [pendingDeleteDisease, setPendingDeleteDisease] = useState<DiseaseProfile | null>(null);
  const [pendingDeleteReportCategory, setPendingDeleteReportCategory] = useState<ReportCategory | null>(null);
  const diseaseSelectRef = useRef<HTMLDivElement | null>(null);
  const reportSelectRef = useRef<HTMLDivElement | null>(null);

  const fileToBase64 = (file: File) =>
    new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        if (typeof result !== "string") {
          reject(new Error("文件读取失败。"));
          return;
        }
        const marker = "base64,";
        const index = result.indexOf(marker);
        if (index < 0) {
          reject(new Error("文件编码失败。"));
          return;
        }
        resolve(result.slice(index + marker.length));
      };
      reader.onerror = () => reject(new Error("文件读取失败。"));
      reader.readAsDataURL(file);
    });

  const diseaseQuery = useQuery<DiseaseProfile[]>({
    queryKey: ["header-disease-profiles"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE}/disease-profiles`);
      if (!response.ok) {
        throw new Error("加载疾病分类失败，请稍后重试。");
      }
      const payload = await response.json();
      const profiles = (payload.data?.profiles ?? []) as Array<{
        id?: string;
        name?: string;
        recordCount?: number;
        record_count?: number;
      }>;
      return profiles
        .filter((item) => item.id && item.name)
        .map((item) => ({
          id: item.id as string,
          name: item.name as string,
          recordCount: Number(item.recordCount ?? item.record_count ?? 0),
        }));
    },
    retry: false,
  });

  const reportCategoryQuery = useQuery<ReportCategory[]>({
    queryKey: ["header-report-categories"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE}/report-categories`);
      if (!response.ok) {
        throw new Error("加载报告分类失败，请稍后重试。");
      }
      const payload = await response.json();
      const categories = (payload.data?.categories ?? []) as Array<{
        id?: string;
        name?: string;
        recordCount?: number;
        record_count?: number;
      }>;
      return categories
        .filter((item) => item.id && item.name)
        .map((item) => ({
          id: item.id as string,
          name: item.name as string,
          recordCount: Number(item.recordCount ?? item.record_count ?? 0),
        }));
    },
    retry: false,
  });

  const canSubmit = useMemo(() => {
    return Boolean(selectedFile) && Boolean(selectedDiseaseId) && Boolean(reportCategory) && !isSubmitting;
  }, [reportCategory, selectedDiseaseId, selectedFile, isSubmitting]);

  const selectedDiseaseName = useMemo(() => {
    const matchedName = (diseaseQuery.data ?? []).find((profile) => profile.id === selectedDiseaseId)?.name ?? "";
    if (matchedName) {
      return matchedName;
    }
    return prefilledDiseaseName;
  }, [diseaseQuery.data, prefilledDiseaseName, selectedDiseaseId]);

  const selectedReportCategoryLabel = useMemo(() => {
    const matchedName = (reportCategoryQuery.data ?? []).find((item) => item.name === reportCategory)?.name ?? "";
    return matchedName || reportCategory;
  }, [reportCategory, reportCategoryQuery.data]);
  const computedReportTitle = useMemo(() => {
    const diseasePart = selectedDiseaseName || "未分类疾病";
    const categoryPart = selectedReportCategoryLabel || "待选择分类";
    const datePart = reportDate || new Date().toISOString().slice(0, 10);
    return `${diseasePart}-${categoryPart}-${datePart}`;
  }, [reportDate, selectedDiseaseName, selectedReportCategoryLabel]);
  const openDialog = useCallback((detail?: OpenUploadDialogDetail) => {
    const diseaseProfileId = detail?.diseaseProfileId;
    const diseaseName = detail?.diseaseName?.trim() ?? "";
    setDialogOpen(true);
    setUploadStage("");
    setDiseaseMenuOpen(false);
    setReportMenuOpen(false);
    setIsInlineCreatingDisease(false);
    setIsInlineCreatingReportCategory(false);
    setReportCategory("");
    setNewReportCategoryName("");
    setNotice({ tone: "neutral", text: "" });
    if (diseaseProfileId && diseaseProfileId !== "unknown") {
      setSelectedDiseaseId(diseaseProfileId);
      setPrefilledDiseaseName(diseaseName);
      setNewDiseaseName("");
    }
  }, []);

  const closeDialog = () => {
    setDialogOpen(false);
    setUploadStage("");
    setDiseaseMenuOpen(false);
    setReportMenuOpen(false);
    setIsInlineCreatingDisease(false);
    setIsInlineCreatingReportCategory(false);
  };

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<OpenUploadDialogDetail>).detail;
      openDialog(detail);
    };
    window.addEventListener("open-upload-dialog", handler);
    return () => window.removeEventListener("open-upload-dialog", handler);
  }, [openDialog]);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target) {
        return;
      }

      if (diseaseSelectRef.current && !diseaseSelectRef.current.contains(target)) {
        setDiseaseMenuOpen(false);
      }
      if (reportSelectRef.current && !reportSelectRef.current.contains(target)) {
        setReportMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  const createDiseaseProfile = async () => {
    const diseaseName = newDiseaseName.trim();
    if (!diseaseName) {
      setNotice({ tone: "error", text: "请先输入疾病名称。" });
      return;
    }

    setIsCreatingDisease(true);
    try {
      const response = await fetch(`${API_BASE}/disease-profiles`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: diseaseName }),
      });

      if (!response.ok) {
        throw new Error("新增疾病分类失败，请检查名称是否重复。");
      }

      const payload = await response.json();
      const diseaseProfileId = payload.data?.diseaseProfileId as string | undefined;
      await diseaseQuery.refetch();

      if (diseaseProfileId) {
        setSelectedDiseaseId(diseaseProfileId);
        setPrefilledDiseaseName(diseaseName);
      } else {
        setSelectedDiseaseId("");
        setPrefilledDiseaseName("");
      }

      setNewDiseaseName("");
      setIsInlineCreatingDisease(false);
      setDiseaseMenuOpen(false);
      setNotice({ tone: "success", text: "已新增疾病分类并自动选中。" });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "新增疾病分类失败，请稍后重试。" });
    } finally {
      setIsCreatingDisease(false);
    }
  };

  const createReportCategory = async () => {
    const categoryName = newReportCategoryName.trim();
    if (!categoryName) {
      setNotice({ tone: "error", text: "请先输入报告分类名称。" });
      return;
    }

    setIsCreatingReportCategory(true);
    try {
      const response = await fetch(`${API_BASE}/report-categories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: categoryName }),
      });
      if (!response.ok) {
        throw new Error("新增报告分类失败，请检查名称后重试。");
      }

      await reportCategoryQuery.refetch();
      setReportCategory(categoryName);
      setNewReportCategoryName("");
      setIsInlineCreatingReportCategory(false);
      setReportMenuOpen(false);
      setNotice({ tone: "success", text: "已新增报告分类并自动选中。" });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "新增报告分类失败，请稍后重试。" });
    } finally {
      setIsCreatingReportCategory(false);
    }
  };

  const promptDeleteDiseaseProfile = (profile: DiseaseProfile) => {
    if (profile.recordCount > 0) {
      setNotice({ tone: "error", text: `“${profile.name}”下已有 ${profile.recordCount} 份报告，无法删除。` });
      return;
    }
    setPendingDeleteDisease(profile);
  };

  const promptDeleteReportCategory = (category: ReportCategory) => {
    if (category.recordCount > 0) {
      setNotice({ tone: "error", text: `“${category.name}”下已有 ${category.recordCount} 份报告，无法删除。` });
      return;
    }
    setPendingDeleteReportCategory(category);
  };

  const closeDeleteDiseaseDialog = () => {
    if (deletingDiseaseId) {
      return;
    }
    setPendingDeleteDisease(null);
  };

  const closeDeleteReportCategoryDialog = () => {
    if (deletingReportCategoryId) {
      return;
    }
    setPendingDeleteReportCategory(null);
  };

  const deleteDiseaseProfile = async () => {
    if (!pendingDeleteDisease) {
      return;
    }
    const profile = pendingDeleteDisease;

    setDeletingDiseaseId(profile.id);
    try {
      const response = await fetch(`${API_BASE}/disease-profiles/${profile.id}?onlyIfEmpty=true`, {
        method: "DELETE",
      });

      const payload = await response.json().catch(() => ({}));
      if (response.status === 409) {
        const linkedCount = Number(payload?.data?.linkedRecordCount ?? profile.recordCount ?? 0);
        setNotice({ tone: "error", text: `“${profile.name}”下已有 ${linkedCount} 份报告，无法删除。` });
        return;
      }
      if (!response.ok) {
        throw new Error("删除疾病失败，请稍后重试。");
      }

      if (selectedDiseaseId === profile.id) {
        setSelectedDiseaseId("");
        setPrefilledDiseaseName("");
      }

      await diseaseQuery.refetch();
      setPendingDeleteDisease(null);
      setNotice({ tone: "success", text: `已删除疾病分类“${profile.name}”。` });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "删除疾病失败，请稍后重试。" });
    } finally {
      setDeletingDiseaseId(null);
    }
  };

  const deleteReportCategory = async () => {
    if (!pendingDeleteReportCategory) {
      return;
    }
    const category = pendingDeleteReportCategory;
    setDeletingReportCategoryId(category.id);
    try {
      const response = await fetch(`${API_BASE}/report-categories/${category.id}?onlyIfEmpty=true`, {
        method: "DELETE",
      });
      const payload = await response.json().catch(() => ({}));
      if (response.status === 409) {
        const linkedCount = Number(payload?.data?.linkedRecordCount ?? category.recordCount ?? 0);
        setNotice({ tone: "error", text: `“${category.name}”下已有 ${linkedCount} 份报告，无法删除。` });
        return;
      }
      if (!response.ok) {
        throw new Error("删除报告分类失败，请稍后重试。");
      }

      if (reportCategory === category.name) {
        setReportCategory("");
      }
      await reportCategoryQuery.refetch();
      setPendingDeleteReportCategory(null);
      setNotice({ tone: "success", text: `已删除报告分类“${category.name}”。` });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "删除报告分类失败，请稍后重试。" });
    } finally {
      setDeletingReportCategoryId(null);
    }
  };

  const handleUpload = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedFile) {
      setNotice({ tone: "error", text: "请先选择待上传文件。" });
      return;
    }

    if (!selectedDiseaseId) {
      setNotice({ tone: "error", text: "请先选择疾病分类。" });
      return;
    }

    if (!reportCategory) {
      setNotice({ tone: "error", text: "请先选择报告分类。" });
      return;
    }

    setNotice({ tone: "neutral", text: "" });
    setUploadStage("准备上传...");
    setIsSubmitting(true);

    try {
      const recordId = crypto.randomUUID();
      const contentType = selectedFile.type || "application/octet-stream";

      setUploadStage("正在申请上传地址...");
      const presignResp = await fetch(`${API_BASE}/ingestions/presign`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fileName: selectedFile.name, contentType, size: selectedFile.size }),
      });
      if (!presignResp.ok) {
        const message = await presignResp.text();
        throw new Error(`申请上传地址失败：${message || "请稍后重试。"}`);
      }

      const presignPayload = await presignResp.json();
      const objectKey = presignPayload.data?.objectKey as string | undefined;
      const uploadUrl = presignPayload.data?.uploadUrl as string | undefined;
      if (!objectKey || !uploadUrl) {
        throw new Error("上传地址返回异常，请稍后重试。");
      }
      if (uploadUrl.includes("/mock-upload/")) {
        throw new Error("后端当前未启用真实 OSS（返回了 mock-upload 地址）。请在 backend-java 开启 APP_OSS_ENABLED=true 并配置 OSS 参数后重试。");
      }

      setUploadStage("正在上传到 OSS...");
      try {
        const uploadResp = await fetch(uploadUrl, {
          method: "PUT",
          body: selectedFile,
          headers: { "Content-Type": contentType },
        });
        if (!uploadResp.ok) {
          const message = await uploadResp.text();
          throw new Error(`文件上传失败：${message || "请检查网络后重试。"}`);
        }
      } catch (error) {
        setUploadStage("浏览器直传失败，正在使用服务端通道上传...");
        const base64Data = await fileToBase64(selectedFile);
        const proxyUploadResp = await fetch(`${API_BASE}/ingestions/proxy-upload`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ objectKey, contentType, base64Data }),
        });
        if (!proxyUploadResp.ok) {
          const message = await proxyUploadResp.text();
          const browserFailure = error instanceof Error ? error.message : "未知错误";
          throw new Error(`文件上传失败（浏览器直传 + 服务端通道均失败）：${browserFailure}; ${message || "请检查 OSS 配置。"}`);
        }
      }

      setUploadStage("正在归档文件...");
      const assetResp = await fetch(`${API_BASE}/ingestions/assets`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          objectKey,
          checksum: `sha256:${selectedFile.size}`,
          recordId,
          diseaseProfileId: selectedDiseaseId,
          sourceType: reportCategory,
          reportDate,
          title: computedReportTitle,
          size: selectedFile.size,
        }),
      });
      if (!assetResp.ok) {
        const message = await assetResp.text();
        throw new Error(`文件归档失败：${message || "请稍后重试。"}`);
      }

      const assetPayload = await assetResp.json();
      const assetId = assetPayload.data?.assetId as string | undefined;
      if (!assetId) {
        throw new Error("归档结果异常，请稍后重试。");
      }

      setUploadStage("正在创建解析任务...");
      const parseResp = await fetch(`${API_BASE}/ingestions/parse-jobs`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": `header-upload-${crypto.randomUUID()}`,
        },
        body: JSON.stringify({ assetIds: [assetId], recordId }),
      });

      if (!parseResp.ok) {
        const message = await parseResp.text();
        throw new Error(`解析任务创建失败：${message || "请稍后重试。"}`);
      }

      const parsePayload = await parseResp.json();
      const jobId = parsePayload.data?.jobId as string | undefined;
      setSelectedFile(null);
      if (!jobId) {
        throw new Error("解析任务创建失败：未返回任务号。");
      }
      setNotice({ tone: "success", text: `上传成功，解析任务已在后台执行（任务号：${jobId}）。` });
      closeDialog();
      void reportCategoryQuery.refetch();
      void diseaseQuery.refetch();
      router.refresh();
    } catch (error) {
      setUploadStage("");
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "上传失败，请稍后重试。" });
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatFileSize = (size: number) => {
    if (size < 1024 * 1024) {
      return `${Math.max(1, Math.round(size / 1024))} KB`;
    }
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  };

  return (
    <>
      <header className="top-header top-header-minimal">
        <div className="header-search">
          <svg className="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11 19C15.4183 19 19 15.4183 19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11C3 15.4183 6.58172 19 11 19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M20.9999 21L16.6499 16.65" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          <input type="text" placeholder="搜索报告、疾病..." className="search-input" />
        </div>

        <div className="header-actions">
          <button className="action-btn action-btn-upload minimal-upload" type="button" onClick={() => openDialog()}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 4V16M12 4L8 8M12 4L16 8M4 20H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            上传
          </button>
          <Link className="action-btn action-btn-agent minimal-agent" href="/agent">
            AI 智能分析
          </Link>
        </div>
      </header>

      {dialogOpen && (
        <div className="upload-dialog-overlay" onClick={closeDialog} role="presentation">
          <section
            className="upload-dialog-panel"
            aria-modal="true"
            aria-labelledby="upload-dialog-title"
            role="dialog"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="dialog-head">
              <div>
                <p className="dialog-kicker">上传病历</p>
                <h2 id="upload-dialog-title">上传并加入疾病时间线</h2>
              </div>
              <button className="dialog-close" type="button" onClick={closeDialog} aria-label="关闭上传弹窗">
                ×
              </button>
            </div>

            <form className="dialog-form" onSubmit={handleUpload}>
              <div className="dialog-grid">
                <label className="dialog-field dialog-field-full dialog-file-field">
                  <span>病历文件</span>
                  <input
                    className="dialog-file-input"
                    type="file"
                    accept=".pdf,image/*"
                    onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
                    required
                  />
                  <p className="dialog-file-tip">支持 PDF、图片（PNG/JPG/WebP）。建议文件大小小于 20MB。</p>
                  {selectedFile && (
                    <p className="dialog-file-meta">
                      已选择：<strong>{selectedFile.name}</strong>（{formatFileSize(selectedFile.size)}）
                    </p>
                  )}
                </label>

                <label className="dialog-field">
                  <span>疾病分类</span>
                  <div className="dialog-disease-select" ref={diseaseSelectRef}>
                    {isInlineCreatingDisease ? (
                      <div className="dialog-select-inline-create">
                        <input
                          className="dialog-select-inline-input"
                          placeholder="输入疾病名称"
                          value={newDiseaseName}
                          onChange={(event) => setNewDiseaseName(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              void createDiseaseProfile();
                            }
                          }}
                          autoFocus
                        />
                        <div className="dialog-select-inline-actions">
                          <button
                            className="btn btn-primary btn-small"
                            type="button"
                            onClick={createDiseaseProfile}
                            disabled={isCreatingDisease}
                          >
                            {isCreatingDisease ? "新增中..." : "新增"}
                          </button>
                          <button
                            className="btn btn-ghost btn-small"
                            type="button"
                            onClick={() => {
                              if (isCreatingDisease) {
                                return;
                              }
                              setIsInlineCreatingDisease(false);
                              setNewDiseaseName("");
                            }}
                            disabled={isCreatingDisease}
                          >
                            取消
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        className={`dialog-select-trigger ${!selectedDiseaseId ? "dialog-select-empty" : ""}`}
                        type="button"
                        aria-haspopup="listbox"
                        aria-expanded={diseaseMenuOpen}
                        onClick={() => {
                          setDiseaseMenuOpen((prev) => !prev);
                          setReportMenuOpen(false);
                        }}
                      >
                        <span>{selectedDiseaseName || "请选择疾病分类"}</span>
                        <span className="dialog-select-caret" aria-hidden="true" />
                      </button>
                    )}

                    {diseaseMenuOpen && !isInlineCreatingDisease && (
                      <ul className="dialog-select-menu" role="listbox" aria-label="疾病分类选项">
                        {(diseaseQuery.data ?? []).map((profile) => {
                          const active = selectedDiseaseId === profile.id;
                          const deletable = profile.recordCount === 0;
                          const deletingThis = deletingDiseaseId === profile.id;
                          return (
                            <li className="dialog-select-option-row" key={profile.id}>
                              <button
                                className={`dialog-select-option dialog-select-option-main ${active ? "active" : ""}`}
                                type="button"
                                onClick={() => {
                                  setSelectedDiseaseId(profile.id);
                                  setPrefilledDiseaseName("");
                                  setNotice({ tone: "neutral", text: "" });
                                  setDiseaseMenuOpen(false);
                                }}
                              >
                                <span>{profile.name}</span>
                                {profile.recordCount > 0 ? <small>{profile.recordCount} 份报告</small> : null}
                              </button>
                              <button
                                className="dialog-select-option-delete"
                                type="button"
                                aria-label={`删除疾病 ${profile.name}`}
                                title={deletable ? `删除 ${profile.name}` : `${profile.name} 下有报告，不能删除`}
                                onClick={() => promptDeleteDiseaseProfile(profile)}
                                disabled={!deletable || deletingThis}
                              >
                                <svg viewBox="0 0 24 24" aria-hidden="true">
                                  <path d="M9 3h6l1 2h4v2H4V5h4l1-2zm1 6h2v9h-2V9zm4 0h2v9h-2V9zM7 9h2v9H7V9z" />
                                </svg>
                              </button>
                            </li>
                          );
                        })}
                        <li className="dialog-select-divider" role="presentation" />
                        <li>
                          <button
                            className="dialog-select-option dialog-select-option-create"
                            type="button"
                            onClick={() => {
                              setIsInlineCreatingDisease(true);
                              setNotice({ tone: "neutral", text: "" });
                              setDiseaseMenuOpen(false);
                              setNewDiseaseName("");
                            }}
                          >
                            + 在下拉框中新增疾病
                          </button>
                        </li>
                      </ul>
                    )}
                  </div>
                </label>

                <label className="dialog-field">
                  <span>报告分类</span>
                  <div className="dialog-disease-select" ref={reportSelectRef}>
                    {isInlineCreatingReportCategory ? (
                      <div className="dialog-select-inline-create">
                        <input
                          className="dialog-select-inline-input"
                          placeholder="输入报告分类名称"
                          value={newReportCategoryName}
                          onChange={(event) => setNewReportCategoryName(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              void createReportCategory();
                            }
                          }}
                          autoFocus
                        />
                        <div className="dialog-select-inline-actions">
                          <button
                            className="btn btn-primary btn-small"
                            type="button"
                            onClick={createReportCategory}
                            disabled={isCreatingReportCategory}
                          >
                            {isCreatingReportCategory ? "新增中..." : "新增"}
                          </button>
                          <button
                            className="btn btn-ghost btn-small"
                            type="button"
                            onClick={() => {
                              if (isCreatingReportCategory) {
                                return;
                              }
                              setIsInlineCreatingReportCategory(false);
                              setNewReportCategoryName("");
                            }}
                            disabled={isCreatingReportCategory}
                          >
                            取消
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        className={`dialog-select-trigger ${!reportCategory ? "dialog-select-empty" : ""}`}
                        type="button"
                        aria-haspopup="listbox"
                        aria-expanded={reportMenuOpen}
                        onClick={() => {
                          setReportMenuOpen((prev) => !prev);
                          setDiseaseMenuOpen(false);
                        }}
                      >
                        <span>{selectedReportCategoryLabel || "请选择报告分类"}</span>
                        <span className="dialog-select-caret" aria-hidden="true" />
                      </button>
                    )}

                    {reportMenuOpen && !isInlineCreatingReportCategory && (
                      <ul className="dialog-select-menu" role="listbox" aria-label="报告分类选项">
                        {(reportCategoryQuery.data ?? []).map((category) => {
                          const active = reportCategory === category.name;
                          const deletable = category.recordCount === 0;
                          const deletingThis = deletingReportCategoryId === category.id;
                          return (
                            <li className="dialog-select-option-row" key={category.id}>
                              <button
                                className={`dialog-select-option dialog-select-option-main ${active ? "active" : ""}`}
                                type="button"
                                onClick={() => {
                                  setReportCategory(category.name);
                                  setNotice({ tone: "neutral", text: "" });
                                  setReportMenuOpen(false);
                                }}
                              >
                                <span>{category.name}</span>
                                {category.recordCount > 0 ? <small>{category.recordCount} 份报告</small> : null}
                              </button>
                              <button
                                className="dialog-select-option-delete"
                                type="button"
                                aria-label={`删除报告分类 ${category.name}`}
                                title={deletable ? `删除 ${category.name}` : `${category.name} 下有报告，不能删除`}
                                onClick={() => promptDeleteReportCategory(category)}
                                disabled={!deletable || deletingThis}
                              >
                                <svg viewBox="0 0 24 24" aria-hidden="true">
                                  <path d="M9 3h6l1 2h4v2H4V5h4l1-2zm1 6h2v9h-2V9zm4 0h2v9h-2V9zM7 9h2v9H7V9z" />
                                </svg>
                              </button>
                            </li>
                          );
                        })}
                        <li className="dialog-select-divider" role="presentation" />
                        <li>
                          <button
                            className="dialog-select-option dialog-select-option-create"
                            type="button"
                            onClick={() => {
                              setIsInlineCreatingReportCategory(true);
                              setNotice({ tone: "neutral", text: "" });
                              setReportMenuOpen(false);
                              setNewReportCategoryName("");
                            }}
                          >
                            + 在下拉框中新增报告分类
                          </button>
                        </li>
                      </ul>
                    )}
                  </div>
                </label>

                <label className="dialog-field">
                  <span>报告日期</span>
                  <input
                    type="date"
                    value={reportDate}
                    onChange={(event) => setReportDate(event.target.value)}
                    required
                  />
                </label>

                <label className="dialog-field dialog-field-full">
                  <span>报告名称</span>
                  <input type="text" value={computedReportTitle} readOnly />
                </label>

              </div>

              <div className="dialog-status-stack">
                {diseaseQuery.isFetching && <p className="status-text">正在加载疾病分类...</p>}
                {reportCategoryQuery.isFetching && <p className="status-text">正在加载报告分类...</p>}
                {isSubmitting && uploadStage && <p className="status-text">{uploadStage}</p>}
                {notice.text && (
                  <p className={`status-text ${notice.tone === "error" ? "error" : ""} ${notice.tone === "success" ? "success" : ""}`}>
                    {notice.text}
                  </p>
                )}
              </div>

              <div className="dialog-actions">
                <button className="btn btn-ghost" type="button" onClick={closeDialog}>
                  取消
                </button>
                <button className="btn btn-primary" type="submit" disabled={!canSubmit}>
                  {isSubmitting ? "上传中..." : "上传并开始解析"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(pendingDeleteDisease)}
        title="确认删除疾病分类"
        description={`确认删除“${pendingDeleteDisease?.name ?? ""}”吗？仅空疾病分类允许删除。`}
        confirmText="确认删除"
        tone="danger"
        loading={deletingDiseaseId !== null}
        onCancel={closeDeleteDiseaseDialog}
        onConfirm={deleteDiseaseProfile}
      />
      <ConfirmDialog
        open={Boolean(pendingDeleteReportCategory)}
        title="确认删除报告分类"
        description={`确认删除“${pendingDeleteReportCategory?.name ?? ""}”吗？仅空报告分类允许删除。`}
        confirmText="确认删除"
        tone="danger"
        loading={deletingReportCategoryId !== null}
        onCancel={closeDeleteReportCategoryDialog}
        onConfirm={deleteReportCategory}
      />
    </>
  );
}

