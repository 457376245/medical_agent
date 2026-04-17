export type ComparisonType = "range" | "upper_bound" | "lower_bound" | "threshold" | "none";
export type ResultState = "high" | "low" | "normal" | "threshold" | "unknown";

export type StructuredFieldView = {
  name: string;
  value: string;
  unit?: string;
  referenceRange?: string;
  numericValue?: number;
  comparisonType?: ComparisonType;
  resultState?: ResultState;
  referenceLowerBound?: number;
  referenceUpperBound?: number;
  referenceLowerInclusive?: boolean;
  referenceUpperInclusive?: boolean;
};

type ObservedOperator = "exact" | "gt" | "gte" | "lt" | "lte";
type SegmentLabelKind = "normal" | "high" | "low" | "unknown";

type ObservedValue = {
  numericValue: number | null;
  operator: ObservedOperator;
};

type ObservedInterval = {
  minValue: number;
  maxValue: number;
  minInclusive: boolean;
  maxInclusive: boolean;
};

type LabeledRangeSegment = {
  labelKind: SegmentLabelKind;
  comparisonType: ComparisonType;
  referenceLowerBound?: number;
  referenceUpperBound?: number;
  referenceLowerInclusive?: boolean;
  referenceUpperInclusive?: boolean;
};

type ReferenceInterpretation = {
  comparisonType: ComparisonType;
  referenceLowerBound?: number;
  referenceUpperBound?: number;
  referenceLowerInclusive?: boolean;
  referenceUpperInclusive?: boolean;
  isThreshold: boolean;
  labeledAbnormalSegments: LabeledRangeSegment[];
};

const LOWER_THRESHOLD_KEYWORDS = [
  "最低检测量",
  "检测下限",
  "定量下限",
  "最低检出限",
  "检出下限",
];

const UPPER_THRESHOLD_KEYWORDS = [
  "最高检测量",
  "检测上限",
  "定量上限",
  "最高检出限",
  "线性上限",
];

const NORMAL_RANGE_KEYWORDS = [
  "正常",
  "适宜",
  "理想",
  "目标",
  "参考",
];

const HIGH_RANGE_KEYWORDS = [
  "偏高",
  "增高",
  "升高",
  "很高",
  "高",
];

const LOW_RANGE_KEYWORDS = [
  "偏低",
  "降低",
  "低",
];

const NUMBER_TOKEN = "[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?";
const SCIENTIFIC_NOTATION_PATTERN = /([+-]?\d+(?:\.\d+)?)\s*[x×*]\s*10\s*\^?\s*([+-]?\d+)/gi;
const NUMBER_PATTERN = new RegExp(NUMBER_TOKEN, "i");
const RANGE_PATTERN = new RegExp(`(${NUMBER_TOKEN})\\s*(?:-+|\u2013|\u2014|~|到|至)\\s*(${NUMBER_TOKEN})`, "i");
const SEGMENT_SPLIT_PATTERN = /[;；\n，,]+/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function toOptionalText(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed || undefined;
}

function toOptionalNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const numeric = Number(trimmed);
  return Number.isFinite(numeric) ? numeric : undefined;
}

function toOptionalBoolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

function toComparisonType(value: unknown): ComparisonType | undefined {
  return value === "range" ||
    value === "upper_bound" ||
    value === "lower_bound" ||
    value === "threshold" ||
    value === "none"
    ? value
    : undefined;
}

function toResultState(value: unknown): ResultState | undefined {
  return value === "high" ||
    value === "low" ||
    value === "normal" ||
    value === "threshold" ||
    value === "unknown"
    ? value
    : undefined;
}

function normalizeNumericText(value?: string): string {
  if (!value) {
    return "";
  }
  return value
    .replaceAll("＋", "+")
    .replaceAll("－", "-")
    .replace(/(?<=\d),(?=\d)/g, "")
    .replace(SCIENTIFIC_NOTATION_PATTERN, "$1e$2")
    .replace(/\s+/g, "")
    .trim();
}

function parseNumberToken(value: string): number | undefined {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : undefined;
}

function findFirstNumber(value?: string): number | undefined {
  const normalized = normalizeNumericText(value);
  const match = normalized.match(NUMBER_PATTERN);
  return match?.[0] ? parseNumberToken(match[0]) : undefined;
}

