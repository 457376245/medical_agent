type StructuredField = {
  name: string;
  value: string;
  unit?: string;
  referenceRange?: string;
};

type RangeBounds = {
  min?: number;
  max?: number;
  minInclusive?: boolean;
  maxInclusive?: boolean;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

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

function parseRangeBounds(referenceRange: string): RangeBounds | null {
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
    if (first <= second) {
      return { min: first, max: second, minInclusive: true, maxInclusive: true };
    }
    return { min: second, max: first, minInclusive: true, maxInclusive: true };
  }

  return null;
}

function resolveResultState(value: string, referenceRange?: string): "high" | "low" | "normal" {
  if (!referenceRange) {
    return "normal";
  }
  const numericValue = toNumeric(value);
  if (numericValue === null) {
    return "normal";
  }
  const bounds = parseRangeBounds(referenceRange);
  if (!bounds) {
    return "normal";
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

function toStructuredFields(payload: unknown): StructuredField[] {
  if (!isRecord(payload)) {
    return [];
  }

  const rawFields = payload.fields;
  if (!Array.isArray(rawFields)) {
    return [];
  }

  return rawFields.flatMap((item) => {
    if (!isRecord(item)) {
      return [];
    }

    const name = typeof item.name === "string" ? item.name.trim() : "";
    const value = typeof item.value === "string" ? item.value.trim() : "";
    if (!name || !value) {
      return [];
    }

    const unit = typeof item.unit === "string" && item.unit.trim() ? item.unit.trim() : undefined;
    const referenceRange =
      typeof item.referenceRange === "string" && item.referenceRange.trim() ? item.referenceRange.trim() : undefined;

    return [{ name, value, unit, referenceRange }];
  });
}

export function StructuredResultTable({ payload }: { payload: unknown }) {
  const fields = toStructuredFields(payload);
  if (fields.length === 0) {
    return <p className="muted">暂无可展示的结构化字段。</p>;
  }

  return (
    <div className="result-table-wrap">
      <table className="result-table">
        <thead>
          <tr>
            <th>项目名称</th>
            <th>结果</th>
            <th>参考范围</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((field, index) => {
            const resultState = resolveResultState(field.value, field.referenceRange);
            const valueClass =
              resultState === "high" ? "result-value-high" : resultState === "low" ? "result-value-low" : "";
            const withArrow =
              resultState === "high" ? `↑ ${field.value}` : resultState === "low" ? `↓ ${field.value}` : field.value;
            const displayValue = field.unit ? `${withArrow} ${field.unit}` : withArrow;
            return (
              <tr key={`${field.name}-${index}`}>
                <td>{field.name}</td>
                <td className={valueClass}>{displayValue}</td>
                <td>{field.referenceRange ?? "-"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
