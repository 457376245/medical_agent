package com.medical.agent.domain.vo;

public record ReportAnalysisResult(String recordId, String content, boolean cached, int version) {}