function hasAnyKeyword(value: string, keywords: string[]): boolean {
  return keywords.some((keyword) => value.includes(keyword));
}

function parseObservedValue(rawValue?: string): ObservedValue {
  const normalized = normalizeNumericText(rawValue);
  if (!normalized) {
    return { numericValue: null, operator: "exact" };
  }

  let operator: ObservedOperator = "exact";
  if (normalized.startsWith(">=") || normalized.startsWith("≥")) {
    operator = "gte";
  } else if (normalized.startsWith(">")) {
    operator = "gt";
  } else if (normalized.startsWith("<=") || normalized.startsWith("≤")) {
    operator = "lte";
  } else if (normalized.startsWith("<")) {
    operator = "lt";
  }

  return {
    numericValue: findFirstNumber(normalized) ?? null,
    operator,
  };
}

function toObservedInterval(observedValue: ObservedValue): ObservedInterval | null {
  const { numericValue, operator } = observedValue;
  if (numericValue === null) {
    return null;
  }
  if (operator === "gt") {
    return { minValue: numericValue, maxValue: Number.POSITIVE_INFINITY, minInclusive: false, maxInclusive: false };
  }
  if (operator === "gte") {
    return { minValue: numericValue, maxValue: Number.POSITIVE_INFINITY, minInclusive: true, maxInclusive: false };
  }
  if (operator === "lt") {
    return { minValue: Number.NEGATIVE_INFINITY, maxValue: numericValue, minInclusive: false, maxInclusive: false };
  }
  if (operator === "lte") {
    return { minValue: Number.NEGATIVE_INFINITY, maxValue: numericValue, minInclusive: false, maxInclusive: true };
  }
  return { minValue: numericValue, maxValue: numericValue, minInclusive: true, maxInclusive: true };
}

function emptyInterpretation(): ReferenceInterpretation {
  return {
    comparisonType: "none",
    isThreshold: false,
    labeledAbnormalSegments: [],
  };
}

function interpretSimpleReferenceRange(referenceRange?: string): ReferenceInterpretation {
  const normalized = normalizeNumericText(referenceRange);
  if (!normalized) {
    return emptyInterpretation();
  }

  const firstNumber = findFirstNumber(normalized);
  if (normalized.startsWith("<=") || normalized.startsWith("≤")) {
    return {
      comparisonType: "upper_bound",
      referenceUpperBound: firstNumber,
      referenceUpperInclusive: true,
      isThreshold: false,
      labeledAbnormalSegments: [],
    };
  }
  if (normalized.startsWith("<")) {
    return {
      comparisonType: "upper_bound",
      referenceUpperBound: firstNumber,
      referenceUpperInclusive: false,
      isThreshold: false,
      labeledAbnormalSegments: [],
    };
  }
  if (normalized.startsWith(">=") || normalized.startsWith("≥")) {
    return {
      comparisonType: "lower_bound",
      referenceLowerBound: firstNumber,
      referenceLowerInclusive: true,
      isThreshold: false,
      labeledAbnormalSegments: [],
    };
  }
  if (normalized.startsWith(">")) {
    return {
      comparisonType: "lower_bound",
      referenceLowerBound: firstNumber,
      referenceLowerInclusive: false,
      isThreshold: false,
      labeledAbnormalSegments: [],
    };
  }

  const rangeMatch = normalized.match(RANGE_PATTERN);
  if (rangeMatch) {
    const first = parseNumberToken(rangeMatch[1]);
    const second = parseNumberToken(rangeMatch[2]);
    if (first !== undefined && second !== undefined) {
      return {
        comparisonType: "range",
        referenceLowerBound: Math.min(first, second),
        referenceUpperBound: Math.max(first, second),
        referenceLowerInclusive: true,
        referenceUpperInclusive: true,
        isThreshold: false,
        labeledAbnormalSegments: [],
      };
    }
  }

  return emptyInterpretation();
}

function findExpressionStart(normalizedSegment: string): number {
  for (let index = 0; index < normalizedSegment.length; index += 1) {
    const current = normalizedSegment[index];
    if (
      /\d/.test(current) ||
      current === "<" ||
      current === ">" ||
      current === "≤" ||
      current === "≥" ||
      current === "+" ||
      current === "-"
    ) {
      return index;
    }
  }
  return normalizedSegment.length;
}

