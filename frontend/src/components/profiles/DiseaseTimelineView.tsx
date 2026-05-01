"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { authFetch } from "../../lib/api";
import { CombinationAnalysisPanel } from "../parse/CombinationAnalysisPanel";
import { StructuredResultTable } from "../parse/StructuredResultTable";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { DeleteRecordButton } from "./DeleteRecordButton";
import { TrendComparisonPanel } from "./TrendComparisonPanel";
import {
  REPORT_CATEGORY_OPTIONS,
  buildGroupedTimelineItems,
  categoryButtonLabel,
  categoryLabel,
  normalizeCategory,
  recordIdsForGroupedDateItem,
  type GroupedDateItem,
  type TimelineExamNode,
  type TimelineRecord,
} from "./timelineGrouping";

type CombinationAnalysisItem = {
  ruleId: string;
  name: string;
  severity: string;
  summary: string;
  detail: string;
  suggestion: string;
  involvedIndicators: string[];
};

type TimelineRecordDetail = {
  parseStatus: string;
  payload: unknown;
  combinationAnalysis: CombinationAnalysisItem[];
};

type RecordAnalysis = {
  content: string;
  cached: boolean;
};

type TrendField = {
  name: string;
  value: string;
  unit?: string;
  referenceRange?: string;
};

type TrendSnapshot = {
  recordId: string;
  recordDate: string;
  title: string;
  sourceType: string;
  fields: TrendField[];
};

type TrendData = {
  recordId: string;
  sourceType: string;
  diseaseProfileId: string;
  limit: number;
  snapshots: TrendSnapshot[];
};

function hasStructuredFields(payload: unknown): boolean {
  if (typeof payload !== "object" || payload === null) {
    return false;
  }
  const payloadRecord = payload as { fields?: unknown };
  return Array.isArray(payloadRecord.fields) && payloadRecord.fields.length > 0;
}

