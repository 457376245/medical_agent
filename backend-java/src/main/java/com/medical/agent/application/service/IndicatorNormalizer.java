package com.medical.agent.application.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IndicatorNormalizer {
  private static final Logger log = LoggerFactory.getLogger(IndicatorNormalizer.class);

  private static final Pattern PARENTHESES = Pattern.compile("[（(]([^)）]+)[)）]");
  private static final Pattern ENGLISH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,20}");

  private final IndicatorCatalog catalog;

  IndicatorNormalizer(IndicatorCatalog catalog) {
    this.catalog = catalog;
  }

  NormalizedIndicator normalize(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      return null;
    }

    String cleaned = preprocess(rawName);
    if (cleaned.isEmpty()) {
      return null;
    }

    // 第 1 层：精确匹配
    String code = catalog.findByExact(cleaned);
    if (code != null) {
      return toResult(code);
    }

    // 第 2 层：括号拆分，分别精确匹配
    code = matchByParenthesisSplit(rawName);
    if (code != null) {
      return toResult(code);
    }

    // 第 3 层：包含匹配
    code = catalog.findByContainment(cleaned);
    if (code != null) {
      return toResult(code);
    }

    // 第 4 层：提取英文 token 精确匹配
    code = matchByEnglishToken(rawName);
    if (code != null) {
      return toResult(code);
    }

    log.debug("指标名未映射: {}", rawName);
    return null;
  }

  boolean isValidCode(String code) {
    return catalog.isValidCode(code);
  }

  private String matchByParenthesisSplit(String rawName) {
    Matcher matcher = PARENTHESES.matcher(rawName);
    while (matcher.find()) {
      String inside = preprocess(matcher.group(1));
      String code = catalog.findByExact(inside);
      if (code != null) {
        return code;
      }
    }
    // 括号外的部分
    String outside = preprocess(PARENTHESES.matcher(rawName).replaceAll(""));
    if (!outside.isEmpty()) {
      String code = catalog.findByExact(outside);
      if (code != null) {
        return code;
      }
    }
    return null;
  }

  private String matchByEnglishToken(String rawName) {
    Matcher matcher = ENGLISH_TOKEN.matcher(rawName);
    while (matcher.find()) {
      String token = matcher.group().toLowerCase(Locale.ROOT);
      String code = catalog.findByExact(token);
      if (code != null) {
        return code;
      }
    }
    return null;
  }

  private NormalizedIndicator toResult(String code) {
    IndicatorCatalog.IndicatorMeta meta = catalog.getMeta(code);
    if (meta == null) {
      return new NormalizedIndicator(code, null, null);
    }
    return new NormalizedIndicator(code, meta.category(), meta.displayName());
  }

  private static String preprocess(String rawName) {
    return rawName
        .replaceAll("[\\s　]+", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  record NormalizedIndicator(String code, String category, String displayName) {}
}