function classifySegmentLabel(label: string): SegmentLabelKind {
  const normalizedLabel = label.replace(/[：:]+$/g, "").toLowerCase();
  if (!normalizedLabel) {
    return "unknown";
  }
  if (hasAnyKeyword(normalizedLabel, NORMAL_RANGE_KEYWORDS)) {
    return "normal";
  }
  if (hasAnyKeyword(normalizedLabel, HIGH_RANGE_KEYWORDS)) {
    return "high";
  }
  if (hasAnyKeyword(normalizedLabel, LOW_RANGE_KEYWORDS)) {
    return "low";
  }
  return "unknown";
}

function hasAnyBound(interpretation: ReferenceInterpretation | LabeledRangeSegment): boolean {
  return interpretation.referenceLowerBound !== undefined || interpretation.referenceUpperBound !== undefined;
}

function parseLabeledSegment(segment: string): LabeledRangeSegment {
  const normalizedSegment = normalizeNumericText(segment);
  const expressionStart = findExpressionStart(normalizedSegment);
  const label = expressionStart <= 0 ? "" : normalizedSegment.slice(0, expressionStart);
  const expression = normalizedSegment.slice(expressionStart);
  const simpleInterpretation = interpretSimpleReferenceRange(expression);

  return {
    labelKind: classifySegmentLabel(label),
    comparisonType: simpleInterpretation.comparisonType,
    referenceLowerBound: simpleInterpretation.referenceLowerBound,
    referenceUpperBound: simpleInterpretation.referenceUpperBound,
    referenceLowerInclusive: simpleInterpretation.referenceLowerInclusive,
    referenceUpperInclusive: simpleInterpretation.referenceUpperInclusive,
  };
}

function interpretLabeledReferenceRange(referenceRange?: string): ReferenceInterpretation | undefined {
  const normalized = normalizeNumericText(referenceRange);
  if (!normalized) {
    return undefined;
  }

  const segments = normalized.split(SEGMENT_SPLIT_PATTERN).filter(Boolean);
  if (segments.length < 2) {
    return undefined;
  }

  let hasRecognizedLabel = false;
  let normalSegment: LabeledRangeSegment | undefined;
  const abnormalSegments: LabeledRangeSegment[] = [];

  for (const rawSegment of segments) {
    const segment = parseLabeledSegment(rawSegment);
    if (segment.labelKind === "unknown") {
      continue;
    }
    hasRecognizedLabel = true;
    if (!hasAnyBound(segment)) {
      continue;
    }
    if (segment.labelKind === "normal" && !normalSegment) {
      normalSegment = segment;
      continue;
    }
    if (segment.labelKind === "high" || segment.labelKind === "low") {
      abnormalSegments.push(segment);
    }
  }

  if (!hasRecognizedLabel) {
    return undefined;
  }
  if (normalSegment) {
    return {
      comparisonType: normalSegment.comparisonType,
      referenceLowerBound: normalSegment.referenceLowerBound,
      referenceUpperBound: normalSegment.referenceUpperBound,
      referenceLowerInclusive: normalSegment.referenceLowerInclusive,
      referenceUpperInclusive: normalSegment.referenceUpperInclusive,
      isThreshold: false,
      labeledAbnormalSegments: [],
    };
  }
  if (abnormalSegments.length > 0) {
    return {
      comparisonType: "none",
      isThreshold: false,
      labeledAbnormalSegments: abnormalSegments,
    };
  }
  return undefined;
}

function interpretReferenceRange(referenceRange?: string): ReferenceInterpretation {
  const normalized = normalizeNumericText(referenceRange);
  if (!normalized) {
    return emptyInterpretation();
  }

  const firstNumber = findFirstNumber(normalized);
  if (hasAnyKeyword(normalized, LOWER_THRESHOLD_KEYWORDS)) {
    return {
      comparisonType: "threshold",
      referenceLowerBound: firstNumber,
      referenceLowerInclusive: true,
      isThreshold: true,
      labeledAbnormalSegments: [],
    };
  }
  if (hasAnyKeyword(normalized, UPPER_THRESHOLD_KEYWORDS)) {
    return {
      comparisonType: "threshold",
      referenceUpperBound: firstNumber,
      referenceUpperInclusive: true,
      isThreshold: true,
      labeledAbnormalSegments: [],
    };
  }

  const labeledInterpretation = interpretLabeledReferenceRange(normalized);
  if (labeledInterpretation) {
    return labeledInterpretation;
  }

  return interpretSimpleReferenceRange(normalized);
}