export function DiseaseTimelineView({
  profileId,
  diseaseName,
  records,
  examNodes = [],
  parsingCount = 0,
  patientId,
  onParsingCanceled,
  onRecordsDeleted,
}: {
  profileId?: string;
  diseaseName?: string;
  records: TimelineRecord[];
  examNodes?: TimelineExamNode[];
  parsingCount?: number;
  patientId?: string;
  onParsingCanceled?: () => void | Promise<void>;
  onRecordsDeleted?: () => void | Promise<void>;
}) {
  const [mutableRecords, setMutableRecords] = useState<TimelineRecord[]>(records);
  const [mutableExamNodes, setMutableExamNodes] = useState<TimelineExamNode[]>(examNodes);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedRecordId, setSelectedRecordId] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [selectedDetail, setSelectedDetail] = useState<TimelineRecordDetail | null>(null);
  const [categoryMenuOpen, setCategoryMenuOpen] = useState(false);
  const [categoryUpdating, setCategoryUpdating] = useState(false);
  const [categoryUpdateError, setCategoryUpdateError] = useState("");
  const [analysisCache, setAnalysisCache] = useState<Record<string, RecordAnalysis>>({});
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState("");
  const [trendOpen, setTrendOpen] = useState(false);
  const [trendCache, setTrendCache] = useState<Record<string, TrendData>>({});
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState("");
  const categoryMenuRef = useRef<HTMLDivElement | null>(null);
  const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false);
  const [cancelLoading, setCancelLoading] = useState(false);
  const [cancelError, setCancelError] = useState("");
  const [deleteTargetNode, setDeleteTargetNode] = useState<GroupedDateItem | null>(null);
  const [deletingNodeId, setDeletingNodeId] = useState<string | null>(null);
  const [batchDeleteError, setBatchDeleteError] = useState("");

  useEffect(() => {
    setMutableRecords(records);
  }, [records]);

  useEffect(() => {
    setMutableExamNodes(examNodes);
  }, [examNodes]);

  const groupedByDate = useMemo<GroupedDateItem[]>(
    () => buildGroupedTimelineItems(mutableRecords, mutableExamNodes),
    [mutableRecords, mutableExamNodes],
  );

  const categoriesForSelectedDate = useMemo(() => {
    if (!selectedDate) {
      return [];
    }
    return groupedByDate.find((item) => item.id === selectedDate)?.categories ?? [];
  }, [groupedByDate, selectedDate]);

  const selectedRecord = useMemo(() => {
    if (!selectedRecordId) {
      return null;
    }
    return mutableRecords.find((item) => item.id === selectedRecordId) ?? null;
  }, [mutableRecords, selectedRecordId]);

  const currentCategoryValue = selectedRecord
    ? normalizeCategory(selectedRecord.sourceType)
    : null;
  const currentCategoryLabel = currentCategoryValue ? categoryLabel(currentCategoryValue) : "";

  useEffect(() => {
    setSelectedDate(null);
    setSelectedCategory(null);
    setSelectedRecordId(null);
    setDetailError("");
    setSelectedDetail(null);
    setDetailLoading(false);
    setCategoryMenuOpen(false);
    setCategoryUpdateError("");
    setAnalysisCache({});
    setAnalysisLoading(false);
    setAnalysisError("");
    setTrendOpen(false);
    setTrendCache({});
    setTrendLoading(false);
    setTrendError("");
    setCancelConfirmOpen(false);
    setCancelLoading(false);
    setCancelError("");
    setDeleteTargetNode(null);
    setDeletingNodeId(null);
    setBatchDeleteError("");
  }, [profileId, patientId]);

  useEffect(() => {
    if (selectedDate && !groupedByDate.some((item) => item.id === selectedDate)) {
      setSelectedDate(null);
      setSelectedCategory(null);
      setSelectedRecordId(null);
      setSelectedDetail(null);
      setDetailError("");
      setCategoryMenuOpen(false);
      setCategoryUpdateError("");
      setAnalysisLoading(false);
      setAnalysisError("");
      setTrendOpen(false);
      setTrendLoading(false);
      setTrendError("");
      return;
    }

    if (selectedRecordId && !mutableRecords.some((item) => item.id === selectedRecordId)) {
      setSelectedCategory(null);
      setSelectedRecordId(null);
      setSelectedDetail(null);
      setDetailError("");
      setCategoryMenuOpen(false);
      setCategoryUpdateError("");
      setAnalysisLoading(false);
      setAnalysisError("");
      setTrendOpen(false);
      setTrendLoading(false);
      setTrendError("");
    }
  }, [groupedByDate, mutableRecords, selectedDate, selectedRecordId]);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target || !categoryMenuRef.current) {
        return;
      }
      if (!categoryMenuRef.current.contains(target)) {
        setCategoryMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  const openUploadDialog = () => {
    if (!profileId) {
      return;
    }
    window.dispatchEvent(
      new CustomEvent("open-upload-dialog", {
        detail: {
          diseaseProfileId: profileId,
          diseaseName,
        },
      }),
    );
  };

  const onConfirmCancelParsing = async () => {
    if (!profileId) return;
    setCancelLoading(true);
    setCancelError("");
    try {
      const response = await authFetch(`/disease-profiles/${profileId}/parsing-records`, {
        method: "DELETE",
      });
      if (!response.ok) {
        throw new Error("取消解析失败，请稍后重试。");
      }
      setCancelConfirmOpen(false);
      if (onParsingCanceled) {
        await onParsingCanceled();
      }
    } catch (error) {
      setCancelError(error instanceof Error ? error.message : "取消解析失败，请稍后重试。");
    } finally {
      setCancelLoading(false);
    }
  };

  const onSelectDate = (nodeId: string) => {
    setSelectedDate(nodeId);
    setSelectedCategory(null);
    setSelectedRecordId(null);
    setSelectedDetail(null);
    setDetailError("");
    setCategoryMenuOpen(false);
    setCategoryUpdateError("");
    setAnalysisLoading(false);
    setAnalysisError("");
    setTrendOpen(false);
    setTrendLoading(false);
    setTrendError("");
    setBatchDeleteError("");
  };

  const clearSelectedReportState = () => {
    setSelectedDate(null);
    setSelectedCategory(null);
    setSelectedRecordId(null);
    setSelectedDetail(null);
    setDetailError("");
    setCategoryMenuOpen(false);
    setCategoryUpdateError("");
    setAnalysisLoading(false);
    setAnalysisError("");
    setTrendOpen(false);
    setTrendLoading(false);
    setTrendError("");
  };

  const onConfirmDeleteNode = async () => {
    if (!profileId || !deleteTargetNode) {
      return;
    }
    const target = deleteTargetNode;
    const recordIds = recordIdsForGroupedDateItem(target);
    if (recordIds.length === 0) {
      setDeleteTargetNode(null);
      return;
    }

    setDeletingNodeId(target.id);
    setBatchDeleteError("");
    try {
      const response = await authFetch(`/disease-profiles/${profileId}/records`, {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ recordIds }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = String(payload?.message ?? "批量删除失败，请稍后重试。");
        throw new Error(message);
      }

      const recordIdSet = new Set(recordIds);
      setMutableRecords((prev) => prev.filter((item) => !recordIdSet.has(item.id)));
      setMutableExamNodes((prev) =>
        prev
          .map((node) => ({
            ...node,
            records: node.records.filter((item) => !recordIdSet.has(item.id)),
          }))
          .filter((node) => node.records.length > 0),
      );
      setAnalysisCache({});
      setTrendCache({});
      if (selectedDate === target.id || (selectedRecordId && recordIdSet.has(selectedRecordId))) {
        clearSelectedReportState();
      }
      setDeleteTargetNode(null);
      if (onRecordsDeleted) {
        await onRecordsDeleted();
      }
    } catch (error) {
      setBatchDeleteError(error instanceof Error ? error.message : "批量删除失败，请稍后重试。");
    } finally {
      setDeletingNodeId(null);
    }
  };

  const loadRecordAnalysis = async (recordId: string) => {
    const cached = analysisCache[recordId];
    if (cached) {
      setAnalysisError("");
      setAnalysisLoading(false);
      return;
    }
    setAnalysisLoading(true);
    setAnalysisError("");
    try {
      const response = await authFetch(`/records/${recordId}/analysis`);
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        if (String(payload?.code ?? "") === "ANALYSIS_NOT_READY") {
          setAnalysisError("");
          return;
        }
        const message = String(payload?.message ?? "加载AI分析失败，请稍后重试。");
        throw new Error(message);
      }
      const content = String(payload?.data?.content ?? "").trim();
      const cachedFlag = Boolean(payload?.data?.cached);
      setAnalysisCache((prev) => ({
        ...prev,
        [recordId]: {
          content,
          cached: cachedFlag,
        },
      }));
    } catch (error) {
      setAnalysisError(error instanceof Error ? error.message : "加载AI分析失败，请稍后重试。");
    } finally {
      setAnalysisLoading(false);
    }
  };

  const onSelectCategory = async (itemKey: string) => {
    if (!selectedDate) {
      return;
    }
    const target = categoriesForSelectedDate.find((item) => item.itemKey === itemKey);
    if (!target) {
      return;
    }

    setSelectedCategory(itemKey);
    setSelectedRecordId(target.record.id);
    setSelectedDetail(null);
    setDetailError("");
    setCategoryMenuOpen(false);
    setCategoryUpdateError("");
    setAnalysisError("");
    setTrendOpen(false);
    setTrendError("");
    setDetailLoading(true);
    try {
      const response = await authFetch(`/records/${target.record.id}`);
      if (!response.ok) {
        throw new Error("加载报告详情失败，请稍后重试。");
      }
      const payload = await response.json();
      const detail = payload?.data;
      const parseStatus = String(detail?.parseStatus ?? "NOT_PARSED").toUpperCase();
      const structuredPayload = detail?.structuredResult?.payload ?? {};
      const combinationAnalysis: CombinationAnalysisItem[] = Array.isArray(detail?.combinationAnalysis)
        ? detail.combinationAnalysis
        : [];
      setSelectedDetail({
        parseStatus,
        payload: structuredPayload,
        combinationAnalysis,
      });
      if (parseStatus === "SUCCESS" && hasStructuredFields(structuredPayload)) {
        void loadRecordAnalysis(target.record.id);
      } else {
        setAnalysisLoading(false);
      }
    } catch (error) {
      setDetailError(error instanceof Error ? error.message : "加载报告详情失败，请稍后重试。");
    } finally {
      setDetailLoading(false);
    }
  };

  const loadRecordTrend = async (recordId: string) => {
    const cached = trendCache[recordId];
    if (cached) {
      setTrendError("");
      setTrendLoading(false);
      return;
    }
    setTrendLoading(true);
    setTrendError("");
    try {
      const response = await authFetch(`/records/${recordId}/trend?limit=6`);
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = String(payload?.message ?? "加载趋势对比失败，请稍后重试。");
        throw new Error(message);
      }
      const data = payload?.data;
      const snapshotsRaw = Array.isArray(data?.snapshots) ? data.snapshots : [];
      const snapshots: TrendSnapshot[] = snapshotsRaw.map((snapshot: Record<string, unknown>) => ({
        recordId: String(snapshot.recordId ?? ""),
        recordDate: String(snapshot.recordDate ?? ""),
        title: String(snapshot.title ?? "未命名报告"),
        sourceType: String(snapshot.sourceType ?? ""),
        fields: (Array.isArray(snapshot.fields) ? snapshot.fields : []).map((field: Record<string, unknown>) => ({
          name: String(field.name ?? ""),
          value: String(field.value ?? ""),
          unit: field.unit ? String(field.unit) : undefined,
          referenceRange: field.referenceRange ? String(field.referenceRange) : undefined,
        })),
      }));
      setTrendCache((prev) => ({
        ...prev,
        [recordId]: {
          recordId: String(data?.recordId ?? recordId),
          sourceType: String(data?.sourceType ?? ""),
          diseaseProfileId: String(data?.diseaseProfileId ?? ""),
          limit: Number(data?.limit ?? 6),
          snapshots,
        },
      }));
    } catch (error) {
      setTrendError(error instanceof Error ? error.message : "加载趋势对比失败，请稍后重试。");
    } finally {
      setTrendLoading(false);
    }
  };

  const updateRecordCategory = async (nextCategoryValue: string) => {
    if (!selectedRecordId) {
      return;
    }
    if (currentCategoryValue === nextCategoryValue) {
      setCategoryMenuOpen(false);
      return;
    }

    setCategoryUpdating(true);
    setCategoryUpdateError("");
    try {
      const response = await authFetch(`/records/${selectedRecordId}/source-type`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ sourceType: nextCategoryValue }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = String(payload?.message ?? "修改报告分类失败，请稍后重试。");
        throw new Error(message);
      }

      const nextSourceType = String(payload?.data?.sourceType ?? nextCategoryValue);
      const nextTitle = String(payload?.data?.title ?? "");
      setMutableRecords((prev) =>
        prev.map((item) =>
          item.id === selectedRecordId
            ? {
                ...item,
                sourceType: nextSourceType,
                title: nextTitle || item.title,
              }
            : item,
        ),
      );
      setMutableExamNodes((prev) =>
        prev.map((node) => ({
          ...node,
          records: node.records.map((item) =>
            item.id === selectedRecordId
              ? {
                  ...item,
                  sourceType: nextSourceType,
                  title: nextTitle || item.title,
                }
              : item,
          ),
        })),
      );
      setSelectedCategory(selectedRecordId);
      setCategoryMenuOpen(false);
      setTrendOpen(false);
      setTrendCache({});
      setTrendError("");
    } catch (error) {
      setCategoryUpdateError(error instanceof Error ? error.message : "修改报告分类失败，请稍后重试。");
    } finally {
      setCategoryUpdating(false);
    }
  };

  if (!profileId) {
    return (
      <main className="page-stack">
        <section className="panel">
          <p className="hero-kicker">疾病时间线</p>
          <h2 className="panel-title">未选择疾病分类</h2>
          <p className="muted panel-subtitle">请先从首页疾病分类卡片点击“进入疾病报告”，再查看对应疾病时间线。</p>
          <div className="actions">
            <Link className="btn btn-primary" href="/">
              返回首页
            </Link>
          </div>
        </section>
      </main>
    );
  }

  const currentStep = !selectedDate ? 0 : !selectedCategory ? 1 : 2;

  return (
    <main className="page-stack">
      <section className="panel reveal">
        <p className="hero-kicker">疾病报告时间线</p>
        <h2 className="panel-title">{diseaseName || "疾病详情"}</h2>
        <p className="muted panel-subtitle">先选择时间节点，再选择报告分类，最后查看该报告的解析结果。</p>
        <div className="actions">
          <button className="btn btn-primary" type="button" onClick={openUploadDialog}>
            上传该疾病报告
          </button>
          <Link
            className="btn btn-ghost"
            href={`/agent?profileId=${encodeURIComponent(profileId)}${selectedRecordId ? `&recordId=${encodeURIComponent(selectedRecordId)}` : ""}`}
          >
            进入 Agent 对话
          </Link>
          <Link className="btn btn-ghost" href="/">
            返回首页
          </Link>
        </div>
      </section>

      {/* Step indicator */}
      <nav className="timeline-step-indicator reveal" aria-label="操作步骤">
        <div className={`timeline-step ${currentStep >= 0 ? "active" : ""} ${currentStep > 0 ? "done" : ""}`}>
          <span className="timeline-step-number">{currentStep > 0 ? "✓" : "1"}</span>
          <span className="timeline-step-label">选择时间</span>
        </div>
        <div className="timeline-step-connector" aria-hidden="true">
          <div className={`timeline-step-connector-fill ${currentStep > 0 ? "filled" : ""}`} />
        </div>
        <div className={`timeline-step ${currentStep >= 1 ? "active" : ""} ${currentStep > 1 ? "done" : ""}`}>
          <span className="timeline-step-number">{currentStep > 1 ? "✓" : "2"}</span>
          <span className="timeline-step-label">选择分类</span>
        </div>
        <div className="timeline-step-connector" aria-hidden="true">
          <div className={`timeline-step-connector-fill ${currentStep > 1 ? "filled" : ""}`} />
        </div>
        <div className={`timeline-step ${currentStep >= 2 ? "active" : ""}`}>
          <span className="timeline-step-number">3</span>
          <span className="timeline-step-label">查看结果</span>
        </div>
      </nav>

      <section className="timeline-selector-grid reveal reveal-delay-1">
        <article className="panel">
          <h3 className="panel-title-small">1. 选择时间节点</h3>
          {parsingCount > 0 && (
            <div style={{ marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
              <p className="status-text" style={{ margin: 0 }}>
                正在解析中：{parsingCount} 份报告...
              </p>
              <button
                className="btn btn-danger btn-small"
                type="button"
                onClick={() => setCancelConfirmOpen(true)}
                disabled={cancelLoading}
              >
                {cancelLoading ? "处理中..." : "取消解析"}
              </button>
              {cancelError ? <span className="status-text error" style={{ margin: 0 }}>{cancelError}</span> : null}
            </div>
          )}
          {groupedByDate.length === 0 ? (
            <p className="muted">该疾病下暂无报告，请先上传。</p>
          ) : (
            <>
              {batchDeleteError ? <p className="status-text error">{batchDeleteError}</p> : null}
              <ul className="date-node-timeline">
                {groupedByDate.map((item) => {
                  const isDeleting = deletingNodeId === item.id;
                  return (
                    <li className="date-node-item" key={item.id}>
                      <div className={`date-node-row ${selectedDate === item.id ? "active" : ""}`}>
                        <button
                          className="date-node-btn"
                          type="button"
                          onClick={() => onSelectDate(item.id)}
                          disabled={isDeleting}
                        >
                          <span className="date-node-dot" aria-hidden="true" />
                          <span className="date-node-label">{item.displayDate}</span>
                          <span className="badge">{item.categories.length} 份报告</span>
                        </button>
                        <button
                          className="btn btn-danger btn-small date-node-delete-btn"
                          type="button"
                          onClick={() => {
                            setBatchDeleteError("");
                            setDeleteTargetNode(item);
                          }}
                          disabled={isDeleting}
                        >
                          {isDeleting ? "删除中..." : "批量删除"}
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            </>
          )}
        </article>

        <article className="panel">
          <h3 className="panel-title-small">2. 选择报告分类</h3>
          {!selectedDate ? (
            <p className="muted">请先在左侧选择时间节点。</p>
          ) : categoriesForSelectedDate.length === 0 ? (
            <p className="muted">该日期暂无报告分类。</p>
          ) : (
            <ul className="category-select-list">
              {categoriesForSelectedDate.map((item) => (
                <li key={item.itemKey}>
                  <button
                    className={`category-select-btn ${selectedCategory === item.itemKey ? "active" : ""}`}
                    type="button"
                    onClick={() => void onSelectCategory(item.itemKey)}
                  >
                    {categoryButtonLabel(item, categoriesForSelectedDate)}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>

      <section className="reveal reveal-delay-2">
        <article className="panel result-full-panel">
          <h3 className="panel-title-small">
            {selectedRecord ? `3. 报告解析结果 - ${selectedRecord.title}` : "3. 报告解析结果"}
          </h3>
          {!selectedDate || !selectedCategory ? (
            <p className="muted">请先选择时间节点与报告分类。</p>
          ) : detailLoading ? (
            <p className="status-text">正在加载报告结果...</p>
          ) : detailError ? (
            <p className="status-text error">{detailError}</p>
          ) : !selectedDetail || !selectedRecord ? (
            <p className="muted">未找到报告结果。</p>
          ) : (
            <>
              <div className="result-toolbar">
                <span className="badge">
                  {groupedByDate.find((item) => item.id === selectedDate)?.displayDate ?? selectedDate}
                </span>
                <div className="result-category-editor" ref={categoryMenuRef}>
                  <button
                    className="result-category-btn"
                    type="button"
                    onClick={() => setCategoryMenuOpen((prev) => !prev)}
                    disabled={categoryUpdating}
                  >
                    {categoryUpdating ? "更新中..." : currentCategoryLabel || "未分类"}
                  </button>
                  {categoryMenuOpen ? (
                    <ul className="result-category-menu">
                      {REPORT_CATEGORY_OPTIONS.map((option) => (
                        <li key={option.value}>
                          <button
                            className={`result-category-menu-btn ${currentCategoryValue === option.value ? "active" : ""}`}
                            type="button"
                            onClick={() => void updateRecordCategory(option.value)}
                            disabled={categoryUpdating}
                          >
                            {option.label}
                          </button>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </div>
                <DeleteRecordButton recordId={selectedRecord.id} profileId={profileId} isSelected />
                <Link
                  className="btn btn-ghost btn-small"
                  href={`/agent?profileId=${encodeURIComponent(profileId)}&recordId=${encodeURIComponent(selectedRecord.id)}`}
                >
                  围绕此报告对话
                </Link>
                <button
                  className={`btn ${trendOpen ? "btn-primary" : "btn-ghost"} btn-small`}
                  type="button"
                  onClick={() => {
                    const nextOpen = !trendOpen;
                    setTrendOpen(nextOpen);
                    if (nextOpen) {
                      void loadRecordTrend(selectedRecord.id);
                    }
                  }}
                >
                  {trendOpen ? "收起趋势" : "📊 趋势对比"}
                </button>
              </div>

              {categoryUpdateError ? <p className="status-text error mt-10">{categoryUpdateError}</p> : null}
              {trendOpen ? (
                <TrendComparisonPanel
                  loading={trendLoading}
                  error={trendError}
                  data={selectedRecord ? trendCache[selectedRecord.id] : undefined}
                />
              ) : null}
              <div className="summary-block mt-10">
                <h4 className="summary-heading">AI分析与建议（300字内）</h4>
                {analysisLoading ? (
                  <p className="status-text">正在生成分析建议...</p>
                ) : selectedDetail.parseStatus !== "SUCCESS" || !hasStructuredFields(selectedDetail.payload) ? (
                  <p className="muted">解析成功且提取到有效结构化字段后，系统会自动生成分析建议。</p>
                ) : analysisError ? (
                  <p className="status-text error">{analysisError}</p>
                ) : analysisCache[selectedRecord.id]?.content ? (
                  <p className="paragraph-relaxed">{analysisCache[selectedRecord.id].content}</p>
                ) : (
                  <p className="muted">暂无分析建议。</p>
                )}
              </div>
              <CombinationAnalysisPanel items={selectedDetail.combinationAnalysis} />
              <h4 className="summary-heading">结构化解析结果</h4>
              <StructuredResultTable payload={selectedDetail.payload} />
            </>
          )}
        </article>
      </section>
      <ConfirmDialog
        open={cancelConfirmOpen}
        title="确认取消解析"
        description={`将删除这 ${parsingCount} 份未完成解析的报告、关联解析数据和已上传文件，删除后不可恢复。`}
        confirmText="确认取消"
        tone="danger"
        loading={cancelLoading}
        onCancel={() => !cancelLoading && setCancelConfirmOpen(false)}
        onConfirm={() => void onConfirmCancelParsing()}
      />
      <ConfirmDialog
        open={Boolean(deleteTargetNode)}
        title="确认删除该时间节点报告"
        description={
          deleteTargetNode
            ? `将删除「${deleteTargetNode.displayDate}」下 ${deleteTargetNode.categories.length} 份报告、关联解析数据和已上传文件，删除后不可恢复。`
            : ""
        }
        confirmText="确认删除"
        tone="danger"
        loading={Boolean(deletingNodeId)}
        onCancel={() => !deletingNodeId && setDeleteTargetNode(null)}
        onConfirm={() => void onConfirmDeleteNode()}
      />
    </main>
  );
}

