"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts";

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

type TrendItem = {
  key: string;
  label: string;
};

type ResultState = "high" | "low" | "normal" | "unknown";
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

const RESULT_STATE_META: Record<ResultState, { label: string; color: string }> = {
  high: { label: "偏高", color: "#dc2626" },
  low: { label: "偏低", color: "#2563eb" },
  normal: { label: "正常", color: "#16a34a" },
  unknown: { label: "无法判定", color: "#8b9aa6" },
};

const SERIES_PALETTE = [
  "#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de",
  "#3ba272", "#fc8452", "#9a60b4", "#ea7ccc", "#48b8d0",
];

function toNumeric(value: string): number | null {
  const match = value.match(/[+-]?\d+(?:\.\d+)?/);
  if (!match) {
    return null;
  }
  const numeric = Number(match[0]);
  return Number.isFinite(numeric) ? numeric : null;
}

function extractRangeNumbers(value: string): number[] {
  const matches = value.match(/\d+(?:\.\d+)?/g);
  if (!matches) {
    return [];
  }
  return matches.map((item) => Number(item)).filter((item) => Number.isFinite(item));
}

function parseRangeBounds(referenceRange: string): {
  min?: number;
  max?: number;
  minInclusive?: boolean;
  maxInclusive?: boolean;
} | null {
  const normalized = referenceRange.replace(/\s+/g, "").replace(/～/g, "~");
  const numbers = extractRangeNumbers(normalized);
  if (numbers.length === 0) {
    return null;
  }
  if (normalized.startsWith("<=") || normalized.startsWith("≤")) {
    return { max: numbers[0], maxInclusive: true };
  }
  if (normalized.startsWith("<")) {
    return { max: numbers[0], maxInclusive: false };
  }
  if (normalized.startsWith(">=") || normalized.startsWith("≥")) {
    return { min: numbers[0], minInclusive: true };
  }
  if (normalized.startsWith(">")) {
    return { min: numbers[0], minInclusive: false };
  }
  if (numbers.length >= 2 && /[-~到至]/.test(normalized)) {
    const [first, second] = numbers;
    const min = Math.min(first, second);
    const max = Math.max(first, second);
    return { min, max, minInclusive: true, maxInclusive: true };
  }
  return null;
}

function resolveResultState(value: string, referenceRange?: string): ResultState {
  if (!referenceRange) {
    return "unknown";
  }
  const numericValue = toNumeric(value);
  if (numericValue === null) {
    return "unknown";
  }
  const bounds = parseRangeBounds(referenceRange);
  if (!bounds) {
    return "unknown";
  }
  if (bounds.max !== undefined) {
    if (bounds.maxInclusive === false ? numericValue >= bounds.max : numericValue > bounds.max) {
      return "high";
    }
  }
  if (bounds.min !== undefined) {
    if (bounds.minInclusive === false ? numericValue <= bounds.min : numericValue < bounds.min) {
      return "low";
    }
  }
  return "normal";
}

function toNormalizedStatusScore(value: string, referenceRange?: string): number | null {
  if (!referenceRange) {
    return null;
  }
  const numericValue = toNumeric(value);
  if (numericValue === null) {
    return null;
  }
  const bounds = parseRangeBounds(referenceRange);
  if (!bounds) {
    return null;
  }
  if (bounds.min !== undefined && bounds.max !== undefined) {
    if (bounds.max === bounds.min) {
      if (numericValue === bounds.max) {
        return 0;
      }
      return numericValue > bounds.max ? 1 : -1;
    }
    const center = (bounds.min + bounds.max) / 2;
    const halfRange = Math.abs(bounds.max - bounds.min) / 2;
    if (halfRange === 0) {
      return 0;
    }
    return (numericValue - center) / halfRange;
  }
  if (bounds.max !== undefined) {
    if (numericValue <= bounds.max) {
      return 0;
    }
    const scale = Math.max(Math.abs(bounds.max), 1);
    return 1 + (numericValue - bounds.max) / scale;
  }
  if (bounds.min !== undefined) {
    if (numericValue >= bounds.min) {
      return 0;
    }
    const scale = Math.max(Math.abs(bounds.min), 1);
    return -1 - (bounds.min - numericValue) / scale;
  }
  return null;
}

