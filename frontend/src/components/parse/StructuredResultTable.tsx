import { normalizeStructuredFields } from "./structuredFieldInterpretation";

function displayValuePrefix(resultState?: string): string {
  if (resultState === "high") {
    return "↑ ";
  }
  if (resultState === "low") {
    return "↓ ";
  }
  if (resultState === "threshold") {
    return "阈值异常 ";
  }
  return "";
}

function valueClassName(resultState?: string): string {
  if (resultState === "high") {
    return "result-value-high";
  }
  if (resultState === "low") {
    return "result-value-low";
  }
  if (resultState === "threshold") {
    return "result-value-threshold";
  }
  return "";
}

export function StructuredResultTable({ payload }: { payload: unknown }) {
  const fields = normalizeStructuredFields(payload);
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
            const resultState = field.resultState;
            const displayValue = `${displayValuePrefix(resultState)}${field.value}${field.unit ? ` ${field.unit}` : ""}`;
            return (
              <tr key={`${field.name}-${index}`}>
                <td>{field.name}</td>
                <td className={valueClassName(resultState)}>{displayValue}</td>
                <td>{field.referenceRange ?? "-"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
