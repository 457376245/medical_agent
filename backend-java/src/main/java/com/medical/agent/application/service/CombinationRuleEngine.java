package com.medical.agent.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CombinationRuleEngine {
  private static final Logger log = LoggerFactory.getLogger(CombinationRuleEngine.class);

  private final List<CombinationRule> rules;

  CombinationRuleEngine(ObjectMapper objectMapper) {
    List<CombinationRule> loaded = List.of();
    try (InputStream in = getClass().getResourceAsStream("/combination_rules.json")) {
      if (in != null) {
        loaded = objectMapper.readValue(in, new TypeReference<>() {});
      }
    } catch (IOException e) {
      log.warn("组合规则加载失败，联动分析将不可用", e);
    }
    this.rules = List.copyOf(loaded);
  }

  CombinationRuleEngine(List<CombinationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  List<TriggeredRule> analyze(JsonNode enrichedPayload) {
    if (enrichedPayload == null || !enrichedPayload.isObject()) {
      return List.of();
    }
    JsonNode fieldsNode = enrichedPayload.path("fields");
    if (!fieldsNode.isArray()) {
      return List.of();
    }

    Map<String, IndicatorField> indicatorMap = new HashMap<>();
    for (JsonNode fieldNode : fieldsNode) {
      if (!fieldNode.isObject()) {
        continue;
      }
      String standardCode = textOrNull(fieldNode, "standardCode");
      if (standardCode == null) {
        continue;
      }
      String resultState = textOrNull(fieldNode, "resultState");
      Double numericValue = doubleOrNull(fieldNode, "numericValue");
      indicatorMap.put(standardCode, new IndicatorField(standardCode, resultState, numericValue));
    }

    if (indicatorMap.isEmpty()) {
      return List.of();
    }
    return evaluate(indicatorMap);
  }

  List<TriggeredRule> evaluate(Map<String, IndicatorField> indicatorMap) {
    List<TriggeredRule> triggered = new ArrayList<>();
    for (CombinationRule rule : rules) {
      if (!hasAllRequired(rule.requiredIndicators(), indicatorMap)) {
        continue;
      }
      Map<String, Object> computedValues = new HashMap<>();
      if (allConditionsMet(rule.conditions(), indicatorMap, computedValues)) {
        triggered.add(toTriggeredRule(rule, computedValues));
      }
    }
    return triggered;
  }

  int ruleCount() {
    return rules.size();
  }

  private boolean hasAllRequired(List<String> required, Map<String, IndicatorField> indicatorMap) {
    if (required == null || required.isEmpty()) {
      return true;
    }
    for (String code : required) {
      if (!indicatorMap.containsKey(code)) {
        return false;
      }
    }
    return true;
  }

  private boolean allConditionsMet(
      List<RuleCondition> conditions,
      Map<String, IndicatorField> indicatorMap,
      Map<String, Object> computedValues) {
    if (conditions == null || conditions.isEmpty()) {
      return false;
    }
    for (RuleCondition condition : conditions) {
      if (!evaluateCondition(condition, indicatorMap, computedValues)) {
        return false;
      }
    }
    return true;
  }

  private boolean evaluateCondition(
      RuleCondition condition,
      Map<String, IndicatorField> indicatorMap,
      Map<String, Object> computedValues) {
    if (condition.isState()) {
      return evaluateStateCondition(condition, indicatorMap);
    }
    if (condition.isValue()) {
      return evaluateValueCondition(condition, indicatorMap);
    }
    if (condition.isRatio()) {
      return evaluateRatioCondition(condition, indicatorMap, computedValues);
    }
    return false;
  }

  private boolean evaluateStateCondition(RuleCondition condition, Map<String, IndicatorField> indicatorMap) {
    String indicator = condition.indicator();
    if (indicator == null) {
      return false;
    }
    IndicatorField field = indicatorMap.get(indicator);
    if (field == null) {
      return false;
    }
    if (condition.anyOf() != null && !condition.anyOf().isEmpty()) {
      return condition.anyOf().contains(field.resultState());
    }
    String expectedState = condition.resultState();
    if (expectedState == null) {
      return false;
    }
    return expectedState.equals(field.resultState());
  }

  private boolean evaluateValueCondition(RuleCondition condition, Map<String, IndicatorField> indicatorMap) {
    String indicator = condition.indicator();
    if (indicator == null) {
      return false;
    }
    IndicatorField field = indicatorMap.get(indicator);
    if (field == null || field.numericValue() == null) {
      return false;
    }
    return compareValue(field.numericValue(), condition.operator(), condition.threshold());
  }

  private boolean evaluateRatioCondition(
      RuleCondition condition,
      Map<String, IndicatorField> indicatorMap,
      Map<String, Object> computedValues) {
    String numerator = condition.numerator();
    String denominator = condition.denominator();
    if (numerator == null || denominator == null) {
      return false;
    }
    IndicatorField numField = indicatorMap.get(numerator);
    IndicatorField denField = indicatorMap.get(denominator);
    if (numField == null || denField == null
        || numField.numericValue() == null || denField.numericValue() == null) {
      return false;
    }
    if (denField.numericValue() == 0.0) {
      return false;
    }
    double ratio = numField.numericValue() / denField.numericValue();
    computedValues.put("ratio", Math.round(ratio * 100.0) / 100.0);
    return compareValue(ratio, condition.operator(), condition.threshold());
  }

  private boolean compareValue(double actual, String operator, Double threshold) {
    if (operator == null || threshold == null) {
      return false;
    }
    return switch (operator) {
      case ">" -> actual > threshold;
      case ">=" -> actual >= threshold;
      case "<" -> actual < threshold;
      case "<=" -> actual <= threshold;
      default -> false;
    };
  }

  private TriggeredRule toTriggeredRule(CombinationRule rule, Map<String, Object> computedValues) {
    CombinationRule.RuleInterpretation interp = rule.interpretation();
    String summary = interp != null ? interp.summary() : "";
    String detail = interp != null ? interpolate(interp.detail(), computedValues) : "";
    String suggestion = interp != null ? interp.suggestion() : "";
    return new TriggeredRule(
        rule.id(),
        rule.name(),
        rule.severity(),
        summary,
        detail,
        suggestion,
        rule.requiredIndicators());
  }

  private String interpolate(String template, Map<String, Object> values) {
    if (template == null || values.isEmpty()) {
      return template == null ? "" : template;
    }
    String result = template;
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
    }
    return result;
  }

  private static String textOrNull(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String text = value.asText("").trim();
    return text.isEmpty() ? null : text;
  }

  private static Double doubleOrNull(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (!value.isNumber()) {
      return null;
    }
    return value.doubleValue();
  }

  record IndicatorField(String standardCode, String resultState, Double numericValue) {}
}
