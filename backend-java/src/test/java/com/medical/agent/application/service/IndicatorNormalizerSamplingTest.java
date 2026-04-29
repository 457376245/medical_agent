package com.medical.agent.application.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorNormalizerSamplingTest {
  private static final double REQUIRED_HIT_RATE = 0.95d;
  private static final int REQUIRED_SAMPLE_SIZE = 300;

  @Test
  void generatedCommonTermSamplesReachRequiredHitRate() throws Exception {
    IndicatorNormalizer normalizer = new IndicatorNormalizer(new IndicatorCatalog(new ObjectMapper()));
    List<Sample> samples = loadSamples();
    List<String> failures = new ArrayList<>();

    for (Sample sample : samples) {
      IndicatorNormalizer.NormalizedIndicator result = normalizer.normalize(sample.rawName());
      String actualCode = result == null ? null : result.code();
      if (!sample.expectedCode().equals(actualCode)) {
        failures.add(sample.rawName() + " expected=" + sample.expectedCode() + " actual=" + actualCode);
      }
    }

    double hitRate = (samples.size() - failures.size()) / (double) samples.size();
    assertTrue(samples.size() >= REQUIRED_SAMPLE_SIZE, "抽样样本数必须不少于 " + REQUIRED_SAMPLE_SIZE + "，当前=" + samples.size());
    assertTrue(
        hitRate >= REQUIRED_HIT_RATE,
        "指标归一化命中率未达标，hitRate=" + hitRate + ", failures=" + failures);
  }

  private static List<Sample> loadSamples() throws Exception {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        IndicatorNormalizerSamplingTest.class.getResourceAsStream("/indicator-normalization-samples.csv"),
        StandardCharsets.UTF_8))) {
      return reader.lines()
          .skip(1)
          .filter(line -> !line.isBlank())
          .map(line -> line.split(",", -1))
          .map(parts -> new Sample(parts[0], parts[1]))
          .toList();
    }
  }

  private record Sample(String rawName, String expectedCode) {}
}
