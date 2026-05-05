"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BarChart3, TrendingDown, TrendingUp } from "lucide-react";
import { AppSelect, type AppSelectOption } from "../../../components/common/AppSelect";
import { AgentPageFrame } from "../../../components/agent/AgentPageFrame";
import { asObject, toOptionalText, toText } from "../../../components/agent/agent-utils";
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
              rootClassName="agent-record-select"
              triggerClassName="agent-profile-select-trigger"
              menuClassName="agent-select-menu"
              onChange={setSourceType}
            />
          </div>

          <div className="agent-trends-layout">
            <article className="agent-dashboard-card agent-chart-card">
              <TrendComparisonPanel
                loading={loadingTrend}
                error={trendError}
                data={trendData ?? undefined}
              />
            </article>

            <aside className="agent-dashboard-card">
              <div className="agent-card-headline">
                <div>
                  <p className="hero-kicker">变化摘要</p>
                  <h3>近期关注项</h3>
                </div>
                <BarChart3 className="w-5 h-5" aria-hidden="true" />
              </div>
              {data?.trendHighlights.length ? (
                <div className="agent-risk-list">
                  {data.trendHighlights.map((item) => {
                    const Icon = item.direction === "down" ? TrendingDown : TrendingUp;
                    return (
                      <div className="agent-trend-highlight" key={`${item.name}-${item.recordDate}`}>
                        <Icon className="w-4 h-4" aria-hidden="true" />
                        <div>
                          <strong>{item.name}</strong>
                          <p>{item.previousValue ? `${item.previousValue} -> ` : ""}{item.currentValue}{item.unit ?? ""}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p>暂无明确趋势亮点。继续上传同一分类报告后，这里会自动整理变化。</p>
              )}
            </aside>
          </div>
        </section>
      )}
    </AgentPageFrame>
  );
}
