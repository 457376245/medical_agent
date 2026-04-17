"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts";

import {
  normalizeStructuredField,
  type ResultState,
  type StructuredFieldView,
} from "../parse/structuredFieldInterpretation";

type TrendSnapshot = {
  recordId: string;
  recordDate: string;
  title: string;
  sourceType: string;
  fields: StructuredFieldView[];
};

type TrendData = {
  recordId: string;
  sourceType: string;
  diseaseProfileId: string;
  limit: number;
  snapshots: TrendSnapshot[];
};

type TrendSnapshotView = Omit<TrendSnapshot, "fields"> & {
  fields: StructuredFieldView[];
};

type TrendItem = {
  key: string;
  label: string;
};

type TrendViewMode = "status" | "raw";

type TrendPoint = {
  value: number | null;
  rawValue: string;
  unit?: string;
  referenceRange?: string;
  state: ResultState;
  normalizedScore: number | null;
};

type TrendComparisonPanelProps = {
  loading: boolean;
  error: string;
  data?: TrendData;
};

const STATUS_SCORE_LIMIT = 3;
const THRESHOLD_STATUS_SCORE = 2;

const RESULT_STATE_META: Record<ResultState, { label: string; color: string }> = {
  high: { label: "偏高", color: "#dc2626" },
  low: { label: "偏低", color: "#2563eb" },
  normal: { label: "正常", color: "#16a34a" },
  threshold: { label: "阈值异常", color: "#d97706" },
  unknown: { label: "无法判定", color: "#8b9aa6" },
};

const SERIES_PALETTE = [
  "#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de",
  "#3ba272", "#fc8452", "#9a60b4", "#ea7ccc", "#48b8d0",
];

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function fieldKey(field: StructuredFieldView): string {
  return `${field.name}__${field.unit ?? ""}`;
}

function fieldLabel(field: StructuredFieldView): string {
  return field.unit ? `${field.name} (${field.unit})` : field.name;
}

function isAbnormalState(state?: ResultState): boolean {
  return state === "high" || state === "low" || state === "threshold";
}

function clampStatusScore(value: number | null): number | null {
  if (value === null || Number.isNaN(value)) {
    return null;
  }
  return Math.max(-STATUS_SCORE_LIMIT, Math.min(STATUS_SCORE_LIMIT, value));
}

function toNormalizedStatusScore(field: StructuredFieldView): number | null {
  if (field.resultState === "threshold") {
    return THRESHOLD_STATUS_SCORE;
  }

  const numericValue = field.numericValue;
  if (numericValue === undefined) {
    return null;
  }

  if (field.comparisonType === "range") {
    if (field.referenceLowerBound === undefined || field.referenceUpperBound === undefined) {
      return null;
    }
    if (field.referenceUpperBound === field.referenceLowerBound) {
      if (numericValue === field.referenceUpperBound) {
        return 0;
      }
      return numericValue > field.referenceUpperBound ? 1 : -1;
    }
    const center = (field.referenceLowerBound + field.referenceUpperBound) / 2;
    const halfRange = Math.abs(field.referenceUpperBound - field.referenceLowerBound) / 2;
    if (halfRange === 0) {
      return 0;
    }
    return (numericValue - center) / halfRange;
  }

  if (field.comparisonType === "upper_bound" && field.referenceUpperBound !== undefined) {
    if (numericValue <= field.referenceUpperBound) {
      return 0;
    }
    const scale = Math.max(Math.abs(field.referenceUpperBound), 1);
    return 1 + (numericValue - field.referenceUpperBound) / scale;
  }

  if (field.comparisonType === "lower_bound" && field.referenceLowerBound !== undefined) {
    if (numericValue >= field.referenceLowerBound) {
      return 0;
    }
    const scale = Math.max(Math.abs(field.referenceLowerBound), 1);
    return -1 - (field.referenceLowerBound - numericValue) / scale;
  }

  return null;
}

