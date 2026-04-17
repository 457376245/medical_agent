package com.medical.agent.application.service;

import java.util.List;

record TriggeredRule(
    String ruleId,
    String name,
    String severity,
    String summary,
    String detail,
    String suggestion,
    List<String> involvedIndicators) {}
