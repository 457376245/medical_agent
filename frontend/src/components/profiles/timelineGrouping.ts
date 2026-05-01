export const REPORT_CATEGORY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "UPLOAD", label: "常规检查" },
  { value: "LAB", label: "检验报告" },
  { value: "IMAGING", label: "影像报告" },
  { value: "OUTPATIENT", label: "门诊记录" },
  { value: "DISCHARGE", label: "出院小结" },
  { value: "OTHER", label: "其他" },
];

export type TimelineRecord = {
  id: string;
  title: string;
  recordDate: string;
  sourceType: string;
};

export type TimelineExamNode = {
  examNodeId: string;
  anchorDate: string;
  dateRangeStart: string;
  dateRangeEnd: string;
  displayDate: string;
  records: TimelineRecord[];
};

export type GroupedCategory = {
  itemKey: string;
  categoryValue: string;
  categoryLabel: string;
  record: TimelineRecord;
};

export type GroupedDateItem = {
  id: string;
  date: string;
  displayDate: string;
  categories: GroupedCategory[];
};

export function normalizeCategory(raw?: string): string {
  const value = (raw ?? "UPLOAD").trim().toUpperCase();
  return value || "UPLOAD";
}

export function categoryLabel(categoryValue: string): string {
  return REPORT_CATEGORY_OPTIONS.find((item) => item.value === categoryValue)?.label ?? categoryValue;
}

export function categoryOrder(categoryValue: string): number {
  const index = REPORT_CATEGORY_OPTIONS.findIndex((item) => item.value === categoryValue);
  return index >= 0 ? index : REPORT_CATEGORY_OPTIONS.length + 1;
}

export function buildGroupedTimelineItems(
  records: TimelineRecord[],
  examNodes?: TimelineExamNode[],
): GroupedDateItem[] {
  if (examNodes && examNodes.length > 0) {
    return examNodes
      .map((node) => ({
        id: node.examNodeId,
        date: node.anchorDate,
        displayDate: node.dateRangeStart || node.anchorDate,
        categories: sortCategories(node.records.map(toGroupedCategory)),
      }))
      .sort((a, b) => String(b.date).localeCompare(String(a.date)));
  }

  const dateMap = new Map<string, GroupedCategory[]>();
  for (const record of records) {
    const date = record.recordDate;
    const next = toGroupedCategory(record);
    if (!dateMap.has(date)) {
      dateMap.set(date, [next]);
    } else {
      dateMap.get(date)?.push(next);
    }
  }

  return Array.from(dateMap.entries())
    .map(([date, categories]) => ({
      id: date,
      date,
      displayDate: date,
      categories: sortCategories(categories),
    }))
    .sort((a, b) => String(b.date).localeCompare(String(a.date)));
}

export function categoryButtonLabel(item: GroupedCategory, siblings: GroupedCategory[]): string {
  const duplicateCount = siblings.filter((candidate) => candidate.categoryValue === item.categoryValue).length;
  if (duplicateCount <= 1) {
    return item.categoryLabel;
  }
  return `${item.categoryLabel} - ${item.record.recordDate}`;
}

export function recordIdsForGroupedDateItem(item: GroupedDateItem): string[] {
  return item.categories.map((category) => category.record.id).filter(Boolean);
}

function toGroupedCategory(record: TimelineRecord): GroupedCategory {
  const categoryValue = normalizeCategory(record.sourceType);
  return {
    itemKey: record.id,
    categoryValue,
    categoryLabel: categoryLabel(categoryValue),
    record,
  };
}

function sortCategories(categories: GroupedCategory[]): GroupedCategory[] {
  return [...categories].sort((a, b) => {
    const orderDiff = categoryOrder(a.categoryValue) - categoryOrder(b.categoryValue);
    if (orderDiff !== 0) {
      return orderDiff;
    }
    const dateDiff = String(a.record.recordDate).localeCompare(String(b.record.recordDate));
    if (dateDiff !== 0) {
      return dateDiff;
    }
    return String(a.record.id).localeCompare(String(b.record.id));
  });
}