function isEntirelyAbove(interval: ObservedInterval, upperBound: number, upperInclusive = true): boolean {
  if (!Number.isFinite(interval.minValue)) {
    return false;
  }
  if (interval.minValue > upperBound) {
    return true;
  }
  return interval.minValue === upperBound && (!interval.minInclusive || !upperInclusive);
}

function isEntirelyBelow(interval: ObservedInterval, lowerBound: number, lowerInclusive = true): boolean {
  if (!Number.isFinite(interval.maxValue)) {
    return false;
  }
  if (interval.maxValue < lowerBound) {
    return true;
  }
  return interval.maxValue === lowerBound && (!interval.maxInclusive || !lowerInclusive);
}

function isEntirelyAtOrBelow(interval: ObservedInterval, upperBound: number, upperInclusive = true): boolean {
  if (!Number.isFinite(interval.maxValue)) {
    return false;
  }
  if (interval.maxValue < upperBound) {
    return true;
  }
  if (interval.maxValue > upperBound) {
    return false;
  }
  return upperInclusive || !interval.maxInclusive;
}

function isEntirelyAtOrAbove(interval: ObservedInterval, lowerBound: number, lowerInclusive = true): boolean {
  if (!Number.isFinite(interval.minValue)) {
    return false;
  }
  if (interval.minValue > lowerBound) {
    return true;
  }
  if (interval.minValue < lowerBound) {
    return false;
  }
  return lowerInclusive || !interval.minInclusive;
}

function matchesLabeledSegment(interval: ObservedInterval, segment: LabeledRangeSegment): boolean {
  if (segment.comparisonType === "range") {
    return segment.referenceLowerBound !== undefined &&
      segment.referenceUpperBound !== undefined &&
      isEntirelyAtOrAbove(interval, segment.referenceLowerBound, segment.referenceLowerInclusive) &&
      isEntirelyAtOrBelow(interval, segment.referenceUpperBound, segment.referenceUpperInclusive);
  }
  if (segment.comparisonType === "upper_bound") {
    return segment.referenceUpperBound !== undefined &&
      isEntirelyAtOrBelow(interval, segment.referenceUpperBound, segment.referenceUpperInclusive);
  }
  if (segment.comparisonType === "lower_bound") {
    return segment.referenceLowerBound !== undefined &&
      isEntirelyAtOrAbove(interval, segment.referenceLowerBound, segment.referenceLowerInclusive);
  }
  return false;
}

function resolveDirectSegmentState(
  interval: ObservedInterval,
  labeledAbnormalSegments: LabeledRangeSegment[],
): ResultState | undefined {
  for (const segment of labeledAbnormalSegments) {
    if (!matchesLabeledSegment(interval, segment)) {
      continue;
    }
    return segment.labelKind === "low" ? "low" : "high";
  }
  return undefined;
}

