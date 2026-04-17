package com.medical.agent.application.service;

import java.util.List;

record RuleCondition(
    String type,
    String indicator,
    String resultState,
    String operator,
    Double threshold,
    String numerator,
    String denominator,
    List<String> anyOf) {

  boolean isState() {
    return "state".equals(type);
  }

  boolean isValue() {
    return "value".equals(type);
  }

  boolean isRatio() {
    return "ratio".equals(type);
  }
}
