package com.medical.agent.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IndicatorNormalizerTest {

  private static IndicatorNormalizer normalizer;

  @BeforeAll
  static void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    IndicatorCatalog catalog = new IndicatorCatalog(objectMapper);
    normalizer = new IndicatorNormalizer(catalog);
  }

  @Test
  void 精确匹配英文缩写() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("ALT");
    assertNotNull(result);
    assertEquals("ALT", result.code());
    assertEquals("肝功能", result.category());
  }

  @Test
  void 精确匹配中文全称() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("谷丙转氨酶");
    assertNotNull(result);
    assertEquals("ALT", result.code());
  }

  @Test
  void 括号拆分匹配_中文括号包英文() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("γ-谷氨酰转肽酶（GGT）");
    assertNotNull(result);
    assertEquals("GGT", result.code());
  }

  @Test
  void 括号拆分匹配_英文括号包中文() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("AST(谷草转氨酶)");
    assertNotNull(result);
    assertEquals("AST", result.code());
  }

  @Test
  void 包含匹配_短缩写() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("谷丙");
    assertNotNull(result);
    assertEquals("ALT", result.code());
  }

  @Test
  void 英文token提取_混合文本() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("丙氨酸氨基转移酶ALT测定");
    assertNotNull(result);
    assertEquals("ALT", result.code());
  }

  @Test
  void 无法识别返回null() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("完全不存在的指标XYZ");
    assertNull(result);
  }

  @Test
  void 空输入返回null() {
    assertNull(normalizer.normalize(null));
    assertNull(normalizer.normalize(""));
    assertNull(normalizer.normalize("   "));
  }

  @Test
  void 乙肝病毒DNA识别() {
    IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize("HBV-DNA");
    assertNotNull(result);
    assertEquals("HBV_DNA", result.code());
    assertEquals("乙肝/传染病", result.category());
  }

  @Test
  void 糖化血红蛋白多种写法() {
    assertNotNull(normalizer.normalize("HbA1c"));
    assertEquals("HBA1C", normalizer.normalize("HbA1c").code());
    assertEquals("HBA1C", normalizer.normalize("糖化血红蛋白").code());
    assertEquals("HBA1C", normalizer.normalize("糖化").code());
  }

  @Test
  void 重复别名优先保留主指标且短英文不误伤() {
    assertEquals("GLU", normalizer.normalize("GLU").code());
    assertEquals("WBC", normalizer.normalize("WBC").code());
    assertEquals("TG", normalizer.normalize("TG").code());
    assertEquals("UA", normalizer.normalize("Uric Acid").code());
    assertEquals("CYSC", normalizer.normalize("Cystatin C").code());
    assertEquals("OGTT_0H", normalizer.normalize("OGTT 0h").code());
  }
}
