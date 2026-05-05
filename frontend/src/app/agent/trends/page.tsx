"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BarChart3 } from "lucide-react";
import { AppSelect, type AppSelectOption } from "../../../components/common/AppSelect";
import { AgentPageFrame } from "../../../components/agent/AgentPageFrame";
import { asObject, toOptionalText, toText } from "../../../components/agent/agent-utils";
import { buildTrendSummary, type TrendSummaryItem } from "../../../components/agent/trendSummary";
import { useAgentDashboard } from "../../../components/agent/useAgentDashboard";
import type { AgentStructuredField, AgentTrendData } from "../../../components/agent/types";
import { usePatient } from "../../../components/auth/PatientProvider";
import type { ComparisonType, ResultState } from "../../../components/parse/structuredFieldInterpretation";
import { TrendComparisonPanel } from "../../../components/profiles/TrendComparisonPanel";
import { categoryLabel } from "../../../components/profiles/timelineGrouping";
import { authFetch } from "../../../lib/api";

const COMPARISON_TYPES = new Set(["range", "upper_bound", "lower_bound", "threshold", "none"]);
const RESULT_STATES = new Set(["high", "low", "normal", "threshold", "unknown"]);

function toOptionalNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function toOptionalBoolean(value: unknown): boolean | undefined {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "true") return true;
    if (normalized === "false") return false;
  }
  return undefined;
}

function toComparisonType(value: unknown): ComparisonType | undefined {
  const normalized = toOptionalText(value);
  return normalized && COMPARISON_TYPES.has(normalized) ? normalized as ComparisonType : undefined;
}

function toResultState(value: unknown): ResultState | undefined {
  const normalized = toOptionalText(value);
  return normalized && RESULT_STATES.has(normalized) ? normalized as ResultState : undefined;
}

function normalizeField(raw: unknown): AgentStructuredField | null {
  const payload = asObject(raw);
  const name = toOptionalText(payload.name);
  const value = toOptionalText(payload.value);
  if (!name || !value) return null;
  return {
    name,
    value,
    unit: toOptionalText(payload.unit),
    referenceRange: toOptionalText(payload.referenceRange ?? payload.reference_range),
    numericValue: toOptionalNumber(payload.numericValue ?? payload.numeric_value),
    comparisonType: toComparisonType(payload.comparisonType ?? payload.comparison_type),
    resultState: toResultState(payload.resultState ?? payload.result_state),
    referenceLowerBound: toOptionalNumber(payload.referenceLowerBound ?? payload.reference_lower_bound),
    referenceUpperBound: toOptionalNumber(payload.referenceUpperBound ?? payload.reference_upper_bound),
    referenceLowerInclusive: toOptionalBoolean(payload.referenceLowerInclusive ?? payload.reference_lower_inclusive),
    referenceUpperInclusive: toOptionalBoolean(payload.referenceUpperInclusive ?? payload.reference_upper_inclusive),
  };
}

function normalizeTrend(raw: unknown): AgentTrendData {
  const payload = asObject(raw);
  return {
    recordId: toOptionalText(payload.recordId ?? payload.record_id) ?? "",
    sourceType: toOptionalText(payload.sourceType ?? payload.source_type) ?? "",
    diseaseProfileId: toOptionalText(payload.diseaseProfileId ?? payload.disease_profile_id) ?? "",
    limit: Number(payload.limit ?? 6),
    snapshots: Array.isArray(payload.snapshots)
      ? payload.snapshots.map((snapshotRaw: unknown) => {
          const snapshot = asObject(snapshotRaw);
          return {
            recordId: toOptionalText(snapshot.recordId ?? snapshot.record_id) ?? "",
            recordDate: toOptionalText(snapshot.recordDate ?? snapshot.record_date) ?? "",
            title: toOptionalText(snapshot.title) ?? "未命名报告",
            sourceType: toOptionalText(snapshot.sourceType ?? snapshot.source_type) ?? "",
            fields: Array.isArray(snapshot.fields)
              ? snapshot.fields.map(normalizeField).filter((item): item is AgentStructuredField => Boolean(item))
              : [],
          };
        })
      : [],
  };
}

function normalizeSourceType(raw: unknown): string {
  const value = toText(raw).trim();
  const lower = value.toLowerCase();
  if (!value || lower === "null" || lower === "undefined") return "";
  return value;
}