function buildReferenceRangeSeries(snapshots: TrendSnapshotView[], selectedKey: string) {
  const lowerBounds: Array<number | null> = [];
  const upperBounds: Array<number | null> = [];
  const thresholdLowerBounds: Array<number | null> = [];
  const thresholdUpperBounds: Array<number | null> = [];

  for (const snapshot of snapshots) {
    const field = snapshot.fields.find((item) => fieldKey(item) === selectedKey);
    if (!field) {
      lowerBounds.push(null);
      upperBounds.push(null);
      thresholdLowerBounds.push(null);
      thresholdUpperBounds.push(null);
      continue;
    }

    if (field.comparisonType === "range" || field.comparisonType === "lower_bound") {
      lowerBounds.push(field.referenceLowerBound ?? null);
    } else {
      lowerBounds.push(null);
    }
    if (field.comparisonType === "range" || field.comparisonType === "upper_bound") {
      upperBounds.push(field.referenceUpperBound ?? null);
    } else {
      upperBounds.push(null);
    }
    if (field.comparisonType === "threshold") {
      thresholdLowerBounds.push(field.referenceLowerBound ?? null);
      thresholdUpperBounds.push(field.referenceUpperBound ?? null);
    } else {
      thresholdLowerBounds.push(null);
      thresholdUpperBounds.push(null);
    }
  }

  const guideLines: Array<Record<string, unknown>> = [];
  if (lowerBounds.some((value) => value !== null)) {
    guideLines.push({
      name: "参考下限",
      type: "line",
      smooth: false,
      connectNulls: false,
      showSymbol: false,
      lineStyle: {
        width: 1.5,
        type: "dashed",
        color: "#64748b",
      },
      itemStyle: {
        color: "#64748b",
      },
      tooltip: {
        show: false,
      },
      emphasis: {
        disabled: true,
      },
      z: 1,
      data: lowerBounds,
    });
  }
  if (upperBounds.some((value) => value !== null)) {
    guideLines.push({
      name: "参考上限",
      type: "line",
      smooth: false,
      connectNulls: false,
      showSymbol: false,
      lineStyle: {
        width: 1.5,
        type: "dashed",
        color: "#94a3b8",
      },
      itemStyle: {
        color: "#94a3b8",
      },
      tooltip: {
        show: false,
      },
      emphasis: {
        disabled: true,
      },
      z: 1,
      data: upperBounds,
    });
  }
  if (thresholdLowerBounds.some((value) => value !== null)) {
    guideLines.push({
      name: "阈值下限",
      type: "line",
      smooth: false,
      connectNulls: false,
      showSymbol: false,
      lineStyle: {
        width: 1.5,
        type: "dashed",
        color: "#d97706",
      },
      itemStyle: {
        color: "#d97706",
      },
      tooltip: {
        show: false,
      },
      emphasis: {
        disabled: true,
      },
      z: 1,
      data: thresholdLowerBounds,
    });
  }
  if (thresholdUpperBounds.some((value) => value !== null)) {
    guideLines.push({
      name: "阈值上限",
      type: "line",
      smooth: false,
      connectNulls: false,
      showSymbol: false,
      lineStyle: {
        width: 1.5,
        type: "dashed",
        color: "#b45309",
      },
      itemStyle: {
        color: "#b45309",
      },
      tooltip: {
        show: false,
      },
      emphasis: {
        disabled: true,
      },
      z: 1,
      data: thresholdUpperBounds,
    });
  }
  return guideLines;
}

