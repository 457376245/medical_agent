import type { AgentStructuredField, AgentTrendData } from "./types";

export type TrendSummaryState = "attention" | "historical" | "stable" | "unknown" | "empty";
export type TrendSummaryItemKind = "current" | "historical" | "unknown";
export type TrendValueDirection = "up" | "down" | "stable" | "unknown";

export type TrendSummaryItem = {
  key: string;
  name: string;
  unit?: string;
  currentValue: string;
  previousValue?: string;
  referenceRange?: string;
  resultState?: string;
  kind: TrendSummaryItemKind;
  direction: TrendValueDirection;
  recordDate?: string;
};

export type TrendSummary = {
  state: TrendSummaryState;
  title: string;
  detail: string;
  latestRecordDate?: string;
  snapshotCount: number;
  currentAbnormalCount: number;
  historicalAbnormalCount: number;
  unknownCount: number;
  items: TrendSummaryItem[];
  currentItems: TrendSummaryItem[];
  historicalItems: TrendSummaryItem[];
  unknownItems: TrendSummaryItem[];
};

function fieldKey(field: AgentStructuredField): string {
  return `${field.name}__${field.unit ?? ""}`;
}

function isAbnormalState(state?: string): boolean {
  return state === "high" || state === "low" || state === "threshold";
}

function toDirection(current?: number, previous?: number): TrendValueDirection {
  if (current === undefined || previous === undefined) {
    return "unknown";
  }
  const compared = compareNumber(current, previous);
  if (compared > 0) return "up";
  if (compared < 0) return "down";
  return "stable";
}

function compareNumber(left: number, right: number): number {
  if (left > right) return 1;
  if (left < right) return -1;
  return 0;
}

function findPreviousField(
  snapshots: AgentTrendData["snapshots"],
  latestIndex: number,
  key: string,
): AgentStructuredField | undefined {
  for (let index = latestIndex - 1; index >= 0; index -= 1) {
    const found = snapshots[index].fields.find((field) => fieldKey(field) === key);
    if (found) {
      return found;
    }
  }
  return undefined;
}

function hasHistoricalAbnormal(
  snapshots: AgentTrendData["snapshots"],
  latestIndex: number,
  key: string,
): boolean {
  for (let index = 0; index < latestIndex; index += 1) {
    const found = snapshots[index].fields.find((field) => fieldKey(field) === key);
    if (found && isAbnormalState(found.resultState)) {
      return true;
    }
  }
  return false;
}

function toSummaryItem(
  field: AgentStructuredField,
  previousField: AgentStructuredField | undefined,
  kind: TrendSummaryItemKind,
  recordDate?: string,
): TrendSummaryItem {
  return {
    key: fieldKey(field),
    name: field.name,
    unit: field.unit,
    currentValue: field.value,
    previousValue: previousField?.value,
    referenceRange: field.referenceRange,
    resultState: field.resultState,
    kind,
    direction: toDirection(field.numericValue, previousField?.numericValue),
    recordDate,
  };
}

export function buildTrendSummary(data: AgentTrendData | null | undefined, categoryName: string): TrendSummary {
  const snapshots = data?.snapshots ?? [];
  if (snapshots.length === 0) {
    return {
      state: "empty",
      title: `${categoryName}暂无可对比报告`,
      detail: "继续上传同一分类报告后，这里会按当前分类自动整理异常和变化。",
      snapshotCount: 0,
      currentAbnormalCount: 0,
      historicalAbnormalCount: 0,
      unknownCount: 0,
      items: [],
      currentItems: [],
      historicalItems: [],
      unknownItems: [],
    };
  }

  const latestIndex = snapshots.length - 1;
  const latestSnapshot = snapshots[latestIndex];
  const latestFields = latestSnapshot.fields ?? [];
  if (latestFields.length === 0) {
    return {
      state: "empty",
      title: `${categoryName}暂无结构化指标`,
      detail: "最新报告暂未提取到可用于趋势摘要的结构化字段。",
      latestRecordDate: latestSnapshot.recordDate,
      snapshotCount: snapshots.length,
      currentAbnormalCount: 0,
      historicalAbnormalCount: 0,
      unknownCount: 0,
      items: [],
      currentItems: [],
      historicalItems: [],
      unknownItems: [],
    };
  }

  const currentItems: TrendSummaryItem[] = [];
  const historicalItems: TrendSummaryItem[] = [];
  const unknownItems: TrendSummaryItem[] = [];

  for (const field of latestFields) {
    const key = fieldKey(field);
    const previousField = findPreviousField(snapshots, latestIndex, key);
    if (isAbnormalState(field.resultState)) {
      currentItems.push(toSummaryItem(field, previousField, "current", latestSnapshot.recordDate));
      continue;
    }
    if (field.resultState === "unknown") {
      unknownItems.push(toSummaryItem(field, previousField, "unknown", latestSnapshot.recordDate));
      continue;
    }
    if (hasHistoricalAbnormal(snapshots, latestIndex, key)) {
      historicalItems.push(toSummaryItem(field, previousField, "historical", latestSnapshot.recordDate));
    }
  }

  const groupedItems = [...currentItems, ...historicalItems, ...unknownItems];

  if (currentItems.length > 0) {
    return {
      state: "attention",
      title: `${categoryName}有 ${currentItems.length} 项当前异常`,
      detail: historicalItems.length > 0
        ? `另有 ${historicalItems.length} 项历史异常本次已恢复正常。`
        : "摘要已按当前选择的报告分类生成，优先展示最新报告中的异常指标。",
      latestRecordDate: latestSnapshot.recordDate,
      snapshotCount: snapshots.length,
      currentAbnormalCount: currentItems.length,
      historicalAbnormalCount: historicalItems.length,
      unknownCount: unknownItems.length,
      items: groupedItems,
      currentItems,
      historicalItems,
      unknownItems,
    };
  }

  if (historicalItems.length > 0) {
    return {
      state: "historical",
      title: `${categoryName}当前未见异常，${historicalItems.length} 项曾异常`,
      detail: "最新报告未见明确异常，下面列出历史曾异常且本次已有结果的指标。",
      latestRecordDate: latestSnapshot.recordDate,
      snapshotCount: snapshots.length,
      currentAbnormalCount: 0,
      historicalAbnormalCount: historicalItems.length,
      unknownCount: unknownItems.length,
      items: groupedItems,
      currentItems,
      historicalItems,
      unknownItems,
    };
  }

  if (unknownItems.length > 0) {
    return {
      state: "unknown",
      title: `${categoryName}有 ${unknownItems.length} 项暂无法判定`,
      detail: "最新报告未发现明确异常，但部分项目缺少完整参考范围或判定信息。",
      latestRecordDate: latestSnapshot.recordDate,
      snapshotCount: snapshots.length,
      currentAbnormalCount: 0,
      historicalAbnormalCount: 0,
      unknownCount: unknownItems.length,
      items: groupedItems,
      currentItems,
      historicalItems,
      unknownItems,
    };
  }

  return {
    state: "stable",
    title: `${categoryName}当前趋势未发现异常`,
    detail: "最新报告中的结构化指标均未见明确异常。",
    latestRecordDate: latestSnapshot.recordDate,
    snapshotCount: snapshots.length,
    currentAbnormalCount: 0,
    historicalAbnormalCount: 0,
    unknownCount: 0,
    items: [],
    currentItems: [],
    historicalItems: [],
    unknownItems: [],
  };
}
