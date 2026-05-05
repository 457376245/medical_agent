"use client";

import { useEffect, useMemo, useRef } from "react";
import * as echarts from "echarts";
import type { AgentTrendData } from "./types";

export function AgentTrendChart({ trendData }: { trendData: AgentTrendData | null }) {
  const chartRef = useRef<HTMLDivElement>(null);

  const seriesFields = useMemo(() => {
    const snapshots = trendData?.snapshots ?? [];
    const names = new Set<string>();
    for (const snapshot of snapshots) {
      for (const field of snapshot.fields) {
        if (field.name && /^-?\d+(\.\d+)?$/.test(field.value)) {
          names.add(field.name);
        }
      }
    }
    return Array.from(names).slice(0, 5);
  }, [trendData]);

  useEffect(() => {
    if (!chartRef.current || !trendData || trendData.snapshots.length === 0 || seriesFields.length === 0) {
      return;
    }
    const chart = echarts.init(chartRef.current);
    const dates = trendData.snapshots.map((snapshot) => snapshot.recordDate || snapshot.title);
    chart.setOption({
      color: ["#0e7490", "#059669", "#9a5b1f", "#7c3aed", "#dc2626"],
      tooltip: { trigger: "axis" },
      grid: { left: 42, right: 18, top: 34, bottom: 36 },
      xAxis: {
        type: "category",
        data: dates,
        axisLine: { lineStyle: { color: "#b7d5df" } },
        axisLabel: { color: "#536b78" },
      },
      yAxis: {
        type: "value",
        axisLine: { show: false },
        splitLine: { lineStyle: { color: "#d2e1e8" } },
        axisLabel: { color: "#536b78" },
      },
      series: seriesFields.map((name) => ({
        name,
        type: "line",
        smooth: true,
        symbolSize: 7,
        data: trendData.snapshots.map((snapshot) => {
          const field = snapshot.fields.find((item) => item.name === name);
          return field ? Number(field.value) : null;
        }),
      })),
    });

    const handleResize = () => chart.resize();
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      chart.dispose();
    };
  }, [seriesFields, trendData]);

  if (!trendData || trendData.snapshots.length === 0) {
    return <div className="agent-chart-empty">还没有可用于趋势展示的报告。</div>;
  }
  if (seriesFields.length === 0) {
    return <div className="agent-chart-empty">当前报告缺少可绘制的数值型指标。</div>;
  }
  return <div className="agent-trend-chart" ref={chartRef} role="img" aria-label="指标趋势图" />;
}
