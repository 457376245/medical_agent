"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { authFetch } from "../../lib/api";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type DiseaseProfile = { id: string; name: string; recordCount: number };
type ReportCategory = { id: string; name: string; recordCount: number };
type NoticeState = { tone: "neutral" | "success" | "error"; text: string };

type FileItemStatus = "pending" | "uploading" | "success" | "error";

type BatchFileItem = {
  id: string;
  file: File;
  reportCategory: string;
  reportDate: string;
  status: FileItemStatus;
  stage: string;
  errorMessage: string;
  jobId: string | null;
};

type BatchUploadDialogProps = {
  open: boolean;
  prefilledDiseaseId?: string;
  prefilledDiseaseName?: string;
  onClose: () => void;
  onUploadComplete: () => void;
};

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const todayISO = () => new Date().toISOString().slice(0, 10);

const formatFileSize = (size: number) => {
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`;
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
};

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

async function runWithConcurrency<T>(
  tasks: (() => Promise<T>)[],
  limit: number,
): Promise<T[]> {
  const results: T[] = [];
  let index = 0;
  async function worker() {
    while (index < tasks.length) {
      const currentIndex = index++;
      results[currentIndex] = await tasks[currentIndex]();
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(limit, tasks.length) }, () => worker()),
  );
  return results;
}

const ACCEPTED_TYPES = [
  "application/pdf",
  "image/png",
  "image/jpeg",
  "image/webp",
];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export function BatchUploadDialog({
  open,
  prefilledDiseaseId,
  prefilledDiseaseName,
  onClose,
  onUploadComplete,
}: BatchUploadDialogProps) {
  /* ---------- state: disease / category selection ---------- */
  const [selectedDiseaseId, setSelectedDiseaseId] = useState(
    prefilledDiseaseId && prefilledDiseaseId !== "unknown"
      ? prefilledDiseaseId
      : "",
  );
  const [localPrefilledDiseaseName, setLocalPrefilledDiseaseName] = useState(
    prefilledDiseaseName ?? "",
  );
  const [diseaseMenuOpen, setDiseaseMenuOpen] = useState(false);
  const [isInlineCreatingDisease, setIsInlineCreatingDisease] = useState(false);
  const [newDiseaseName, setNewDiseaseName] = useState("");
  const [isCreatingDisease, setIsCreatingDisease] = useState(false);

  const [isInlineCreatingReportCategory, setIsInlineCreatingReportCategory] =
    useState(false);
  const [newReportCategoryName, setNewReportCategoryName] = useState("");
  const [isCreatingReportCategory, setIsCreatingReportCategory] =
    useState(false);

  /* ---------- state: delete confirm ---------- */
  const [deletingDiseaseId, setDeletingDiseaseId] = useState<string | null>(
    null,
  );
  const [deletingReportCategoryId, setDeletingReportCategoryId] = useState<
    string | null
  >(null);
  const [pendingDeleteDisease, setPendingDeleteDisease] =
    useState<DiseaseProfile | null>(null);
  const [pendingDeleteReportCategory, setPendingDeleteReportCategory] =
    useState<ReportCategory | null>(null);

  /* ---------- state: batch file items ---------- */
  const [fileItems, setFileItems] = useState<BatchFileItem[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<NoticeState>({
    tone: "neutral",
    text: "",
  });
  const [dragOver, setDragOver] = useState(false);

  /* ---------- refs ---------- */
  const diseaseSelectRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  /* ---------- queries ---------- */
  const diseaseQuery = useQuery<DiseaseProfile[]>({
    queryKey: ["header-disease-profiles"],
    queryFn: async () => {
      const response = await authFetch("/disease-profiles");
      if (!response.ok) throw new Error("加载疾病分类失败，请稍后重试。");
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
      const response = await authFetch("/report-categories");
      if (!response.ok) throw new Error("加载报告分类失败，请稍后重试。");
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

  /* ---------- derived ---------- */
  const selectedDiseaseName = useMemo(() => {
    const matched = (diseaseQuery.data ?? []).find(
      (p) => p.id === selectedDiseaseId,
    )?.name;
    return matched || localPrefilledDiseaseName;
  }, [diseaseQuery.data, localPrefilledDiseaseName, selectedDiseaseId]);

  const successCount = fileItems.filter((f) => f.status === "success").length;
  const errorCount = fileItems.filter((f) => f.status === "error").length;
  const canSubmit =
    fileItems.length > 0 && Boolean(selectedDiseaseId) && !isSubmitting;
  const hasRetryable = errorCount > 0 && !isSubmitting;

  /* ---------- click-outside to close dropdown ---------- */
  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target) return;
      if (
        diseaseSelectRef.current &&
        !diseaseSelectRef.current.contains(target)
      ) {
        setDiseaseMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  /* ---------- file management ---------- */
  const handleFilesSelected = useCallback((files: FileList | File[]) => {
    const today = todayISO();
    const newItems: BatchFileItem[] = [];
    for (const file of Array.from(files)) {
      if (
        ACCEPTED_TYPES.length > 0 &&
        !ACCEPTED_TYPES.includes(file.type) &&
        !file.name.toLowerCase().endsWith(".pdf")
      ) {
        continue;
      }
      newItems.push({
        id: crypto.randomUUID(),
        file,
        reportCategory: "",
        reportDate: today,
        status: "pending",
        stage: "",
        errorMessage: "",
        jobId: null,
      });
    }
    if (newItems.length === 0) return;
    setFileItems((prev) => {
      const existing = new Set(prev.map((f) => `${f.file.name}:${f.file.size}`));
      const deduped = newItems.filter(
        (f) => !existing.has(`${f.file.name}:${f.file.size}`),
      );
      return [...prev, ...deduped];
    });
  }, []);

  const removeFileItem = useCallback((id: string) => {
    setFileItems((prev) => prev.filter((f) => f.id !== id));
  }, []);

  const updateFileItem = useCallback(
    (id: string, patch: Partial<BatchFileItem>) => {
      setFileItems((prev) =>
        prev.map((item) => (item.id === id ? { ...item, ...patch } : item)),
      );
    },
    [],
  );

  /* ---------- drag & drop ---------- */
  const onDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!dragOver) setDragOver(true);
    },
    [dragOver],
  );
  const onDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOver(false);
  }, []);
  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragOver(false);
      if (e.dataTransfer.files.length > 0) {
        handleFilesSelected(e.dataTransfer.files);
      }
    },
    [handleFilesSelected],
  );

  /* ---------- disease CRUD ---------- */
  const createDiseaseProfile = async () => {
    const name = newDiseaseName.trim();
    if (!name) {
      setNotice({ tone: "error", text: "请先输入疾病名称。" });
      return;
    }
    setIsCreatingDisease(true);
    try {
      const response = await authFetch("/disease-profiles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name }),
      });
      if (!response.ok)
        throw new Error("新增疾病分类失败，请检查名称是否重复。");
      const payload = await response.json();
      const id = payload.data?.diseaseProfileId as string | undefined;
      await diseaseQuery.refetch();
      if (id) {
        setSelectedDiseaseId(id);
        setLocalPrefilledDiseaseName(name);
      }
      setNewDiseaseName("");
      setIsInlineCreatingDisease(false);
      setDiseaseMenuOpen(false);
      setNotice({ tone: "success", text: "已新增疾病分类并自动选中。" });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "新增疾病分类失败，请稍后重试。",
      });
    } finally {
      setIsCreatingDisease(false);
    }
  };

  const promptDeleteDiseaseProfile = (profile: DiseaseProfile) => {
    if (profile.recordCount > 0) {
      setNotice({
        tone: "error",
        text: `"${profile.name}"下已有 ${profile.recordCount} 份报告，无法删除。`,
      });
      return;
    }
    setPendingDeleteDisease(profile);
  };

  const deleteDiseaseProfile = async () => {
    if (!pendingDeleteDisease) return;
    const profile = pendingDeleteDisease;
    setDeletingDiseaseId(profile.id);
    try {
      const response = await authFetch(
        `/disease-profiles/${profile.id}?onlyIfEmpty=true`,
        { method: "DELETE" },
      );
      const payload = await response.json().catch(() => ({}));
      if (response.status === 409) {
        const count = Number(
          payload?.data?.linkedRecordCount ?? profile.recordCount ?? 0,
        );
        setNotice({
          tone: "error",
          text: `"${profile.name}"下已有 ${count} 份报告，无法删除。`,
        });
        return;
      }
      if (!response.ok) throw new Error("删除疾病失败，请稍后重试。");
      if (selectedDiseaseId === profile.id) {
        setSelectedDiseaseId("");
        setLocalPrefilledDiseaseName("");
      }
      await diseaseQuery.refetch();
      setPendingDeleteDisease(null);
      setNotice({
        tone: "success",
        text: `已删除疾病分类"${profile.name}"。`,
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "删除疾病失败，请稍后重试。",
      });
    } finally {
      setDeletingDiseaseId(null);
    }
  };

  /* ---------- report category CRUD ---------- */
  const createReportCategory = async () => {
    const name = newReportCategoryName.trim();
    if (!name) {
      setNotice({ tone: "error", text: "请先输入报告分类名称。" });
      return;
    }
    setIsCreatingReportCategory(true);
    try {
      const response = await authFetch("/report-categories", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name }),
      });
      if (!response.ok)
        throw new Error("新增报告分类失败，请检查名称后重试。");
      await reportCategoryQuery.refetch();
      setNewReportCategoryName("");
      setIsInlineCreatingReportCategory(false);
      setNotice({ tone: "success", text: "已新增报告分类。" });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "新增报告分类失败，请稍后重试。",
      });
    } finally {
      setIsCreatingReportCategory(false);
    }
  };

  const promptDeleteReportCategory = (category: ReportCategory) => {
    if (category.recordCount > 0) {
      setNotice({
        tone: "error",
        text: `"${category.name}"下已有 ${category.recordCount} 份报告，无法删除。`,
      });
      return;
    }
    setPendingDeleteReportCategory(category);
  };

  const deleteReportCategory = async () => {
    if (!pendingDeleteReportCategory) return;
    const category = pendingDeleteReportCategory;
    setDeletingReportCategoryId(category.id);
    try {
      const response = await authFetch(
        `/report-categories/${category.id}?onlyIfEmpty=true`,
        { method: "DELETE" },
      );
      const payload = await response.json().catch(() => ({}));
      if (response.status === 409) {
        const count = Number(
          payload?.data?.linkedRecordCount ?? category.recordCount ?? 0,
        );
        setNotice({
          tone: "error",
          text: `"${category.name}"下已有 ${count} 份报告，无法删除。`,
        });
        return;
      }
      if (!response.ok) throw new Error("删除报告分类失败，请稍后重试。");
      await reportCategoryQuery.refetch();
      setPendingDeleteReportCategory(null);
      setNotice({
        tone: "success",
        text: `已删除报告分类"${category.name}"。`,
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "删除报告分类失败，请稍后重试。",
      });
    } finally {
      setDeletingReportCategoryId(null);
    }
  };

  /* ---------- upload logic ---------- */
  const uploadSingleFile = useCallback(
    async (item: BatchFileItem) => {
      const diseaseName = selectedDiseaseName || "未分类疾病";

      try {
        updateFileItem(item.id, { status: "uploading", stage: "准备上传..." });

        const recordId = crypto.randomUUID();
        const contentType = item.file.type || "application/octet-stream";

        // 1. presign
        updateFileItem(item.id, { stage: "正在申请上传地址..." });
        const presignResp = await authFetch("/ingestions/presign", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            fileName: item.file.name,
            contentType,
            size: item.file.size,
          }),
        });
        if (!presignResp.ok) {
          const message = await presignResp.text();
          throw new Error(`申请上传地址失败：${message || "请稍后重试。"}`);
        }
        const presignPayload = await presignResp.json();
        const objectKey = presignPayload.data?.objectKey as string | undefined;
        const uploadUrl = presignPayload.data?.uploadUrl as string | undefined;
        if (!objectKey || !uploadUrl)
          throw new Error("上传地址返回异常，请稍后重试。");
        if (uploadUrl.includes("/mock-upload/"))
          throw new Error(
            "后端当前未启用真实 OSS（返回了 mock-upload 地址）。请在 backend-java 开启 APP_OSS_ENABLED=true 并配置 OSS 参数后重试。",
          );

        // 2. upload to OSS (with proxy fallback)
        updateFileItem(item.id, { stage: "正在上传到 OSS..." });
        try {
          const uploadResp = await fetch(uploadUrl, {
            method: "PUT",
            body: item.file,
            headers: { "Content-Type": contentType },
          });
          if (!uploadResp.ok) {
            const message = await uploadResp.text();
            throw new Error(
              `文件上传失败：${message || "请检查网络后重试。"}`,
            );
          }
        } catch (error) {
          updateFileItem(item.id, {
            stage: "浏览器直传失败，正在使用服务端通道上传...",
          });
          const base64Data = await fileToBase64(item.file);
          const proxyResp = await authFetch("/ingestions/proxy-upload", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ objectKey, contentType, base64Data }),
          });
          if (!proxyResp.ok) {
            const message = await proxyResp.text();
            const browserFailure =
              error instanceof Error ? error.message : "未知错误";
            throw new Error(
              `文件上传失败（浏览器直传 + 服务端通道均失败）：${browserFailure}; ${message || "请检查 OSS 配置。"}`,
            );
          }
        }

        // 3. create asset
        updateFileItem(item.id, { stage: "正在归档文件..." });
        const categoryPart = item.reportCategory || "";
        const titleParts = [diseaseName];
        if (categoryPart) titleParts.push(categoryPart);
        titleParts.push(item.reportDate || todayISO());
        const title = titleParts.join("-");

        const assetResp = await authFetch("/ingestions/assets", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            objectKey,
            checksum: `sha256:${item.file.size}`,
            recordId,
            diseaseProfileId: selectedDiseaseId,
            sourceType: item.reportCategory || undefined,
            reportDate: item.reportDate,
            title,
            size: item.file.size,
          }),
        });
        if (!assetResp.ok) {
          const message = await assetResp.text();
          throw new Error(`文件归档失败：${message || "请稍后重试。"}`);
        }
        const assetPayload = await assetResp.json();
        const assetId = assetPayload.data?.assetId as string | undefined;
        if (!assetId) throw new Error("归档结果异常，请稍后重试。");

        // 4. create parse job
        updateFileItem(item.id, { stage: "正在创建解析任务..." });
        const parseResp = await authFetch("/ingestions/parse-jobs", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": `batch-upload-${crypto.randomUUID()}`,
          },
          body: JSON.stringify({ assetIds: [assetId], recordId }),
        });
        if (!parseResp.ok) {
          const message = await parseResp.text();
          throw new Error(
            `解析任务创建失败：${message || "请稍后重试。"}`,
          );
        }
        const parsePayload = await parseResp.json();
        const jobId = parsePayload.data?.jobId as string | undefined;

        updateFileItem(item.id, {
          status: "success",
          stage: "",
          jobId: jobId ?? null,
        });
      } catch (error) {
        updateFileItem(item.id, {
          status: "error",
          stage: "",
          errorMessage:
            error instanceof Error ? error.message : "上传失败，请稍后重试。",
        });
      }
    },
    [selectedDiseaseId, selectedDiseaseName, updateFileItem],
  );

  const handleBatchUpload = async (e: React.FormEvent) => {
    e.preventDefault();

    if (fileItems.length === 0) {
      setNotice({ tone: "error", text: "请先选择待上传文件。" });
      return;
    }
    if (!selectedDiseaseId) {
      setNotice({ tone: "error", text: "请先选择疾病分类。" });
      return;
    }

    setNotice({ tone: "neutral", text: "" });
    setIsSubmitting(true);

    const pending = fileItems.filter(
      (f) => f.status === "pending" || f.status === "error",
    );

    // reset error items to pending
    for (const item of pending) {
      if (item.status === "error") {
        updateFileItem(item.id, {
          status: "pending",
          errorMessage: "",
          stage: "",
        });
      }
    }

    const tasks = pending.map(
      (item) => () => uploadSingleFile(item),
    );
    await runWithConcurrency(tasks, 3);

    setIsSubmitting(false);

    // check final state
    setFileItems((current) => {
      const allSuccess = current.every((f) => f.status === "success");
      const failures = current.filter((f) => f.status === "error").length;
      if (allSuccess) {
        setNotice({
          tone: "success",
          text: `全部 ${current.length} 个文件上传成功。`,
        });
        setTimeout(() => {
          void reportCategoryQuery.refetch();
          void diseaseQuery.refetch();
          onUploadComplete();
          onClose();
        }, 600);
      } else {
        setNotice({
          tone: "error",
          text: `${current.length - failures} 个文件成功，${failures} 个失败。可点击"重试失败"重新上传。`,
        });
      }
      return current;
    });
  };

  /* ---------- render guard ---------- */
  if (!open) return null;

  /* ---------- JSX ---------- */
  const categories = reportCategoryQuery.data ?? [];

  return (
    <>
      <div
        className="upload-dialog-overlay"
        onClick={onClose}
        role="presentation"
      >
        <section
          className="upload-dialog-panel"
          aria-modal="true"
          aria-labelledby="batch-upload-dialog-title"
          role="dialog"
          onClick={(e) => e.stopPropagation()}
        >
          {/* ---- head ---- */}
          <div className="dialog-head">
            <div>
              <p className="dialog-kicker">上传病历</p>
              <h2 id="batch-upload-dialog-title">批量上传报告</h2>
            </div>
            <button
              className="dialog-close"
              type="button"
              onClick={onClose}
              aria-label="关闭上传弹窗"
            >
              &times;
            </button>
          </div>

          {/* ---- form ---- */}
          <form className="dialog-form" onSubmit={handleBatchUpload}>
            {/* disease selector */}
            <label className="dialog-field" style={{ marginBottom: 10 }}>
              <span>疾病分类</span>
              <div className="dialog-disease-select" ref={diseaseSelectRef}>
                {isInlineCreatingDisease ? (
                  <div className="dialog-select-inline-create">
                    <input
                      className="dialog-select-inline-input"
                      placeholder="输入疾病名称"
                      value={newDiseaseName}
                      onChange={(e) => setNewDiseaseName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
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
                          if (isCreatingDisease) return;
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
                    onClick={() => setDiseaseMenuOpen((prev) => !prev)}
                  >
                    <span>{selectedDiseaseName || "请选择疾病分类"}</span>
                    <span
                      className="dialog-select-caret"
                      aria-hidden="true"
                    />
                  </button>
                )}

                {diseaseMenuOpen && !isInlineCreatingDisease && (
                  <ul
                    className="dialog-select-menu"
                    role="listbox"
                    aria-label="疾病分类选项"
                  >
                    {(diseaseQuery.data ?? []).map((profile) => {
                      const active = selectedDiseaseId === profile.id;
                      const deletable = profile.recordCount === 0;
                      const deletingThis = deletingDiseaseId === profile.id;
                      return (
                        <li
                          className="dialog-select-option-row"
                          key={profile.id}
                        >
                          <button
                            className={`dialog-select-option dialog-select-option-main ${active ? "active" : ""}`}
                            type="button"
                            onClick={() => {
                              setSelectedDiseaseId(profile.id);
                              setLocalPrefilledDiseaseName("");
                              setNotice({ tone: "neutral", text: "" });
                              setDiseaseMenuOpen(false);
                            }}
                          >
                            <span>{profile.name}</span>
                            {profile.recordCount > 0 ? (
                              <small>{profile.recordCount} 份报告</small>
                            ) : null}
                          </button>
                          <button
                            className="dialog-select-option-delete"
                            type="button"
                            aria-label={`删除疾病 ${profile.name}`}
                            title={
                              deletable
                                ? `删除 ${profile.name}`
                                : `${profile.name} 下有报告，不能删除`
                            }
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
                    <li
                      className="dialog-select-divider"
                      role="presentation"
                    />
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

            {/* drop zone */}
            <div
              className={`batch-drop-zone ${dragOver ? "drag-over" : ""}`}
              onDragOver={onDragOver}
              onDragLeave={onDragLeave}
              onDrop={onDrop}
              onClick={() => fileInputRef.current?.click()}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  fileInputRef.current?.click();
                }
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept=".pdf,image/png,image/jpeg,image/webp"
                style={{ display: "none" }}
                onChange={(e) => {
                  if (e.target.files && e.target.files.length > 0) {
                    handleFilesSelected(e.target.files);
                  }
                  e.target.value = "";
                }}
              />
              <svg
                width="32"
                height="32"
                viewBox="0 0 24 24"
                fill="none"
                style={{ color: "var(--primary)", marginBottom: 4 }}
              >
                <path
                  d="M12 4V16M12 4L8 8M12 4L16 8M4 20H20"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              <p style={{ margin: 0, fontWeight: 600 }}>
                拖拽文件到此处 或 点击选择多个文件
              </p>
              <p
                style={{
                  margin: 0,
                  fontSize: "0.8rem",
                  color: "var(--muted)",
                }}
              >
                支持 PDF、图片（PNG/JPG/WebP）。建议文件大小小于 20MB。
              </p>
            </div>

            {/* file table */}
            {fileItems.length > 0 && (
              <div className="batch-file-table">
                <div className="batch-file-header">
                  <span className="batch-col-name">文件名</span>
                  <span className="batch-col-category">分类（可选）</span>
                  <span className="batch-col-date">日期</span>
                  <span className="batch-col-status">状态</span>
                  <span className="batch-col-action" />
                </div>
                {fileItems.map((item) => (
                  <div className="batch-file-row" key={item.id}>
                    <span className="batch-col-name batch-file-name">
                      <span className="batch-file-name-text" title={item.file.name}>
                        {item.file.name}
                      </span>
                      <small>{formatFileSize(item.file.size)}</small>
                    </span>
                    <span className="batch-col-category">
                      <select
                        className="batch-category-select"
                        value={item.reportCategory}
                        onChange={(e) => {
                          const val = e.target.value;
                          if (val === "__create__") {
                            setIsInlineCreatingReportCategory(true);
                            setNewReportCategoryName("");
                            return;
                          }
                          updateFileItem(item.id, { reportCategory: val });
                        }}
                        disabled={
                          item.status === "uploading" ||
                          item.status === "success"
                        }
                      >
                        <option value="">自动识别</option>
                        {categories.map((c) => (
                          <option key={c.id} value={c.name}>
                            {c.name}
                          </option>
                        ))}
                        <option value="__create__">+ 新增分类...</option>
                      </select>
                    </span>
                    <span className="batch-col-date">
                      <input
                        type="date"
                        value={item.reportDate}
                        onChange={(e) =>
                          updateFileItem(item.id, {
                            reportDate: e.target.value,
                          })
                        }
                        disabled={
                          item.status === "uploading" ||
                          item.status === "success"
                        }
                      />
                    </span>
                    <span className="batch-col-status">
                      {item.status === "pending" && (
                        <span className="batch-status-dot pending" title="待上传" />
                      )}
                      {item.status === "uploading" && (
                        <span
                          className="batch-status-spinner"
                          title={item.stage}
                        />
                      )}
                      {item.status === "success" && (
                        <span className="batch-status-dot success" title="上传成功">
                          &#10003;
                        </span>
                      )}
                      {item.status === "error" && (
                        <span
                          className="batch-status-dot error"
                          title={item.errorMessage}
                        >
                          !
                        </span>
                      )}
                    </span>
                    <span className="batch-col-action">
                      {item.status !== "uploading" &&
                        item.status !== "success" && (
                          <button
                            className="batch-remove-btn"
                            type="button"
                            title="移除"
                            onClick={() => removeFileItem(item.id)}
                          >
                            &times;
                          </button>
                        )}
                    </span>
                    {item.status === "error" && item.errorMessage && (
                      <p className="batch-file-error">{item.errorMessage}</p>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* inline report category create */}
            {isInlineCreatingReportCategory && (
              <div
                className="dialog-select-inline-create"
                style={{ marginTop: 8 }}
              >
                <input
                  className="dialog-select-inline-input"
                  placeholder="输入报告分类名称"
                  value={newReportCategoryName}
                  onChange={(e) => setNewReportCategoryName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
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
                      if (isCreatingReportCategory) return;
                      setIsInlineCreatingReportCategory(false);
                      setNewReportCategoryName("");
                    }}
                    disabled={isCreatingReportCategory}
                  >
                    取消
                  </button>
                </div>
              </div>
            )}

            {/* hint */}
            {fileItems.length > 0 && (
              <p
                className="batch-hint"
                style={{
                  margin: "8px 0 0",
                  fontSize: "0.82rem",
                  color: "var(--muted)",
                }}
              >
                未选择分类的报告将在解析后自动识别分类
              </p>
            )}

            {/* progress */}
            {isSubmitting && (
              <div className="batch-progress" style={{ marginTop: 10 }}>
                <p
                  style={{
                    margin: 0,
                    fontSize: "0.88rem",
                    color: "var(--muted)",
                  }}
                >
                  正在上传 {successCount + errorCount}/{fileItems.length}...
                </p>
                <div className="batch-progress-bar">
                  <div
                    className="batch-progress-fill"
                    style={{
                      width: `${((successCount + errorCount) / fileItems.length) * 100}%`,
                    }}
                  />
                </div>
              </div>
            )}

            {/* status */}
            <div className="dialog-status-stack">
              {diseaseQuery.isFetching && (
                <p className="status-text">正在加载疾病分类...</p>
              )}
              {reportCategoryQuery.isFetching && (
                <p className="status-text">正在加载报告分类...</p>
              )}
              {notice.text && (
                <p
                  className={`status-text ${notice.tone === "error" ? "error" : ""} ${notice.tone === "success" ? "success" : ""}`}
                >
                  {notice.text}
                </p>
              )}
            </div>

            {/* actions */}
            <div className="dialog-actions">
              <span
                style={{
                  flex: 1,
                  fontSize: "0.86rem",
                  color: "var(--muted)",
                  alignSelf: "center",
                }}
              >
                {fileItems.length > 0 &&
                  `共 ${fileItems.length} 个文件${successCount > 0 ? `，${successCount} 个已完成` : ""}`}
              </span>
              <button
                className="btn btn-ghost"
                type="button"
                onClick={onClose}
              >
                取消
              </button>
              {hasRetryable ? (
                <button
                  className="btn btn-primary"
                  type="submit"
                  disabled={!canSubmit}
                >
                  重试失败（{errorCount}）
                </button>
              ) : (
                <button
                  className="btn btn-primary"
                  type="submit"
                  disabled={!canSubmit}
                >
                  {isSubmitting
                    ? "上传中..."
                    : `全部上传${fileItems.length > 0 ? `（${fileItems.length}）` : ""}`}
                </button>
              )}
            </div>
          </form>
        </section>
      </div>

      <ConfirmDialog
        open={Boolean(pendingDeleteDisease)}
        title="确认删除疾病分类"
        description={`确认删除"${pendingDeleteDisease?.name ?? ""}"吗？仅空疾病分类允许删除。`}
        confirmText="确认删除"
        tone="danger"
        loading={deletingDiseaseId !== null}
        onCancel={() => {
          if (!deletingDiseaseId) setPendingDeleteDisease(null);
        }}
        onConfirm={deleteDiseaseProfile}
      />
      <ConfirmDialog
        open={Boolean(pendingDeleteReportCategory)}
        title="确认删除报告分类"
        description={`确认删除"${pendingDeleteReportCategory?.name ?? ""}"吗？仅空报告分类允许删除。`}
        confirmText="确认删除"
        tone="danger"
        loading={deletingReportCategoryId !== null}
        onCancel={() => {
          if (!deletingReportCategoryId)
            setPendingDeleteReportCategory(null);
        }}
        onConfirm={deleteReportCategory}
      />
    </>
  );
}
