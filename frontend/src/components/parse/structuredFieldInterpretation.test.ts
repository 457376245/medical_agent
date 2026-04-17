import { describe, expect, it } from "vitest";

import {
  normalizeStructuredField,
  normalizeStructuredFields,
} from "./structuredFieldInterpretation";

describe("structuredFieldInterpretation", () => {
  it("检测下限与科学计数法结果应识别为阈值异常", () => {
    const field = normalizeStructuredField({
      name: "HBV-DNA",
      value: ">1.00×10^8 IU/ml",
      referenceRange: "最低检测量 50IU/mL",
    });

    expect(field).not.toBeNull();
    expect(field?.comparisonType).toBe("threshold");
    expect(field?.resultState).toBe("threshold");
    expect(field?.numericValue).toBe(100000000);
    expect(field?.referenceLowerBound).toBe(50);
  });

  it("普通区间结果应区分偏高与正常", () => {
    const payload = {
      fields: [
        {
          name: "葡萄糖",
          value: "6.3",
          referenceRange: "3.9-6.1",
        },
        {
          name: "葡萄糖",
          value: "5.0",
          referenceRange: "3.9-6.1",
        },
      ],
    };

    const fields = normalizeStructuredFields(payload);

    expect(fields[0]?.resultState).toBe("high");
    expect(fields[1]?.resultState).toBe("normal");
  });

  it("后端已增强字段应优先保留", () => {
    const field = normalizeStructuredField({
      name: "HBV-DNA",
      value: ">1.00×10^8 IU/ml",
      referenceRange: "最低检测量 50IU/mL",
      numericValue: 100000000,
      comparisonType: "threshold",
      resultState: "threshold",
      referenceLowerBound: 50,
      referenceLowerInclusive: true,
    });

    expect(field).toMatchObject({
      numericValue: 100000000,
      comparisonType: "threshold",
      resultState: "threshold",
      referenceLowerBound: 50,
      referenceLowerInclusive: true,
    });
  });

  it("多段带标签参考范围应按正常基线判定异常", () => {
    const payload = {
      fields: [
        {
          name: "非高密度脂蛋白胆固醇",
          value: "4.17",
          referenceRange: "适宜<4.10 mmol/L;增高4.10-4.90;很高>4.90",
        },
        {
          name: "甘油三酯",
          value: "2.15",
          referenceRange: "适宜<1.70 mmol/L;增高1.70-2.30;很高>2.30",
        },
        {
          name: "低密度脂蛋白胆固醇",
          value: "3.69",
          referenceRange: "适宜<3.40 mmol/L;增高3.40-4.10;很高>4.10",
        },
        {
          name: "高密度脂蛋白胆固醇",
          value: "2.18",
          referenceRange: ">1.04 mmol/L",
        },
      ],
    };

    const fields = normalizeStructuredFields(payload);

    expect(fields[0]).toMatchObject({
      comparisonType: "upper_bound",
      resultState: "high",
      referenceUpperBound: 4.1,
    });
    expect(fields[1]?.resultState).toBe("high");
    expect(fields[2]?.resultState).toBe("high");
    expect(fields[3]).toMatchObject({
      comparisonType: "lower_bound",
      resultState: "normal",
      referenceLowerBound: 1.04,
    });
  });
});