function resolveResultState(
  observedValue: ObservedValue,
  interpretation: ReferenceInterpretation,
): ResultState {
  if (interpretation.isThreshold) {
    return observedValue.numericValue !== null && hasAnyBound(interpretation) ? "threshold" : "unknown";
  }
  if (observedValue.numericValue === null) {
    return "unknown";
  }

  const interval = toObservedInterval(observedValue);
  if (!interval) {
    return "unknown";
  }

  const directSegmentState = resolveDirectSegmentState(interval, interpretation.labeledAbnormalSegments);
  if (directSegmentState) {
    return directSegmentState;
  }
  if (!hasAnyBound(interpretation)) {
    return "unknown";
  }

  if (interpretation.comparisonType === "range") {
    const lower = interpretation.referenceLowerBound;
    const upper = interpretation.referenceUpperBound;
    if (lower === undefined || upper === undefined) {
      return "unknown";
    }
    if (isEntirelyAbove(interval, upper, interpretation.referenceUpperInclusive)) {
      return "high";
    }
    if (isEntirelyBelow(interval, lower, interpretation.referenceLowerInclusive)) {
      return "low";
    }
    if (
      isEntirelyAtOrAbove(interval, lower, interpretation.referenceLowerInclusive) &&
      isEntirelyAtOrBelow(interval, upper, interpretation.referenceUpperInclusive)
    ) {
      return "normal";
    }
    return "unknown";
  }

  if (interpretation.comparisonType === "upper_bound") {
    const upper = interpretation.referenceUpperBound;
    if (upper === undefined) {
      return "unknown";
    }
    if (isEntirelyAbove(interval, upper, interpretation.referenceUpperInclusive)) {
      return "high";
    }
    if (isEntirelyAtOrBelow(interval, upper, interpretation.referenceUpperInclusive)) {
      return "normal";
    }
    return "unknown";
  }

  if (interpretation.comparisonType === "lower_bound") {
    const lower = interpretation.referenceLowerBound;
    if (lower === undefined) {
      return "unknown";
    }
    if (isEntirelyBelow(interval, lower, interpretation.referenceLowerInclusive)) {
      return "low";
    }
    if (isEntirelyAtOrAbove(interval, lower, interpretation.referenceLowerInclusive)) {
      return "normal";
    }
    return "unknown";
  }

  return "unknown";
}

function buildFallbackInterpretation(value?: string, referenceRange?: string) {
  const observedValue = parseObservedValue(value);
  const interpretation = interpretReferenceRange(referenceRange);
  return {
    numericValue: observedValue.numericValue ?? undefined,
    comparisonType: interpretation.comparisonType,
    resultState: resolveResultState(observedValue, interpretation),
    referenceLowerBound: interpretation.referenceLowerBound,
    referenceUpperBound: interpretation.referenceUpperBound,
    referenceLowerInclusive: interpretation.referenceLowerInclusive,
    referenceUpperInclusive: interpretation.referenceUpperInclusive,
  };
}

export function normalizeStructuredField(raw: unknown): StructuredFieldView | null {
  if (!isRecord(raw)) {
    return null;
  }

  const name = toOptionalText(raw.name);
  const value = toOptionalText(raw.value);
  if (!name || !value) {
    return null;
  }

  const unit = toOptionalText(raw.unit);
  const referenceRange = toOptionalText(raw.referenceRange ?? raw.reference_range);
  const fallback = buildFallbackInterpretation(value, referenceRange);

  const comparisonType = toComparisonType(raw.comparisonType ?? raw.comparison_type) ?? fallback.comparisonType;
  const referenceLowerBound =
    toOptionalNumber(raw.referenceLowerBound ?? raw.reference_lower_bound) ?? fallback.referenceLowerBound;
  const referenceUpperBound =
    toOptionalNumber(raw.referenceUpperBound ?? raw.reference_upper_bound) ?? fallback.referenceUpperBound;
  const referenceLowerInclusive =
    toOptionalBoolean(raw.referenceLowerInclusive ?? raw.reference_lower_inclusive) ?? fallback.referenceLowerInclusive;
  const referenceUpperInclusive =
    toOptionalBoolean(raw.referenceUpperInclusive ?? raw.reference_upper_inclusive) ?? fallback.referenceUpperInclusive;
  const numericValue = toOptionalNumber(raw.numericValue ?? raw.numeric_value) ?? fallback.numericValue;

  const interpretation: ReferenceInterpretation = {
    comparisonType,
    referenceLowerBound,
    referenceUpperBound,
    referenceLowerInclusive,
    referenceUpperInclusive,
    isThreshold: comparisonType === "threshold",
    labeledAbnormalSegments: [],
  };
  const observedValue = parseObservedValue(value);
  const resultState =
    toResultState(raw.resultState ?? raw.result_state) ?? resolveResultState(observedValue, interpretation);

  return {
    name,
    value,
    unit,
    referenceRange,
    numericValue,
    comparisonType,
    resultState,
    referenceLowerBound,
    referenceUpperBound,
    referenceLowerInclusive,
    referenceUpperInclusive,
  };
}

export function normalizeStructuredFields(payload: unknown): StructuredFieldView[] {
  if (!isRecord(payload) || !Array.isArray(payload.fields)) {
    return [];
  }

  return payload.fields.flatMap((item) => {
    const normalized = normalizeStructuredField(item);
    return normalized ? [normalized] : [];
  });
}
