"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { StructuredResultTable } from "../parse/StructuredResultTable";
import { DeleteRecordButton } from "./DeleteRecordButton";
import { TrendComparisonPanel } from "./TrendComparisonPanel";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

const REPORT_CATEGORY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "UPLOAD", label: "常规检查" },
  { value: "LAB", label: "检验报告" },
  { value: "IMAGING", label: "影像报告" },
  { value: "OUTPATIENT", label: "门诊记录" },
  { value: "DISCHARGE", label: "出院小结" },
  { value: "OTHER", label: "其他" },
];

type TimelineRecord = {
  id: string;
  title: string;
  recordDate: string;
  sourceType: string;
};

type TimelineRecordDetail = {
  parseStatus: string;
  payload: unknown;
};

type GroupedCategory = {
  categoryValue: string;
  categoryLabel: string;
  record: TimelineRecord;
};

type GroupedDateItem = {
  date: string;
  categories: GroupedCategory[];
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

function normalizeCategory(raw?: string): string {
  const value = (raw ?? "UPLOAD").trim().toUpperCase();
  return value || "UPLOAD";
}

function categoryLabel(categoryValue: string): string {
  return REPORT_CATEGORY_OPTIONS.find((item) => item.value === categoryValue)?.label ?? categoryValue;
}

function categoryOrder(categoryValue: string): number {
  const index = REPORT_CATEGORY_OPTIONS.findIndex((item) => item.value === categoryValue);
  return index >= 0 ? index : REPORT_CATEGORY_OPTIONS.length + 1;
}

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
}: {
  profileId?: string;
  diseaseName?: string;
  records: TimelineRecord[];
}) {
  const [mutableRecords, setMutableRecords] = useState<TimelineRecord[]>(records);
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

  useEffect(() => {
    setMutableRecords(records);
  }, [records]);

  const groupedByDate = useMemo<GroupedDateItem[]>(() => {
    const dateMap = new Map<string, GroupedCategory[]>();

    for (const record of mutableRecords) {
      const date = record.recordDate;
      const categoryValue = normalizeCategory(record.sourceType);
      const next: GroupedCategory = {
        categoryValue,
        categoryLabel: categoryLabel(categoryValue),
        record,
      };
      if (!dateMap.has(date)) {
        dateMap.set(date, [next]);
      } else {
        dateMap.get(date)?.push(next);
      }
    }

    return Array.from(dateMap.entries())
      .map(([date, categories]) => ({
        date,
        categories: [...categories].sort((a, b) => categoryOrder(a.categoryValue) - categoryOrder(b.categoryValue)),
      }))
      .sort((a, b) => String(b.date).localeCompare(String(a.date)));
  }, [mutableRecords]);

  const categoriesForSelectedDate = useMemo(() => {
    if (!selectedDate) {
      return [];
    }
    return groupedByDate.find((item) => item.date === selectedDate)?.categories ?? [];
  }, [groupedByDate, selectedDate]);

  const selectedRecord = useMemo(() => {
    if (!selectedRecordId) {
      return null;
    }
    return mutableRecords.find((item) => item.id === selectedRecordId) ?? null;
  }, [mutableRecords, selectedRecordId]);

  const currentCategoryValue = selectedRecord
    ? normalizeCategory(selectedRecord.sourceType)
    : selectedCategory
      ? normalizeCategory(selectedCategory)
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
  }, [profileId]);

  useEffect(() => {
    if (selectedDate && !groupedByDate.some((item) => item.date === selectedDate)) {
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

  const onSelectDate = (date: string) => {
    setSelectedDate(date);
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
      const response = await fetch(`${API_BASE}/records/${recordId}/analysis`);
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

  const onSelectCategory = async (categoryValue: string) => {
    if (!selectedDate) {
      return;
    }
    const target = categoriesForSelectedDate.find((item) => item.categoryValue === categoryValue);
    if (!target) {
      return;
    }

    setSelectedCategory(categoryValue);
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
      const response = await fetch(`${API_BASE}/records/${target.record.id}`);
      if (!response.ok) {
        throw new Error("加载报告详情失败，请稍后重试。");
      }
      const payload = await response.json();
      const detail = payload?.data;
      const parseStatus = String(detail?.parseStatus ?? "NOT_PARSED").toUpperCase();
      const structuredPayload = detail?.structuredResult?.payload ?? {};
      setSelectedDetail({
        parseStatus,
        payload: structuredPayload,
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
      const response = await fetch(`${API_BASE}/records/${recordId}/trend?limit=6`);
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
      const response = await fetch(`${API_BASE}/records/${selectedRecordId}/source-type`, {
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
      setSelectedCategory(nextSourceType);
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
          <Link className="btn btn-ghost" href="/">
            返回首页
          </Link>
        </div>
      </section>

      <section className="timeline-selector-grid reveal reveal-delay-1">
        <article className="panel">
          <h3 className="panel-title-small">1. 选择时间节点</h3>
          {groupedByDate.length === 0 ? (
            <p className="muted">该疾病下暂无报告，请先上传。</p>
          ) : (
            <ul className="date-node-timeline">
              {groupedByDate.map((item) => (
                <li className="date-node-item" key={item.date}>
                  <button
                    className={`date-node-btn ${selectedDate === item.date ? "active" : ""}`}
                    type="button"
                    onClick={() => onSelectDate(item.date)}
                  >
                    <span className="date-node-dot" aria-hidden="true" />
                    <span className="date-node-label">{item.date}</span>
                    <span className="badge">{item.categories.length} 类报告</span>
                  </button>
                </li>
              ))}
            </ul>
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
                <li key={`${item.record.recordDate}-${item.categoryValue}`}>
                  <button
                    className={`category-select-btn ${selectedCategory === item.categoryValue ? "active" : ""}`}
                    type="button"
                    onClick={() => void onSelectCategory(item.categoryValue)}
                  >
                    {item.categoryLabel}
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
                <span className="badge">{selectedDate}</span>
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
                <button
                  className="btn btn-ghost btn-small"
                  type="button"
                  onClick={() => {
                    const nextOpen = !trendOpen;
                    setTrendOpen(nextOpen);
                    if (nextOpen) {
                      void loadRecordTrend(selectedRecord.id);
                    }
                  }}
                >
                  {trendOpen ? "收起趋势" : "趋势对比"}
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
              <h4 className="summary-heading">结构化解析结果</h4>
              <StructuredResultTable payload={selectedDetail.payload} />
            </>
          )}
        </article>
      </section>
    </main>
  );
}