function clampStatusScore(value: number | null): number | null {
  if (value === null || Number.isNaN(value)) {
    return null;
  }
  return Math.max(-STATUS_SCORE_LIMIT, Math.min(STATUS_SCORE_LIMIT, value));
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function fieldKey(field: TrendField): string {
  return `${field.name}__${field.unit ?? ""}`;
}

function fieldLabel(field: TrendField): string {
  return field.unit ? `${field.name} (${field.unit})` : field.name;
}

export function TrendComparisonPanel({ loading, error, data }: TrendComparisonPanelProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<TrendViewMode>("status");

  const snapshots = data?.snapshots ?? [];
  const abnormalItems = useMemo<TrendItem[]>(() => {
    const itemMap = new Map<string, TrendItem>();
    for (const snapshot of snapshots) {
      for (const field of snapshot.fields ?? []) {
        const state = resolveResultState(field.value, field.referenceRange);
        if (state !== "high" && state !== "low") {
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
  }, [snapshots]);

  useEffect(() => {
    if (abnormalItems.length === 0) {
      setSelectedKeys([]);
      return;
    }
    setSelectedKeys([abnormalItems[0].key]);
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

  const canDrawChart = abnormalItems.length > 0 && selectedKeys.length > 0 && snapshots.length > 0;

  useEffect(() => {
    if (!chartRef.current || !canDrawChart) {
      return;
    }
    const chart = echarts.init(chartRef.current);
    const itemMap = new Map<string, string>();
    for (const item of abnormalItems) {
      itemMap.set(item.key, item.label);
    }
    const xAxisDates = snapshots.map((snapshot) => snapshot.recordDate);
    const isMultiRaw = viewMode === "raw" && selectedKeys.length > 1;
    const metricSeries = selectedKeys.map((key, index) => {
      const dataPoints = snapshots.map<TrendPoint>((snapshot) => {
        const field = snapshot.fields.find((item) => fieldKey(item) === key);
        if (!field) {
          return {
            value: null,
            rawValue: "-",
            state: "unknown",
            normalizedScore: null,
          };
        }
        const normalizedScore = clampStatusScore(toNormalizedStatusScore(field.value, field.referenceRange));
        const rawNumeric = toNumeric(field.value);
        return {
          value: viewMode === "status" ? normalizedScore : rawNumeric,
          rawValue: field.value,
          unit: field.unit,
          referenceRange: field.referenceRange,
          state: resolveResultState(field.value, field.referenceRange),
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
                  if (state === "high") return "↑";
                  if (state === "low") return "↓";
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
        ? (() => {
            const selectedKey = selectedKeys[0];
            const lowerBounds: Array<number | null> = [];
            const upperBounds: Array<number | null> = [];
            for (const snapshot of snapshots) {
              const field = snapshot.fields.find((item) => fieldKey(item) === selectedKey);
              if (!field?.referenceRange) {
                lowerBounds.push(null);
                upperBounds.push(null);
                continue;
              }
              const bounds = parseRangeBounds(field.referenceRange);
              lowerBounds.push(bounds?.min ?? null);
              upperBounds.push(bounds?.max ?? null);
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
            return guideLines;
          })()
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
  }, [abnormalItems, canDrawChart, selectedKeys, snapshots, viewMode]);

  if (loading) {
    return <p className="status-text">正在加载趋势对比...</p>;
  }
  if (error) {
    return <p className="status-text error">{error}</p>;
  }
  if (snapshots.length === 0) {
    return <p className="muted">当前暂无可对比的历史报告。</p>;
  }
  if (abnormalItems.length === 0) {
    return <p className="status-text success">你的指标正常，请继续保持。</p>;
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
