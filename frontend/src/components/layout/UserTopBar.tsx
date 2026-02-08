"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
const CREATE_DISEASE_OPTION = "__create_disease__";

type DiseaseProfile = {
  id: string;
  name: string;
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
  const [newDiseaseName, setNewDiseaseName] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<NoticeState>({ tone: "neutral", text: "" });
  const [uploadStage, setUploadStage] = useState<string>("");

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  const waitForParseTerminalStatus = async (jobId: string) => {
    for (let attempt = 0; attempt < 45; attempt += 1) {
      const statusResp = await fetch(`${API_BASE}/parse-jobs/${jobId}`);
      if (!statusResp.ok) {
        const message = await statusResp.text();
        throw new Error(`查询解析状态失败：${message || "请稍后重试。"}`);
      }
      const statusPayload = await statusResp.json();
      const status = String(statusPayload.data?.status ?? "").toUpperCase();
      const progress = Number(statusPayload.data?.progress ?? 0);
      const errorCode = statusPayload.data?.errorCode as string | undefined;

      if (status === "SUCCESS") {
        return { status, progress, errorCode };
      }
      if (status === "FAILED") {
        return { status, progress, errorCode };
      }

      setUploadStage(`解析中... ${Math.max(0, Math.min(progress, 100))}%`);
      await sleep(2000);
    }
    return { status: "TIMEOUT", progress: 0, errorCode: "PARSE_TIMEOUT" };
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

  const diseaseQuery = useQuery({
    queryKey: ["header-disease-profiles"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE}/disease-profiles`);
      if (!response.ok) {
        throw new Error("加载疾病分类失败，请稍后重试。");
      }
      const payload = await response.json();
      const profiles = (payload.data?.profiles ?? []) as Array<{ id?: string; name?: string }>;
      return profiles
        .filter((item) => item.id && item.name)
        .map((item) => ({ id: item.id as string, name: item.name as string }));
    },
    retry: false,
  });

  const canSubmit = useMemo(() => {
    return Boolean(selectedFile) && Boolean(selectedDiseaseId) && selectedDiseaseId !== CREATE_DISEASE_OPTION && !isSubmitting;
  }, [selectedDiseaseId, selectedFile, isSubmitting]);

  const isCreateMode = selectedDiseaseId === CREATE_DISEASE_OPTION;

  const openDialog = () => {
    setDialogOpen(true);
    setUploadStage("");
    setNotice({ tone: "neutral", text: "" });
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setUploadStage("");
  };

  const createDiseaseProfile = async () => {
    const diseaseName = newDiseaseName.trim();
    if (!diseaseName) {
      setNotice({ tone: "error", text: "请先输入疾病名称。" });
      return;
    }

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
      } else {
        setSelectedDiseaseId("");
      }

      setNewDiseaseName("");
      setNotice({ tone: "success", text: "已新增疾病分类并自动选中。" });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "新增疾病分类失败，请稍后重试。" });
    }
  };

  const handleUpload = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedFile) {
      setNotice({ tone: "error", text: "请先选择待上传文件。" });
      return;
    }

    if (!selectedDiseaseId || selectedDiseaseId === CREATE_DISEASE_OPTION) {
      setNotice({ tone: "error", text: "请先选择疾病分类。" });
      return;
    }

    setNotice({ tone: "neutral", text: "" });
    setUploadStage("准备上传...");
    setIsSubmitting(true);

    try {
      const recordId = crypto.randomUUID();
      const contentType = selectedFile.type || "application/octet-stream";

      setUploadStage("正在申请上传地址...");
      const presignResp = await fetch(`${API_BASE}/uploads/presign`, {
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
        const proxyUploadResp = await fetch(`${API_BASE}/uploads/proxy-put`, {
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
      const assetResp = await fetch(`${API_BASE}/assets/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          objectKey,
          checksum: `sha256:${selectedFile.size}`,
          recordId,
          diseaseProfileId: selectedDiseaseId,
          reportDate,
          title: selectedFile.name,
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
      const parseResp = await fetch(`${API_BASE}/parse-jobs`, {
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
      if (jobId) {
        setUploadStage("解析任务已创建，正在等待结果...");
        const terminal = await waitForParseTerminalStatus(jobId);
        if (terminal.status === "SUCCESS") {
          setNotice({ tone: "success", text: `上传并解析完成（任务号：${jobId}）。` });
          setUploadStage("已完成");
        } else if (terminal.status === "FAILED") {
          throw new Error(
            `解析失败（任务号：${jobId}，错误码：${terminal.errorCode ?? "UNKNOWN"}）。请检查后端 Agent 与 OSS/LLM 配置。`,
          );
        } else {
          throw new Error(`解析超时（任务号：${jobId}）。请稍后在时间线页刷新查看最终状态。`);
        }
      } else {
        setNotice({ tone: "success", text: "上传成功，已创建解析任务。" });
        setUploadStage("已完成");
      }
      router.refresh();
    } catch (error) {
      setUploadStage("");
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "上传失败，请稍后重试。" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <header className="top-header">
        <div className="brand-meta">
          <p className="brand-kicker">用户中心</p>
          <h1 className="brand-title">疾病记录时间线</h1>
          <p className="brand-subtitle">按疾病查看报告演变，后续可直接接入 Agent 进行病情分析。</p>
        </div>

        <div className="header-actions">
          <button className="action-btn action-btn-upload" type="button" onClick={openDialog}>
            上传
          </button>
          <Link className="action-btn action-btn-agent" href="/agent">
            Agent
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
                <label className="dialog-field">
                  <span>病历文件</span>
                  <input
                    type="file"
                    accept=".pdf,image/*"
                    onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
                    required
                  />
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
                  <span>疾病分类</span>
                  <select
                    value={selectedDiseaseId}
                    onChange={(event) => {
                      setSelectedDiseaseId(event.target.value);
                      setNotice({ tone: "neutral", text: "" });
                    }}
                    required
                  >
                    <option value="">请选择疾病分类</option>
                    {(diseaseQuery.data ?? []).map((profile: DiseaseProfile) => (
                      <option key={profile.id} value={profile.id}>
                        {profile.name}
                      </option>
                    ))}
                    <option value={CREATE_DISEASE_OPTION}>+ 在下拉框中新增疾病</option>
                  </select>
                </label>

                {isCreateMode && (
                  <div className="dialog-inline-create">
                    <input
                      placeholder="请输入新疾病名称，例如：高血压"
                      value={newDiseaseName}
                      onChange={(event) => setNewDiseaseName(event.target.value)}
                    />
                    <button className="btn-secondary" type="button" onClick={createDiseaseProfile}>
                      新增并选中
                    </button>
                  </div>
                )}
              </div>

              {diseaseQuery.isFetching && <p className="status-text">正在加载疾病分类...</p>}
              {isSubmitting && uploadStage && <p className="status-text">{uploadStage}</p>}
              {notice.text && (
                <p className={`status-text ${notice.tone === "error" ? "error" : ""} ${notice.tone === "success" ? "success" : ""}`}>
                  {notice.text}
                </p>
              )}

              <div className="dialog-actions">
                <button className="btn-secondary" type="button" onClick={closeDialog}>
                  取消
                </button>
                <button className="btn-primary-solid" type="submit" disabled={!canSubmit}>
                  {isSubmitting ? "上传中..." : "上传并开始解析"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </>
  );
}
