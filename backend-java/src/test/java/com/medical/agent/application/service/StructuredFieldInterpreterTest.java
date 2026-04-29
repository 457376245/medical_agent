package com.medical.agent.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.domain.vo.TrendField;
import org.junit.jupiter.api.Test;

class StructuredFieldInterpreterTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final IndicatorCatalog catalog = new IndicatorCatalog(objectMapper);
  private final IndicatorNormalizer normalizer = new IndicatorNormalizer(catalog);
  private final StructuredFieldInterpreter interpreter = new StructuredFieldInterpreter(objectMapper, normalizer);

  @Test
  void enrichPayloadMarksThresholdFieldsAsThreshold() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "HBV-DNA",
              "value": ">1.00×10^8 IU/ml",
              "referenceRange": "最低检测量 50IU/mL"
            }
          ]
        }
        """);

    JsonNode field = interpreter.enrichPayload(payload).path("fields").get(0);

    assertEquals("threshold", field.path("comparisonType").asText());
    assertEquals("threshold", field.path("resultState").asText());
    assertEquals(50.0d, field.path("referenceLowerBound").asDouble(), 0.000001d);
    assertEquals(100000000.0d, field.path("numericValue").asDouble(), 0.000001d);
  }

  @Test
  void enrichPayloadMarksRangeFieldsHighAndNormal() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "空腹血糖",
              "value": "6.3",
              "referenceRange": "3.9-6.1"
            },
            {
              "name": "餐后血糖",
              "value": "5.0",
              "referenceRange": "3.9-6.1"
            }
          ]
        }
        """);

    JsonNode enriched = interpreter.enrichPayload(payload);

    assertEquals("range", enriched.path("fields").get(0).path("comparisonType").asText());
    assertEquals("high", enriched.path("fields").get(0).path("resultState").asText());
    assertEquals("normal", enriched.path("fields").get(1).path("resultState").asText());
  }

  @Test
  void enrichPayloadDoesNotBlockUnmappedIndicator() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "某未知指标",
              "value": "120",
              "referenceRange": "0-100"
            }
          ]
        }
        """);

    JsonNode field = interpreter.enrichPayload(payload).path("fields").get(0);

    assertEquals("range", field.path("comparisonType").asText());
    assertEquals("high", field.path("resultState").asText());
    assertFalse(field.has("standardCode"));
  }

  @Test
  void enrichPayloadMarksLabeledMultiSegmentsAsHighOrNormal() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "非高密度脂蛋白胆固醇",
              "value": "4.17",
              "referenceRange": "适宜<4.10 mmol/L;增高4.10-4.90;很高>4.90"
            },
            {
              "name": "甘油三酯",
              "value": "2.15",
              "referenceRange": "适宜<1.70 mmol/L;增高1.70-2.30;很高>2.30"
            },
            {
              "name": "低密度脂蛋白胆固醇",
              "value": "3.69",
              "referenceRange": "适宜<3.40 mmol/L;增高3.40-4.10;很高>4.10"
            },
            {
              "name": "高密度脂蛋白胆固醇",
              "value": "2.18",
              "referenceRange": ">1.04 mmol/L"
            }
          ]
        }
        """);

    JsonNode enriched = interpreter.enrichPayload(payload);

    assertEquals("upper_bound", enriched.path("fields").get(0).path("comparisonType").asText());
    assertEquals("high", enriched.path("fields").get(0).path("resultState").asText());
    assertEquals(4.10d, enriched.path("fields").get(0).path("referenceUpperBound").asDouble(), 0.000001d);

    assertEquals("high", enriched.path("fields").get(1).path("resultState").asText());
    assertEquals("high", enriched.path("fields").get(2).path("resultState").asText());
    assertEquals("lower_bound", enriched.path("fields").get(3).path("comparisonType").asText());
    assertEquals("normal", enriched.path("fields").get(3).path("resultState").asText());
  }

  @Test
  void enrichPayloadHandlesDoubleHyphenRange() throws Exception {
    JsonNode payload = objectMapper.readTree("""
        {
          "fields": [
            {
              "name": "前白蛋白",
              "value": "269",
              "referenceRange": "180--350mg/L"
            },
            {
              "name": "丙氨酸氨基转移酶",
              "value": "17",
              "referenceRange": "7--40IU/L"
            }
          ]
        }
        """);

    JsonNode enriched = interpreter.enrichPayload(payload);

    assertEquals("range", enriched.path("fields").get(0).path("comparisonType").asText());
    assertEquals("normal", enriched.path("fields").get(0).path("resultState").asText());
    assertEquals(180.0d, enriched.path("fields").get(0).path("referenceLowerBound").asDouble(), 0.000001d);
    assertEquals(350.0d, enriched.path("fields").get(0).path("referenceUpperBound").asDouble(), 0.000001d);

    assertEquals("range", enriched.path("fields").get(1).path("comparisonType").asText());
    assertEquals("normal", enriched.path("fields").get(1).path("resultState").asText());
    assertEquals(7.0d, enriched.path("fields").get(1).path("referenceLowerBound").asDouble(), 0.000001d);
    assertEquals(40.0d, enriched.path("fields").get(1).path("referenceUpperBound").asDouble(), 0.000001d);
  }

  @Test
  void toTrendFieldEnhancesLegacyFieldWithoutStoredMetadata() throws Exception {
    JsonNode legacyField = objectMapper.readTree("""
        {
          "name": "空腹血糖",
          "value": "6.3",
          "unit": "mmol/L",
          "referenceRange": "3.9-6.1"
        }
        """);

    TrendField trendField = interpreter.toTrendField(legacyField);

    assertNotNull(trendField);
    assertEquals("high", trendField.resultState());
    assertEquals("range", trendField.comparisonType());
    assertEquals(3.9d, trendField.referenceLowerBound(), 0.000001d);
    assertEquals(6.1d, trendField.referenceUpperBound(), 0.000001d);
  }
}