export function TrendComparisonPanel({ loading, error, data }: TrendComparisonPanelProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<TrendViewMode>("status");

  const normalizedSnapshots = useMemo<TrendSnapshotView[]>(() => {
    const snapshots = data?.snapshots ?? [];
    return snapshots.map((snapshot) => ({
      ...snapshot,
      fields: snapshot.fields.flatMap((field) => {
        const normalized = normalizeStructuredField(field);
        return normalized ? [normalized] : [];
      }),
    }));
  }, [data?.snapshots]);

  const abnormalItems = useMemo<TrendItem[]>(() => {
    const itemMap = new Map<string, TrendItem>();
    for (const snapshot of normalizedSnapshots) {
      for (const field of snapshot.fields) {
        if (!isAbnormalState(field.resultState)) {
          continue;
        }
        const key = fieldKey(field);
        if (!itemMap.has(key)) {
          itemMap.set(key, {
            key,
            label: fieldLabel(field),
          });
        }
      }
    }
    return Array.from(itemMap.values());
  }, [normalizedSnapshots]);

  const hasUnknownOnly = useMemo(() => {
    if (abnormalItems.length > 0) {
      return false;
    }
    return normalizedSnapshots.some((snapshot) =>
      snapshot.fields.some((field) => field.resultState === "unknown"),
    );
  }, [abnormalItems.length, normalizedSnapshots]);

  useEffect(() => {
    if (abnormalItems.length === 0) {
      setSelectedKeys([]);
      return;
    }
    setSelectedKeys((prev) => {
      const preserved = prev.filter((key) => abnormalItems.some((item) => item.key === key));
      return preserved.length > 0 ? preserved : [abnormalItems[0].key];
    });
  }, [abnormalItems]);

  const activeSingleIndex = useMemo(() => {
    if (abnormalItems.length === 0 || selectedKeys.length !== 1) {
      return -1;
    }
    return abnormalItems.findIndex((item) => item.key === selectedKeys[0]);
  }, [abnormalItems, selectedKeys]);

  const moveToNextSingleItem = () => {
    if (abnormalItems.length === 0) {
      return;
    }
    const nextIndex = activeSingleIndex >= 0 ? (activeSingleIndex + 1) % abnormalItems.length : 0;
    setSelectedKeys([abnormalItems[nextIndex].key]);
  };

  const canDrawChart = abnormalItems.length > 0 && selectedKeys.length > 0 && normalizedSnapshots.length > 0;

  useEffect(() => {
    if (!chartRef.current || !canDrawChart) {
      return;
    }
    const chart = echarts.init(chartRef.current);
    const itemMap = new Map<string, string>();
    for (const item of abnormalItems) {
      itemMap.set(item.key, item.label);
    }

    const xAxisDates = normalizedSnapshots.map((snapshot) => snapshot.recordDate);
    const isMultiRaw = viewMode === "raw" && selectedKeys.length > 1;
    const metricSeries = selectedKeys.map((key, index) => {
      const dataPoints = normalizedSnapshots.map<TrendPoint>((snapshot) => {
        const field = snapshot.fields.find((item) => fieldKey(item) === key);
        if (!field) {
          return {
            value: null,
            rawValue: "-",
            state: "unknown",
            normalizedScore: null,
          };
        }

        const normalizedScore = clampStatusScore(toNormalizedStatusScore(field));
        return {
          value: viewMode === "status" ? normalizedScore : field.numericValue ?? null,
          rawValue: field.value,
          unit: field.unit,
          referenceRange: field.referenceRange,
          state: field.resultState ?? "unknown",
          normalizedScore,
        };
      });
      const seriesColor = SERIES_PALETTE[index % SERIES_PALETTE.length];
      return {
        name: itemMap.get(key) ?? key,
        type: "line",
        smooth: true,
        connectNulls: false,
        symbolSize: isMultiRaw ? 10 : 8,
        ...(isMultiRaw ? { color: seriesColor } : {}),
        lineStyle: {
          width: 2,
        },
        itemStyle: {
          color: (params: { data?: TrendPoint }) => {
            const state = params?.data?.state ?? "unknown";
            return RESULT_STATE_META[state].color;
          },
        },
        ...(isMultiRaw
          ? {
              label: {
                show: true,
                formatter: (params: { data?: TrendPoint }) => {
                  const state = params?.data?.state;
                  if (state === "high") {
                    return "↑";
                  }
                  if (state === "low") {
                    return "↓";
                  }
                  if (state === "threshold") {
                    return "阈";
                  }
                  return "";
                },
                color: "inherit",
                fontSize: 12,
                fontWeight: "bold" as const,
                position: "top" as const,
              },
            }
          : {}),
        z: 3,
        data: dataPoints,
      };
    });

    const referenceRangeSeries =
      viewMode === "raw" && selectedKeys.length === 1
        ? buildReferenceRangeSeries(normalizedSnapshots, selectedKeys[0])
        : [];
    const series = [...metricSeries, ...referenceRangeSeries];

    chart.setOption({
      tooltip: {
        trigger: "item",
        formatter: (params: unknown) => {
          const item = params as {
            marker?: string;
            seriesName?: string;
            name?: string;
            data?: TrendPoint;
          };
          const point = item.data;
          const state = point?.state ?? "unknown";
          const stateText = RESULT_STATE_META[state].label;
          const valueText = point?.rawValue ? escapeHtml(point.rawValue) : "-";
          const rangeText = point?.referenceRange ? escapeHtml(point.referenceRange) : "-";
          const scoreText =
            point?.normalizedScore === null || point?.normalizedScore === undefined
              ? "-"
              : point.normalizedScore.toFixed(2);
          const modeExtra = viewMode === "status" ? `，状态值：${scoreText}` : "";
          const dateTitle = escapeHtml(String(item.name ?? ""));
          const seriesTitle = escapeHtml(String(item.seriesName ?? ""));
          return `<strong>${dateTitle}</strong><br/>${item.marker ?? ""}${seriesTitle}：${valueText}（${stateText}），参考范围：${rangeText}${modeExtra}`;
        },
      },
      legend: {
        type: "scroll",
        top: 4,
      },
      grid: {
        left: 52,
        right: 24,
        top: 56,
        bottom: 48,
      },
      xAxis: {
        type: "category",
        data: xAxisDates,
      },
      yAxis:
        viewMode === "status"
          ? {
              type: "value",
              name: "状态分值",
              min: -STATUS_SCORE_LIMIT,
              max: STATUS_SCORE_LIMIT,
              interval: 1,
              axisLabel: {
                formatter: (tickValue: number) => {
                  if (tickValue === THRESHOLD_STATUS_SCORE) {
                    return "阈值异常";
                  }
                  if (tickValue === 1) {
                    return "偏高阈值";
                  }
                  if (tickValue === 0) {
                    return "正常中线";
                  }
                  if (tickValue === -1) {
                    return "偏低阈值";
                  }
                  return String(tickValue);
                },
              },
            }
          : {
              type: "value",
              name: "检测数值",
            },
      series,
    } as echarts.EChartsOption);
    const onResize = () => chart.resize();
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
      chart.dispose();
    };
  }, [abnormalItems, canDrawChart, normalizedSnapshots, selectedKeys, viewMode]);

  if (loading) {
    return <p className="status-text">正在加载趋势对比...</p>;
  }
  if (error) {
    return <p className="status-text error">{error}</p>;
  }
  if (normalizedSnapshots.length === 0) {
    return <p className="muted">当前暂无可对比的历史报告。</p>;
  }
  if (abnormalItems.length === 0) {
    if (hasUnknownOnly) {
      return <p className="muted">当前未发现可明确判定的异常指标，部分项目因参考范围表达不完整暂无法判定。</p>;
    }
    return <p className="status-text success">当前趋势未发现异常指标。</p>;
  }

  return (
    <section className="trend-panel mt-10">
      <div className="trend-head">
        <h4 className="summary-heading">趋势对比（当前 + 前5次）</h4>
        <div className="trend-controls">
          <div className="trend-mode-toggle" role="tablist" aria-label="趋势视图切换">
            <button
              className={`trend-mode-btn ${viewMode === "status" ? "active" : ""}`}
              type="button"
              onClick={() => setViewMode("status")}
              aria-pressed={viewMode === "status"}
            >
              状态趋势
            </button>
            <button
              className={`trend-mode-btn ${viewMode === "raw" ? "active" : ""}`}
              type="button"
              onClick={() => setViewMode("raw")}
              aria-pressed={viewMode === "raw"}
            >
              原始数值
            </button>
          </div>
          <div className="trend-actions">
            <button
              className="btn btn-ghost btn-small"
              type="button"
              onClick={() => setSelectedKeys(abnormalItems.map((item) => item.key))}
              disabled={selectedKeys.length === abnormalItems.length}
            >
              全选
            </button>
            <button
              className="btn btn-ghost btn-small"
              type="button"
              onClick={() => setSelectedKeys([])}
              disabled={selectedKeys.length === 0}
            >
              全不选
            </button>
          </div>
        </div>
      </div>
      <div className="trend-state-legend" aria-hidden="true">
        <span className="trend-state-pill trend-state-normal">正常</span>
        <span className="trend-state-pill trend-state-high">偏高</span>
        <span className="trend-state-pill trend-state-low">偏低</span>
        <span className="trend-state-pill trend-state-threshold">阈值异常</span>
        <span className="trend-state-pill trend-state-unknown">无法判定</span>
      </div>
      {canDrawChart ? (
        <div className="trend-chart" ref={chartRef} />
      ) : (
        <p className="muted">请至少勾选一个异常项目后查看折线图。</p>
      )}
      <div className="trend-selectors">
        {abnormalItems.map((item) => {
          const checked = selectedKeys.includes(item.key);
          return (
            <label className="trend-checkbox" key={item.key}>
              <input
                type="checkbox"
                checked={checked}
                onChange={(event) => {
                  setSelectedKeys((prev) => {
                    if (event.target.checked) {
                      if (prev.includes(item.key)) {
                        return prev;
                      }
                      return [...prev, item.key];
                    }
                    return prev.filter((key) => key !== item.key);
                  });
                }}
              />
              <span>{item.label}</span>
            </label>
          );
        })}
        <div className="trend-next-wrap">
          <button
            className="trend-next-btn"
            type="button"
            onClick={moveToNextSingleItem}
            disabled={abnormalItems.length === 0}
          >
            下一个
          </button>
        </div>
      </div>
    </section>
  );
}