function resultStateLabel(value?: string): string {
  if (value === "high") return "偏高";
  if (value === "low") return "偏低";
  if (value === "threshold") return "阈值异常";
  if (value === "normal") return "正常";
  return "无法判定";
}

function directionText(item: TrendSummaryItem): string {
  if (!item.previousValue || item.direction === "unknown") {
    return "暂无上次值";
  }
  if (item.direction === "up") return `较上次上升，上次 ${item.previousValue}${item.unit ?? ""}`;
  if (item.direction === "down") return `较上次下降，上次 ${item.previousValue}${item.unit ?? ""}`;
  return `较上次持平，上次 ${item.previousValue}${item.unit ?? ""}`;
}

function resultStateClass(value?: string): string {
  if (value === "high") return "state-high";
  if (value === "low") return "state-low";
  if (value === "threshold") return "state-threshold";
  if (value === "normal") return "state-normal";
  return "state-unknown";
}

export default function AgentTrendsPage() {
  const searchParams = useSearchParams();
  const { currentPatient } = usePatient();
  const profileId = searchParams.get("profileId")?.trim() || undefined;
  const { data, loading, error } = useAgentDashboard(profileId, currentPatient?.id);
  const [sourceType, setSourceType] = useState("");
  const [trendData, setTrendData] = useState<AgentTrendData | null>(null);
  const [trendError, setTrendError] = useState("");
  const [loadingTrend, setLoadingTrend] = useState(false);
  const selectedProfileId = data?.selectedProfile?.profileId ?? "";
  const selectedSourceTypeLabel = sourceType ? categoryLabel(sourceType) : "当前分类";

  const sourceTypeOptions: AppSelectOption[] = useMemo(() => {
    const seen = new Set<string>();
    const options: AppSelectOption[] = [];
    for (const rawSourceType of data?.sourceTypes ?? []) {
      const nextSourceType = normalizeSourceType(rawSourceType);
      if (!nextSourceType || seen.has(nextSourceType)) {
        continue;
      }
      seen.add(nextSourceType);
      options.push({
        value: nextSourceType,
        label: categoryLabel(nextSourceType),
      });
    }
    return options;
  }, [data?.sourceTypes]);

  const defaultSourceType = useMemo(() => {
    if (sourceTypeOptions.length === 0) {
      return "";
    }
    const latestSourceType = normalizeSourceType(data?.latestRecord?.sourceType);
    if (latestSourceType && sourceTypeOptions.some((option) => option.value === latestSourceType)) {
      return latestSourceType;
    }
    return sourceTypeOptions[0].value;
  }, [data?.latestRecord?.sourceType, sourceTypeOptions]);

  useEffect(() => {
    setSourceType(defaultSourceType);
  }, [defaultSourceType, selectedProfileId]);

  useEffect(() => {
    let cancelled = false;
    if (!selectedProfileId || !sourceType) {
      setTrendData(null);
      return;
    }
    const loadTrend = async () => {
      setLoadingTrend(true);
      setTrendError("");
      try {
        const params = new URLSearchParams({
          profileId: selectedProfileId,
          sourceType,
          limit: "6",
        });
        const response = await authFetch(`/agent/trends?${params.toString()}`);
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(toText(payload.message || "加载趋势失败，请稍后重试。"));
        }
        if (!cancelled) {
          setTrendData(normalizeTrend(payload.data));
        }
      } catch (loadError) {
        if (!cancelled) {
          setTrendData(null);
          setTrendError(loadError instanceof Error ? loadError.message : "加载趋势失败，请稍后重试。");
        }
      } finally {
        if (!cancelled) setLoadingTrend(false);
      }
    };
    void loadTrend();
    return () => {
      cancelled = true;
    };
  }, [selectedProfileId, sourceType]);

  const trendSummary = useMemo(
    () => buildTrendSummary(trendData, selectedSourceTypeLabel),
    [selectedSourceTypeLabel, trendData],
  );
  const hasSummaryRows = trendSummary.items.length > 0;

  const renderSummaryGroup = (title: string, items: TrendSummaryItem[], className: string) => {
    if (items.length === 0) {
      return null;
    }
    return (
      <section className={`agent-trend-summary-group ${className}`} aria-label={title}>
        <h4>{title}</h4>
        <div className="agent-trend-summary-rows">
          {items.map((item) => (
            <div className="agent-trend-summary-row" key={`${item.key}-${item.kind}`}>
              <div className="agent-trend-summary-main">
                <strong>{item.name}</strong>
                <span className={`agent-trend-state-pill ${resultStateClass(item.resultState)}`}>
                  {resultStateLabel(item.resultState)}
                </span>
              </div>
              <span className="agent-trend-summary-value">
                当前 {item.currentValue}{item.unit ?? ""}
              </span>
              <span className="agent-trend-summary-change">{directionText(item)}</span>
              <span className="agent-trend-summary-reference">
                {item.referenceRange ? `参考 ${item.referenceRange}` : "参考范围缺失"}
              </span>
            </div>
          ))}
        </div>
      </section>
    );
  };

  return (
    <AgentPageFrame profiles={data?.profiles ?? []} selectedProfile={data?.selectedProfile}>
      {loading ? (
        <div className="agent-dashboard-loading" role="status">
          <div className="agent-skeleton-line" style={{ width: "44%" }} />
          <div className="agent-skeleton-line" style={{ width: "62%" }} />
        </div>
      ) : error ? (
        <section className="agent-empty-state">
          <h2>暂时无法加载趋势</h2>
          <p>{error}</p>
        </section>
      ) : (
        <section className="agent-page-panel">
          <div className="agent-section-head">
            <div>
              <p className="hero-kicker">指标趋势</p>
              <h2>检验指标变化</h2>
            </div>
            <AppSelect
              ariaLabel="选择报告分类"
              value={sourceType}
              options={sourceTypeOptions.length > 0 ? sourceTypeOptions : [{ value: "", label: "暂无可用报告分类", disabled: true }]}
              disabled={sourceTypeOptions.length === 0}
              rootClassName="agent-record-select agent-trends-category-select"
              triggerClassName="agent-profile-select-trigger"
              menuClassName="agent-select-menu"
              onChange={setSourceType}
            />
          </div>

          <div className="agent-trends-stack">
            <section className={`agent-dashboard-card agent-trend-summary-card trend-summary-${trendSummary.state}`}>
              <div className="agent-card-headline">
                <div>
                  <p className="hero-kicker">变化摘要</p>
                  <h3>{loadingTrend ? "正在整理趋势" : trendSummary.title}</h3>
                </div>
                <BarChart3 className="w-5 h-5" aria-hidden="true" />
              </div>

              {loadingTrend ? (
                <p className="status-text">正在加载当前分类的变化摘要...</p>
              ) : trendError ? (
                <p className="status-text error">{trendError}</p>
              ) : (
                <>
                  <div className="agent-trend-summary-metrics" aria-label="当前趋势摘要统计">
                    <span>
                      <strong>{trendSummary.snapshotCount}</strong>
                      次报告
                    </span>
                    <span>
                      <strong>{trendSummary.currentAbnormalCount}</strong>
                      当前异常
                    </span>
                    <span>
                      <strong>{trendSummary.historicalAbnormalCount}</strong>
                      已恢复
                    </span>
                    <span>
                      <strong>{trendSummary.unknownCount}</strong>
                      无法判定
                    </span>
                  </div>
                  <p className="agent-trend-summary-detail">
                    {trendSummary.latestRecordDate ? `最新报告：${trendSummary.latestRecordDate}。` : ""}
                    {trendSummary.detail}
                  </p>
                  {hasSummaryRows ? (
                    <div className="agent-trend-summary-groups">
                      {renderSummaryGroup("当前异常", trendSummary.currentItems, "summary-group-current")}
                      {renderSummaryGroup("已恢复正常", trendSummary.historicalItems, "summary-group-historical")}
                      {renderSummaryGroup("无法判定", trendSummary.unknownItems, "summary-group-unknown")}
                    </div>
                  ) : (
                    <p className="status-text success">{trendSummary.detail}</p>
                  )}
                </>
              )}
            </section>

            <article className="agent-dashboard-card agent-chart-card agent-trend-chart-card-full">
              <TrendComparisonPanel
                loading={loadingTrend}
                error={trendError}
                data={trendData ?? undefined}
              />
            </article>
          </div>
        </section>
      )}
    </AgentPageFrame>
  );
}
