package com.medical.agent.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;


final class IndicatorCatalog {

  private final Map<String, String> exactIndex;
  private final List<AliasEntry> substringEntries;
  private final Map<String, IndicatorMeta> metaMap;

  IndicatorCatalog(ObjectMapper objectMapper) {
    Map<String, String> exact = new HashMap<>();
    List<AliasEntry> substring = new ArrayList<>();
    Map<String, IndicatorMeta> meta = new HashMap<>();

    try (InputStream in = getClass().getResourceAsStream("/indicator_catalog.json")) {
      if (in != null) {
        JsonNode root = objectMapper.readTree(in);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> entry = fields.next();
          String code = entry.getKey();
          JsonNode node = entry.getValue();

          String displayName = textOrEmpty(node, "displayName");
          String shortName = textOrEmpty(node, "shortName");
          String category = textOrEmpty(node, "category");
          String commonUnit = textOrEmpty(node, "commonUnit");
          meta.put(code, new IndicatorMeta(code, displayName, shortName, category, commonUnit));

          exact.putIfAbsent(normalizeKey(code), code);

          JsonNode aliases = node.path("aliases");
          if (aliases.isArray()) {
            for (JsonNode alias : aliases) {
              String aliasText = alias.asText("").trim();
              if (!aliasText.isEmpty()) {
                String key = normalizeKey(aliasText);
                exact.putIfAbsent(key, code);
                substring.add(new AliasEntry(key, code));
              }
            }
          }
        }
      }
    } catch (IOException ignored) {
      // 编码库加载失败时静默降级，所有指标将标记为 UNMAPPED
    }

    this.exactIndex = Map.copyOf(exact);
    this.substringEntries = List.copyOf(substring);
    this.metaMap = Map.copyOf(meta);
  }

  String findByExact(String normalizedName) {
    return exactIndex.get(normalizedName);
  }

  String findByContainment(String normalizedName) {
    // 优先：alias 完全包含在输入中（输入较长，alias 较短）
    String bestMatch = null;
    int bestLength = 0;
    for (AliasEntry entry : substringEntries) {
      if (isUnsafeShortAsciiAlias(entry.alias())) {
        continue;
      }
      if (normalizedName.contains(entry.alias()) && entry.alias().length() > bestLength) {
        bestMatch = entry.code();
        bestLength = entry.alias().length();
      }
    }
    if (bestMatch != null) {
      return bestMatch;
    }
    // 其次：输入完全包含在某个 alias 中（输入较短，alias 较长）
    for (AliasEntry entry : substringEntries) {
      if (isUnsafeShortAsciiAlias(normalizedName)) {
        continue;
      }
      if (entry.alias().contains(normalizedName) && normalizedName.length() >= 2) {
        return entry.code();
      }
    }
    return null;
  }

  IndicatorMeta getMeta(String code) {
    return metaMap.get(code);
  }

  boolean isValidCode(String code) {
    return metaMap.containsKey(code);
  }

  private static String textOrEmpty(JsonNode node, String key) {
    JsonNode value = node.path(key);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("");
  }

  private static String normalizeKey(String raw) {
    return raw
        .replaceAll("[\\s　]+", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private static boolean isUnsafeShortAsciiAlias(String value) {
    return value.length() < 3 && value.matches("[a-z0-9+-]+");
  }

  record IndicatorMeta(String code, String displayName, String shortName, String category, String commonUnit) {}

  private record AliasEntry(String alias, String code) {}
}
