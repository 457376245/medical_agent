package com.medical.agent.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medical.agent.domain.vo.TrendField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StructuredFieldInterpreter {
  private static final Logger log = LoggerFactory.getLogger(StructuredFieldInterpreter.class);

  private static final String NUMBER_TOKEN = "[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?";
  private static final Pattern SCIENTIFIC_NOTATION_PATTERN = Pattern.compile(
      "([+-]?\\d+(?:\\.\\d+)?)\\s*[x×*]\\s*10\\s*\\^?\\s*([+-]?\\d+)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBER_PATTERN = Pattern.compile(NUMBER_TOKEN);
  private static final Pattern RANGE_PATTERN = Pattern.compile(
      "(" + NUMBER_TOKEN + ")\\s*(?:-+|\u2013|\u2014|~|到|至)\\s*(" + NUMBER_TOKEN + ")",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern SEGMENT_SPLIT_PATTERN = Pattern.compile("[;；\\n，,]+");

  private static final List<String> LOWER_THRESHOLD_KEYWORDS = List.of(
      "最低检测量",
      "检测下限",
      "定量下限",
      "最低检出限",
      "检出下限");
  private static final List<String> UPPER_THRESHOLD_KEYWORDS = List.of(
      "最高检测量",
      "检测上限",
      "定量上限",
      "最高检出限",
      "线性上限");
  private static final List<String> NORMAL_RANGE_KEYWORDS = List.of(
      "正常",
      "适宜",
      "理想",
      "目标",
      "参考");
  private static final List<String> HIGH_RANGE_KEYWORDS = List.of(
      "偏高",
      "增高",
      "升高",
      "很高",
      "高");
  private static final List<String> LOW_RANGE_KEYWORDS = List.of(
      "偏低",
      "降低",
      "低");

  private final ObjectMapper objectMapper;
  private final IndicatorNormalizer indicatorNormalizer;

  StructuredFieldInterpreter(ObjectMapper objectMapper, IndicatorNormalizer indicatorNormalizer) {
    this.objectMapper = objectMapper;
    this.indicatorNormalizer = indicatorNormalizer;
  }

  JsonNode enrichPayload(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      return payload == null ? objectMapper.createObjectNode() : payload;
    }

    ObjectNode enrichedPayload = ((ObjectNode) payload).deepCopy();
    JsonNode fieldsNode = enrichedPayload.path("fields");
    if (!fieldsNode.isArray()) {
      return enrichedPayload;
    }

    for (JsonNode fieldNode : fieldsNode) {
      if (fieldNode.isObject()) {
        enrichFieldInPlace((ObjectNode) fieldNode);
      }
    }
    return enrichedPayload;
  }

  TrendField toTrendField(JsonNode fieldNode) {
    if (fieldNode == null || !fieldNode.isObject()) {
      return null;
    }
    ObjectNode enrichedField = ((ObjectNode) fieldNode).deepCopy();
    enrichFieldInPlace(enrichedField);
    String name = readText(enrichedField, "name");
    String value = readText(enrichedField, "value");
    if (name.isEmpty() || value.isEmpty()) {
      return null;
    }

    String unit = emptyToNull(readText(enrichedField, "unit"));
    String referenceRange = emptyToNull(readText(enrichedField, "referenceRange"));
    Double numericValue = readDouble(enrichedField, "numericValue");
    String comparisonType = emptyToNull(readText(enrichedField, "comparisonType"));
    String resultState = emptyToNull(readText(enrichedField, "resultState"));
    Double referenceLowerBound = readDouble(enrichedField, "referenceLowerBound");
    Double referenceUpperBound = readDouble(enrichedField, "referenceUpperBound");
    Boolean referenceLowerInclusive = readBoolean(enrichedField, "referenceLowerInclusive");
    Boolean referenceUpperInclusive = readBoolean(enrichedField, "referenceUpperInclusive");

    return new TrendField(
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
        referenceUpperInclusive);
  }

  private void enrichFieldInPlace(ObjectNode fieldNode) {
    String value = readText(fieldNode, "value");
    String referenceRange = readText(fieldNode, "referenceRange");

    ObservedValue observedValue = parseObservedValue(value);
    ReferenceRangeInterpretation interpretation = interpretReferenceRange(referenceRange);
    String resultState = resolveResultState(observedValue, interpretation);

    if (observedValue.numericValue() != null) {
      fieldNode.put("numericValue", observedValue.numericValue());
    } else {
      fieldNode.remove("numericValue");
    }

    fieldNode.put("comparisonType", interpretation.comparisonType());
    fieldNode.put("resultState", resultState);

    if (interpretation.referenceLowerBound() != null) {
      fieldNode.put("referenceLowerBound", interpretation.referenceLowerBound());
    } else {
      fieldNode.remove("referenceLowerBound");
    }
    if (interpretation.referenceUpperBound() != null) {
      fieldNode.put("referenceUpperBound", interpretation.referenceUpperBound());
    } else {
      fieldNode.remove("referenceUpperBound");
    }
    if (interpretation.referenceLowerInclusive() != null) {
      fieldNode.put("referenceLowerInclusive", interpretation.referenceLowerInclusive());
    } else {
      fieldNode.remove("referenceLowerInclusive");
    }
    if (interpretation.referenceUpperInclusive() != null) {
      fieldNode.put("referenceUpperInclusive", interpretation.referenceUpperInclusive());
    } else {
      fieldNode.remove("referenceUpperInclusive");
    }

    normalizeIndicatorInPlace(fieldNode);
  }

  private void normalizeIndicatorInPlace(ObjectNode fieldNode) {
    if (indicatorNormalizer == null) {
      return;
    }

    String existingCode = readText(fieldNode, "standardCode");
    if (!existingCode.isEmpty() && indicatorNormalizer.isValidCode(existingCode)) {
      IndicatorNormalizer.NormalizedIndicator meta = indicatorNormalizer.normalize(existingCode);
      if (meta != null && meta.category() != null) {
        fieldNode.put("category", meta.category());
      }
      return;
    }
    if (!existingCode.isEmpty()) {
      fieldNode.remove("standardCode");
    }

    String name = readText(fieldNode, "name");
    if (name.isEmpty()) {
      return;
    }
    IndicatorNormalizer.NormalizedIndicator result = indicatorNormalizer.normalize(name);
    if (result != null) {
      fieldNode.put("standardCode", result.code());
      if (result.category() != null) {
        fieldNode.put("category", result.category());
      }
    }
  }

  private String resolveResultState(ObservedValue observedValue, ReferenceRangeInterpretation interpretation) {
    if (interpretation.isThreshold()) {
      return observedValue.numericValue() != null && interpretation.hasAnyBound() ? "threshold" : "unknown";
    }
    if (observedValue.numericValue() == null) {
      return "unknown";
    }

    ObservedInterval observedInterval = observedValue.toInterval();
    if (observedInterval == null) {
      return "unknown";
    }

    String directSegmentState = resolveDirectSegmentState(observedInterval, interpretation.labeledAbnormalSegments());
    if (directSegmentState != null) {
      return directSegmentState;
    }
    if (!interpretation.hasAnyBound()) {
      return "unknown";
    }

    return switch (interpretation.comparisonType()) {
      case "range" -> resolveRangeState(observedInterval, interpretation);
      case "upper_bound" -> resolveUpperBoundState(observedInterval, interpretation);
      case "lower_bound" -> resolveLowerBoundState(observedInterval, interpretation);
      default -> "unknown";
    };
  }

  private String resolveDirectSegmentState(
      ObservedInterval observedInterval,
      List<LabeledRangeSegment> labeledAbnormalSegments) {
    for (LabeledRangeSegment segment : labeledAbnormalSegments) {
      if (!matchesSegment(observedInterval, segment)) {
        continue;
      }
      return segment.labelKind() == SegmentLabelKind.LOW ? "low" : "high";
    }
    return null;
  }

  private boolean matchesSegment(ObservedInterval observedInterval, LabeledRangeSegment segment) {
    return switch (segment.comparisonType()) {
      case "range" -> segment.referenceLowerBound() != null
          && segment.referenceUpperBound() != null
          && isEntirelyAtOrAbove(observedInterval, segment.referenceLowerBound(), segment.referenceLowerInclusive())
          && isEntirelyAtOrBelow(observedInterval, segment.referenceUpperBound(), segment.referenceUpperInclusive());
      case "upper_bound" -> segment.referenceUpperBound() != null
          && isEntirelyAtOrBelow(observedInterval, segment.referenceUpperBound(), segment.referenceUpperInclusive());
      case "lower_bound" -> segment.referenceLowerBound() != null
          && isEntirelyAtOrAbove(observedInterval, segment.referenceLowerBound(), segment.referenceLowerInclusive());
      default -> false;
    };
  }

  private String resolveRangeState(ObservedInterval observedInterval, ReferenceRangeInterpretation interpretation) {
    Double lowerBound = interpretation.referenceLowerBound();
    Double upperBound = interpretation.referenceUpperBound();
    if (lowerBound == null || upperBound == null) {
      return "unknown";
    }
    if (isEntirelyAbove(observedInterval, upperBound, interpretation.referenceUpperInclusive())) {
      return "high";
    }
    if (isEntirelyBelow(observedInterval, lowerBound, interpretation.referenceLowerInclusive())) {
      return "low";
    }
    if (isEntirelyAtOrAbove(observedInterval, lowerBound, interpretation.referenceLowerInclusive())
        && isEntirelyAtOrBelow(observedInterval, upperBound, interpretation.referenceUpperInclusive())) {
      return "normal";
    }
    return "unknown";
  }

  private String resolveUpperBoundState(ObservedInterval observedInterval, ReferenceRangeInterpretation interpretation) {
    Double upperBound = interpretation.referenceUpperBound();
    if (upperBound == null) {
      return "unknown";
    }
    if (isEntirelyAbove(observedInterval, upperBound, interpretation.referenceUpperInclusive())) {
      return "high";
    }
    if (isEntirelyAtOrBelow(observedInterval, upperBound, interpretation.referenceUpperInclusive())) {
      return "normal";
    }
    return "unknown";
  }

  private String resolveLowerBoundState(ObservedInterval observedInterval, ReferenceRangeInterpretation interpretation) {
    Double lowerBound = interpretation.referenceLowerBound();
    if (lowerBound == null) {
      return "unknown";
    }
    if (isEntirelyBelow(observedInterval, lowerBound, interpretation.referenceLowerInclusive())) {
      return "low";
    }
    if (isEntirelyAtOrAbove(observedInterval, lowerBound, interpretation.referenceLowerInclusive())) {
      return "normal";
    }
    return "unknown";
  }

  private boolean isEntirelyAbove(ObservedInterval observedInterval, double upperBound, Boolean upperInclusive) {
    if (Double.isInfinite(observedInterval.minValue())) {
      return false;
    }
    if (observedInterval.minValue() > upperBound) {
      return true;
    }
    return observedInterval.minValue() == upperBound
        && (!observedInterval.minInclusive() || !booleanOrDefault(upperInclusive, true));
  }

  private boolean isEntirelyBelow(ObservedInterval observedInterval, double lowerBound, Boolean lowerInclusive) {
    if (Double.isInfinite(observedInterval.maxValue())) {
      return false;
    }
    if (observedInterval.maxValue() < lowerBound) {
      return true;
    }
    return observedInterval.maxValue() == lowerBound
        && (!observedInterval.maxInclusive() || !booleanOrDefault(lowerInclusive, true));
  }

  private boolean isEntirelyAtOrBelow(ObservedInterval observedInterval, double upperBound, Boolean upperInclusive) {
    if (Double.isInfinite(observedInterval.maxValue())) {
      return false;
    }
    if (observedInterval.maxValue() < upperBound) {
      return true;
    }
    if (observedInterval.maxValue() > upperBound) {
      return false;
    }
    return booleanOrDefault(upperInclusive, true) || !observedInterval.maxInclusive();
  }

  private boolean isEntirelyAtOrAbove(ObservedInterval observedInterval, double lowerBound, Boolean lowerInclusive) {
    if (Double.isInfinite(observedInterval.minValue())) {
      return false;
    }
    if (observedInterval.minValue() > lowerBound) {
      return true;
    }
    if (observedInterval.minValue() < lowerBound) {
      return false;
    }
    return booleanOrDefault(lowerInclusive, true) || !observedInterval.minInclusive();
  }

  private ReferenceRangeInterpretation interpretReferenceRange(String referenceRange) {
    if (referenceRange == null || referenceRange.isBlank()) {
      return ReferenceRangeInterpretation.none();
    }

    String normalized = normalizeNumericText(referenceRange);
    String lowered = normalized.toLowerCase(Locale.ROOT);
    Double firstNumber = findFirstNumber(normalized);

    if (containsAny(lowered, LOWER_THRESHOLD_KEYWORDS)) {
      return new ReferenceRangeInterpretation(
          "threshold",
          firstNumber,
          null,
          Boolean.TRUE,
          null,
          true,
          List.of());
    }
    if (containsAny(lowered, UPPER_THRESHOLD_KEYWORDS)) {
      return new ReferenceRangeInterpretation(
          "threshold",
          null,
          firstNumber,
          null,
          Boolean.TRUE,
          true,
          List.of());
    }

    ReferenceRangeInterpretation labeledInterpretation = interpretLabeledReferenceRange(normalized);
    if (labeledInterpretation != null) {
      return labeledInterpretation;
    }

    return interpretSimpleReferenceRange(normalized);
  }

  private ReferenceRangeInterpretation interpretLabeledReferenceRange(String normalized) {
    String[] segments = SEGMENT_SPLIT_PATTERN.split(normalized);
    if (segments.length < 2) {
      return null;
    }

    boolean hasRecognizedLabel = false;
    LabeledRangeSegment normalSegment = null;
    List<LabeledRangeSegment> abnormalSegments = new ArrayList<>();

    for (String rawSegment : segments) {
      if (rawSegment == null || rawSegment.isBlank()) {
        continue;
      }
      LabeledRangeSegment segment = parseLabeledSegment(rawSegment);
      if (segment == null || segment.labelKind() == SegmentLabelKind.UNKNOWN) {
        continue;
      }
      hasRecognizedLabel = true;
      if (!segment.hasAnyBound()) {
        continue;
      }
      if (segment.labelKind() == SegmentLabelKind.NORMAL && normalSegment == null) {
        normalSegment = segment;
        continue;
      }
      if (segment.labelKind() == SegmentLabelKind.HIGH || segment.labelKind() == SegmentLabelKind.LOW) {
        abnormalSegments.add(segment);
      }
    }

    if (!hasRecognizedLabel) {
      return null;
    }
    if (normalSegment != null) {
      return new ReferenceRangeInterpretation(
          normalSegment.comparisonType(),
          normalSegment.referenceLowerBound(),
          normalSegment.referenceUpperBound(),
          normalSegment.referenceLowerInclusive(),
          normalSegment.referenceUpperInclusive(),
          false,
          List.of());
    }
    if (!abnormalSegments.isEmpty()) {
      return new ReferenceRangeInterpretation(
          "none",
          null,
          null,
          null,
          null,
          false,
          abnormalSegments);
    }
    return null;
  }

  private LabeledRangeSegment parseLabeledSegment(String segment) {
    String normalizedSegment = normalizeNumericText(segment);
    int expressionStart = findExpressionStart(normalizedSegment);
    String label = expressionStart <= 0 ? "" : normalizedSegment.substring(0, expressionStart);
    String expression = expressionStart < 0 ? normalizedSegment : normalizedSegment.substring(expressionStart);
    SegmentLabelKind labelKind = classifySegmentLabel(label);
    ReferenceRangeInterpretation simpleInterpretation = interpretSimpleReferenceRange(expression);
    return new LabeledRangeSegment(
        labelKind,
        simpleInterpretation.comparisonType(),
        simpleInterpretation.referenceLowerBound(),
        simpleInterpretation.referenceUpperBound(),
        simpleInterpretation.referenceLowerInclusive(),
        simpleInterpretation.referenceUpperInclusive());
  }

  private int findExpressionStart(String normalizedSegment) {
    for (int i = 0; i < normalizedSegment.length(); i++) {
      char current = normalizedSegment.charAt(i);
      if (Character.isDigit(current)
          || current == '<'
          || current == '>'
          || current == '≤'
          || current == '≥'
          || current == '+'
          || current == '-') {
        return i;
      }
    }
    return normalizedSegment.length();
  }

  private SegmentLabelKind classifySegmentLabel(String label) {
    String normalizedLabel = label
        .replaceAll("[：:]+$", "")
        .toLowerCase(Locale.ROOT);
    if (normalizedLabel.isBlank()) {
      return SegmentLabelKind.UNKNOWN;
    }
    if (containsAny(normalizedLabel, NORMAL_RANGE_KEYWORDS)) {
      return SegmentLabelKind.NORMAL;
    }
    if (containsAny(normalizedLabel, HIGH_RANGE_KEYWORDS)) {
      return SegmentLabelKind.HIGH;
    }
    if (containsAny(normalizedLabel, LOW_RANGE_KEYWORDS)) {
      return SegmentLabelKind.LOW;
    }
    return SegmentLabelKind.UNKNOWN;
  }

  private ReferenceRangeInterpretation interpretSimpleReferenceRange(String normalized) {
    if (normalized == null || normalized.isBlank()) {
      return ReferenceRangeInterpretation.none();
    }

    Double firstNumber = findFirstNumber(normalized);
    if (normalized.startsWith("<=") || normalized.startsWith("≤")) {
      return new ReferenceRangeInterpretation("upper_bound", null, firstNumber, null, Boolean.TRUE, false, List.of());
    }
    if (normalized.startsWith("<")) {
      return new ReferenceRangeInterpretation("upper_bound", null, firstNumber, null, Boolean.FALSE, false, List.of());
    }
    if (normalized.startsWith(">=") || normalized.startsWith("≥")) {
      return new ReferenceRangeInterpretation("lower_bound", firstNumber, null, Boolean.TRUE, null, false, List.of());
    }
    if (normalized.startsWith(">")) {
      return new ReferenceRangeInterpretation("lower_bound", firstNumber, null, Boolean.FALSE, null, false, List.of());
    }

    Matcher rangeMatcher = RANGE_PATTERN.matcher(normalized);
    if (rangeMatcher.find()) {
      Double first = parseDouble(rangeMatcher.group(1));
      Double second = parseDouble(rangeMatcher.group(2));
      if (first != null && second != null) {
        double lower = Math.min(first, second);
        double upper = Math.max(first, second);
        return new ReferenceRangeInterpretation("range", lower, upper, Boolean.TRUE, Boolean.TRUE, false, List.of());
      }
    }

    log.debug("无法解析参考范围: {}", normalized);
    return ReferenceRangeInterpretation.none();
  }

  private ObservedValue parseObservedValue(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return ObservedValue.unknown();
    }

    String normalized = normalizeNumericText(rawValue);
    ValueOperator operator = ValueOperator.fromRaw(normalized);
    Double numericValue = findFirstNumber(normalized);
    if (numericValue == null) {
      return ObservedValue.unknown();
    }
    return new ObservedValue(numericValue, operator);
  }

  private String normalizeNumericText(String rawValue) {
    if (rawValue == null) {
      return "";
    }

    String normalized = rawValue
        .replace('＋', '+')
        .replace('－', '-')
        .replaceAll("(?<=\\d),(?=\\d)", "")
        .trim();
    Matcher matcher = SCIENTIFIC_NOTATION_PATTERN.matcher(normalized);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(buffer, matcher.group(1) + "e" + matcher.group(2));
    }
    matcher.appendTail(buffer);
    return buffer.toString().replaceAll("\\s+", "");
  }

  private Double findFirstNumber(String value) {
    Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
    if (!matcher.find()) {
      return null;
    }
    return parseDouble(matcher.group());
  }

  private Double parseDouble(String value) {
    try {
      return Double.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private boolean containsAny(String value, List<String> keywords) {
    for (String keyword : keywords) {
      if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private String readText(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return "";
    }
    return value.asText("").trim();
  }

  private Double readDouble(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (!value.isNumber()) {
      return null;
    }
    return value.doubleValue();
  }

  private Boolean readBoolean(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (!value.isBoolean()) {
      return null;
    }
    return value.booleanValue();
  }

  private boolean booleanOrDefault(Boolean value, boolean fallback) {
    return value == null ? fallback : value;
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record ReferenceRangeInterpretation(
      String comparisonType,
      Double referenceLowerBound,
      Double referenceUpperBound,
      Boolean referenceLowerInclusive,
      Boolean referenceUpperInclusive,
      boolean isThreshold,
      List<LabeledRangeSegment> labeledAbnormalSegments) {
    static ReferenceRangeInterpretation none() {
      return new ReferenceRangeInterpretation("none", null, null, null, null, false, List.of());
    }

    boolean hasAnyBound() {
      return referenceLowerBound != null || referenceUpperBound != null;
    }
  }

  private record LabeledRangeSegment(
      SegmentLabelKind labelKind,
      String comparisonType,
      Double referenceLowerBound,
      Double referenceUpperBound,
      Boolean referenceLowerInclusive,
      Boolean referenceUpperInclusive) {
    boolean hasAnyBound() {
      return referenceLowerBound != null || referenceUpperBound != null;
    }
  }

  private enum SegmentLabelKind {
    NORMAL,
    HIGH,
    LOW,
    UNKNOWN
  }

  private record ObservedValue(Double numericValue, ValueOperator operator) {
    static ObservedValue unknown() {
      return new ObservedValue(null, ValueOperator.EXACT);
    }

    ObservedInterval toInterval() {
      if (numericValue == null) {
        return null;
      }
      return switch (operator) {
        case GREATER_THAN -> new ObservedInterval(numericValue, Double.POSITIVE_INFINITY, false, false);
        case GREATER_THAN_OR_EQUAL -> new ObservedInterval(numericValue, Double.POSITIVE_INFINITY, true, false);
        case LESS_THAN -> new ObservedInterval(Double.NEGATIVE_INFINITY, numericValue, false, false);
        case LESS_THAN_OR_EQUAL -> new ObservedInterval(Double.NEGATIVE_INFINITY, numericValue, false, true);
        case EXACT -> new ObservedInterval(numericValue, numericValue, true, true);
      };
    }
  }

  private record ObservedInterval(double minValue, double maxValue, boolean minInclusive, boolean maxInclusive) {
  }

  private enum ValueOperator {
    EXACT,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL;

    static ValueOperator fromRaw(String value) {
      String normalized = value == null ? "" : value.trim();
      if (normalized.startsWith(">=") || normalized.startsWith("≥")) {
        return GREATER_THAN_OR_EQUAL;
      }
      if (normalized.startsWith(">")) {
        return GREATER_THAN;
      }
      if (normalized.startsWith("<=") || normalized.startsWith("≤")) {
        return LESS_THAN_OR_EQUAL;
      }
      if (normalized.startsWith("<")) {
        return LESS_THAN;
      }
      return EXACT;
    }
  }
}
