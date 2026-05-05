import { describe, expect, it } from "vitest";
import { buildTrendSummary } from "./trendSummary";
import type { AgentTrendData } from "./types";

function trend(fieldsByDate: Array<{ recordDate: string; fields: AgentTrendData["snapshots"][number]["fields"] }>): AgentTrendData {
  return {
    recordId: "record-latest",
    sourceType: "LAB",
    diseaseProfileId: "profile-1",
    limit: 6,
    snapshots: fieldsByDate.map((snapshot, index) => ({
      recordId: `record-${index + 1}`,
      recordDate: snapshot.recordDate,
      title: `报告 ${index + 1}`,
      sourceType: "LAB",
      fields: snapshot.fields,
    })),
  };
}

describe("buildTrendSummary", () => {
  it("prioritizes current abnormal fields from the latest snapshot", () => {
    const summary = buildTrendSummary(
      trend([
        {
          recordDate: "2026-01-01",
          fields: [{ name: "总胆汁酸", value: "8", unit: "umol/L", numericValue: 8, resultState: "normal" }],
        },
        {
          recordDate: "2026-02-01",
          fields: [{ name: "总胆汁酸", value: "22", unit: "umol/L", numericValue: 22, resultState: "high" }],
        },
      ]),
      "肝功能",
    );

    expect(summary.state).toBe("attention");
    expect(summary.currentAbnormalCount).toBe(1);
    expect(summary.items[0]).toMatchObject({
      name: "总胆汁酸",
      currentValue: "22",
      previousValue: "8",
      kind: "current",
      direction: "up",
    });
  });

  it("reports historical abnormal fields when the latest value is normal", () => {
    const summary = buildTrendSummary(
      trend([
        {
          recordDate: "2026-01-01",
          fields: [{ name: "总胆汁酸", value: "22", unit: "umol/L", numericValue: 22, resultState: "high" }],
        },
        {
          recordDate: "2026-02-01",
          fields: [{ name: "总胆汁酸", value: "8", unit: "umol/L", numericValue: 8, resultState: "normal" }],
        },
      ]),
      "肝功能",
    );

    expect(summary.state).toBe("historical");
    expect(summary.historicalAbnormalCount).toBe(1);
    expect(summary.items[0]).toMatchObject({
      name: "总胆汁酸",
      kind: "historical",
      direction: "down",
    });
  });

  it("returns stable when latest fields are all normal and no history is abnormal", () => {
    const summary = buildTrendSummary(
      trend([
        {
          recordDate: "2026-01-01",
          fields: [{ name: "白蛋白", value: "43", unit: "g/L", numericValue: 43, resultState: "normal" }],
        },
        {
          recordDate: "2026-02-01",
          fields: [{ name: "白蛋白", value: "44", unit: "g/L", numericValue: 44, resultState: "normal" }],
        },
      ]),
      "肝功能",
    );

    expect(summary.state).toBe("stable");
    expect(summary.items).toEqual([]);
  });

  it("returns unknown when latest fields cannot be judged", () => {
    const summary = buildTrendSummary(
      trend([
        {
          recordDate: "2026-02-01",
          fields: [{ name: "视黄醇结合蛋白", value: "32", unit: "mg/L", resultState: "unknown" }],
        },
      ]),
      "肝功能",
    );

    expect(summary.state).toBe("unknown");
    expect(summary.unknownCount).toBe(1);
    expect(summary.items[0]).toMatchObject({
      name: "视黄醇结合蛋白",
      kind: "unknown",
      direction: "unknown",
    });
  });

  it("returns an empty summary when there are no snapshots", () => {
    const summary = buildTrendSummary(null, "肝功能");

    expect(summary.state).toBe("empty");
    expect(summary.snapshotCount).toBe(0);
    expect(summary.items).toEqual([]);
  });
});
