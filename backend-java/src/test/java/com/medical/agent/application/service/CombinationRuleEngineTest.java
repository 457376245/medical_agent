package com.medical.agent.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CombinationRuleEngineTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CombinationRuleEngine engine = new CombinationRuleEngine(objectMapper);

  @Test
  void rulesLoadedFromClasspath() {
    assertTrue(engine.ruleCount() >= 10, "至少应加载 10 条 MVP 规则");
  }

  @Test
  void liverAlcoholicTriggersWhenAstAltRatioHighAndGgtHigh() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("AST", field("AST", "high", 120.0));
    map.put("ALT", field("ALT", "high", 50.0));
    map.put("GGT", field("GGT", "high", 80.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "LIVER_ALCOHOLIC".equals(r.ruleId())));
  }

  @Test
  void liverAlcoholicDoesNotTriggerWhenRatioLow() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("AST", field("AST", "high", 60.0));
    map.put("ALT", field("ALT", "high", 50.0));
    map.put("GGT", field("GGT", "high", 80.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "LIVER_ALCOHOLIC".equals(r.ruleId())));
  }

  @Test
  void liverCholestasisTriggersWhenAlpGgtHighAltNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("ALP", field("ALP", "high", 200.0));
    map.put("GGT", field("GGT", "high", 80.0));
    map.put("ALT", field("ALT", "normal", 25.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "LIVER_CHOLESTASIS".equals(r.ruleId())));
  }

  @Test
  void liverCholestasisDoesNotTriggerWhenAltHigh() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("ALP", field("ALP", "high", 200.0));
    map.put("GGT", field("GGT", "high", 80.0));
    map.put("ALT", field("ALT", "high", 90.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "LIVER_CHOLESTASIS".equals(r.ruleId())));
  }

  @Test
  void liverHepatocellTriggersWhenAltAndDbilHigh() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("ALT", field("ALT", "high", 90.0));
    map.put("DBIL", field("DBIL", "high", 12.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "LIVER_HEPATOCELL".equals(r.ruleId())));
  }

  @Test
  void liverHepatocellDoesNotTriggerWhenDbilNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("ALT", field("ALT", "high", 90.0));
    map.put("DBIL", field("DBIL", "normal", 3.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "LIVER_HEPATOCELL".equals(r.ruleId())));
  }

  @Test
  void renalDeclineTriggersWhenCreaAndBunHigh() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("CREA", field("CREA", "high", 150.0));
    map.put("BUN", field("BUN", "high", 10.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "RENAL_DECLINE".equals(r.ruleId())));
  }

  @Test
  void renalDeclineDoesNotTriggerWhenCreaNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("CREA", field("CREA", "normal", 80.0));
    map.put("BUN", field("BUN", "high", 10.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "RENAL_DECLINE".equals(r.ruleId())));
  }

  @Test
  void renalPrerenalTriggersWhenBunHighCreaNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("BUN", field("BUN", "high", 10.0));
    map.put("CREA", field("CREA", "normal", 80.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "RENAL_PRERENAL".equals(r.ruleId())));
  }

  @Test
  void renalPrerenalDoesNotTriggerWhenBunNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("BUN", field("BUN", "normal", 5.0));
    map.put("CREA", field("CREA", "normal", 80.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "RENAL_PRERENAL".equals(r.ruleId())));
  }

  @Test
  void dmPrediabetesTriggersWhenGluInRange() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("GLU", field("GLU", "high", 6.5));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "DM_PREDIABETES".equals(r.ruleId())));
  }

  @Test
  void dmPrediabetesDoesNotTriggerWhenGluNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("GLU", field("GLU", "normal", 5.5));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "DM_PREDIABETES".equals(r.ruleId())));
  }

  @Test
  void dmUncontrolledTriggersWhenHba1cHigh() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("HBA1C", field("HBA1C", "high", 8.5));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "DM_UNCONTROLLED".equals(r.ruleId())));
  }

  @Test
  void dmUncontrolledDoesNotTriggerWhenHba1cNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("HBA1C", field("HBA1C", "normal", 5.5));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "DM_UNCONTROLLED".equals(r.ruleId())));
  }

  @Test
  void thyroidHypoTriggersWhenTshHighFt4Low() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "high", 15.0));
    map.put("FT4", field("FT4", "low", 5.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "THYROID_HYPO".equals(r.ruleId())));
  }

  @Test
  void thyroidHypoDoesNotTriggerWhenFt4Normal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "high", 15.0));
    map.put("FT4", field("FT4", "normal", 14.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "THYROID_HYPO".equals(r.ruleId())));
  }

  @Test
  void thyroidHyperTriggersWhenTshLowFt4High() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "low", 0.1));
    map.put("FT4", field("FT4", "high", 35.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "THYROID_HYPER".equals(r.ruleId())));
  }

  @Test
  void thyroidHyperDoesNotTriggerWhenTshNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "normal", 2.5));
    map.put("FT4", field("FT4", "high", 35.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "THYROID_HYPER".equals(r.ruleId())));
  }

  @Test
  void thyroidSubclinicalTriggersWhenTshAbnormalFt4Normal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "high", 8.0));
    map.put("FT4", field("FT4", "normal", 14.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertTrue(results.stream().anyMatch(r -> "THYROID_SUBCLINICAL".equals(r.ruleId())));
  }

  @Test
  void thyroidSubclinicalDoesNotTriggerWhenTshNormal() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("TSH", field("TSH", "normal", 2.5));
    map.put("FT4", field("FT4", "normal", 14.0));

    List<TriggeredRule> results = engine.evaluate(map);
    assertFalse(results.stream().anyMatch(r -> "THYROID_SUBCLINICAL".equals(r.ruleId())));
  }

  @Test
  void emptyIndicatorMapTriggersNoRules() {
    List<TriggeredRule> results = engine.evaluate(Map.of());
    assertTrue(results.isEmpty());
  }

  @Test
  void triggeredRuleContainsInterpretation() {
    Map<String, CombinationRuleEngine.IndicatorField> map = new HashMap<>();
    map.put("AST", field("AST", "high", 120.0));
    map.put("ALT", field("ALT", "high", 50.0));
    map.put("GGT", field("GGT", "high", 80.0));

    List<TriggeredRule> results = engine.evaluate(map);
    TriggeredRule liver = results.stream()
        .filter(r -> "LIVER_ALCOHOLIC".equals(r.ruleId()))
        .findFirst()
        .orElseThrow();
    assertFalse(liver.summary().isEmpty());
    assertFalse(liver.suggestion().isEmpty());
    assertEquals("warning", liver.severity());
    assertEquals(List.of("AST", "ALT", "GGT"), liver.involvedIndicators());
  }

  // --- analyze(JsonNode) 集成测试（原 CombinationAnalyzerTest） ---

  @Test
  void analyzeLiverPatternFromEnrichedPayload() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {"name": "谷草转氨酶", "value": "120", "standardCode": "AST", "resultState": "high", "numericValue": 120.0},
            {"name": "谷丙转氨酶", "value": "50", "standardCode": "ALT", "resultState": "high", "numericValue": 50.0},
            {"name": "谷氨酰转肽酶", "value": "80", "standardCode": "GGT", "resultState": "high", "numericValue": 80.0}
          ]
        }
        """);

    List<TriggeredRule> results = engine.analyze(payload);
    assertTrue(results.stream().anyMatch(r -> "LIVER_ALCOHOLIC".equals(r.ruleId())));
  }

  @Test
  void analyzeReturnsEmptyForNormalIndicators() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {"name": "谷草转氨酶", "value": "25", "standardCode": "AST", "resultState": "normal", "numericValue": 25.0},
            {"name": "谷丙转氨酶", "value": "20", "standardCode": "ALT", "resultState": "normal", "numericValue": 20.0},
            {"name": "谷氨酰转肽酶", "value": "15", "standardCode": "GGT", "resultState": "normal", "numericValue": 15.0}
          ]
        }
        """);

    List<TriggeredRule> results = engine.analyze(payload);
    assertFalse(results.stream().anyMatch(r -> "LIVER_ALCOHOLIC".equals(r.ruleId())));
  }

  @Test
  void analyzeIgnoresFieldsWithoutStandardCode() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {"name": "某未知指标", "value": "120", "resultState": "high", "numericValue": 120.0}
          ]
        }
        """);

    List<TriggeredRule> results = engine.analyze(payload);
    assertTrue(results.isEmpty());
  }

  @Test
  void analyzeReturnsEmptyForNullPayload() {
    List<TriggeredRule> results = engine.analyze(null);
    assertTrue(results.isEmpty());
  }

  @Test
  void analyzeThyroidHypoPattern() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {"name": "促甲状腺激素", "value": "15", "standardCode": "TSH", "resultState": "high", "numericValue": 15.0},
            {"name": "游离甲状腺素", "value": "5", "standardCode": "FT4", "resultState": "low", "numericValue": 5.0}
          ]
        }
        """);

    List<TriggeredRule> results = engine.analyze(payload);
    assertTrue(results.stream().anyMatch(r -> "THYROID_HYPO".equals(r.ruleId())));
  }

  @Test
  void analyzeMultiplePatternsCanTriggerSimultaneously() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {"name": "肌酐", "value": "150", "standardCode": "CREA", "resultState": "high", "numericValue": 150.0},
            {"name": "尿素氮", "value": "10", "standardCode": "BUN", "resultState": "high", "numericValue": 10.0},
            {"name": "糖化血红蛋白", "value": "8.5", "standardCode": "HBA1C", "resultState": "high", "numericValue": 8.5}
          ]
        }
        """);

    List<TriggeredRule> results = engine.analyze(payload);
    assertTrue(results.stream().anyMatch(r -> "RENAL_DECLINE".equals(r.ruleId())));
    assertTrue(results.stream().anyMatch(r -> "DM_UNCONTROLLED".equals(r.ruleId())));
    assertEquals(2, results.size());
  }

  private static CombinationRuleEngine.IndicatorField field(String code, String state, double value) {
    return new CombinationRuleEngine.IndicatorField(code, state, value);
  }
}
