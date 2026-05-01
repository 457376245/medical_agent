import { describe, expect, it } from "vitest";

import {
  buildGroupedTimelineItems,
  categoryButtonLabel,
  recordIdsForGroupedDateItem,
  type TimelineExamNode,
  type TimelineRecord,
} from "./timelineGrouping";

describe("timelineGrouping", () => {
  it("uses backend exam nodes instead of splitting close report dates", () => {
    const examNodes: TimelineExamNode[] = [
      {
        examNodeId: "node-1",
        anchorDate: "2026-04-01",
        dateRangeStart: "2026-04-01",
        dateRangeEnd: "2026-04-04",
        displayDate: "2026-04-01 至 2026-04-04",
        records: [
          record("lab-1", "2026-04-01", "LAB"),
          record("image-1", "2026-04-03", "IMAGING"),
          record("visit-1", "2026-04-04", "OUTPATIENT"),
        ],
      },
    ];

    const grouped = buildGroupedTimelineItems([], examNodes);

    expect(grouped).toHaveLength(1);
    expect(grouped[0]?.displayDate).toBe("2026-04-01");
    expect(grouped[0]?.categories.map((item) => item.record.id)).toEqual(["lab-1", "image-1", "visit-1"]);
  });

  it("falls back to exact record dates when backend exam nodes are absent", () => {
    const grouped = buildGroupedTimelineItems([
      record("lab-1", "2026-04-01", "LAB"),
      record("image-1", "2026-04-03", "IMAGING"),
    ]);

    expect(grouped).toHaveLength(2);
    expect(grouped.map((item) => item.displayDate)).toEqual(["2026-04-03", "2026-04-01"]);
  });

  it("keeps same source type reports selectable by record id", () => {
    const examNodes: TimelineExamNode[] = [
      {
        examNodeId: "node-1",
        anchorDate: "2026-04-01",
        dateRangeStart: "2026-04-01",
        dateRangeEnd: "2026-04-03",
        displayDate: "2026-04-01 至 2026-04-03",
        records: [
          record("lab-1", "2026-04-01", "LAB"),
          record("lab-2", "2026-04-03", "LAB"),
        ],
      },
    ];

    const categories = buildGroupedTimelineItems([], examNodes)[0]?.categories ?? [];

    expect(categories.map((item) => item.itemKey)).toEqual(["lab-1", "lab-2"]);
    expect(categories.map((item) => categoryButtonLabel(item, categories))).toEqual([
      "检验报告 - 2026-04-01",
      "检验报告 - 2026-04-03",
    ]);
  });

  it("extracts all record ids from a grouped date item for node-level batch deletion", () => {
    const examNodes: TimelineExamNode[] = [
      {
        examNodeId: "node-1",
        anchorDate: "2026-04-01",
        dateRangeStart: "2026-04-01",
        dateRangeEnd: "2026-04-03",
        displayDate: "2026-04-01 至 2026-04-03",
        records: [
          record("lab-1", "2026-04-01", "LAB"),
          record("lab-2", "2026-04-03", "LAB"),
          record("image-1", "2026-04-03", "IMAGING"),
        ],
      },
    ];

    const groupedItem = buildGroupedTimelineItems([], examNodes)[0];

    expect(groupedItem ? recordIdsForGroupedDateItem(groupedItem) : []).toEqual(["lab-1", "lab-2", "image-1"]);
  });

  function record(id: string, recordDate: string, sourceType: string): TimelineRecord {
    return {
      id,
      recordDate,
      sourceType,
      title: `${sourceType}-${recordDate}`,
    };
  }
});
