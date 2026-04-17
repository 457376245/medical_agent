package com.medical.agent.application.service;

import java.util.List;

record CombinationRule(
    String id,
    String name,
    String category,
    String severity,
    String description,
    List<String> requiredIndicators,
    List<RuleCondition> conditions,
    RuleInterpretation interpretation) {

  record RuleInterpretation(String summary, String detail, String suggestion) {}
}
