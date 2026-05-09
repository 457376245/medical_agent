package com.medical.agent.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.domain.vo.UltrasoundFollowUpResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class UltrasoundFollowUpAnalyzer {
  private static final int MAX_EVIDENCE = 3;
  private static final int MAX_HISTORY = 4;

  public UltrasoundFollowUpResult analyze(String currentRecordId, List<ReportSnapshot> snapshots) {
    ReportSnapshot current = snapshots.stream()
        .filter(item -> item.recordId().equals(currentRecordId))
        .findFirst()
        .orElse(null);
    if (current == null || !isUltrasound(current)) {
      return null;
    }

    List<ReportSnapshot> ultrasoundReports = snapshots.stream()
        .filter(this::isUltrasound)
        .sorted(Comparator.comparing(ReportSnapshot::recordDate).reversed())
        .toList();

    int currentIndex = -1;
    for (int i = 0; i < ultrasoundReports.size(); i++) {
      if (currentRecordId.equals(ultrasoundReports.get(i).recordId())) {
        currentIndex = i;
        break;
      }
    }
    if (currentIndex < 0) {
      return null;
    }

    ReportSnapshot previous = currentIndex + 1 < ultrasoundReports.size()
        ? ultrasoundReports.get(currentIndex + 1)
        : null;
    List<UltrasoundFollowUpResult.EvidenceItem> currentEvidence = evidenceItems(current);
    List<UltrasoundFollowUpResult.EvidenceItem> previousEvidence = previous == null
        ? List.of()
        : evidenceItems(previous);
    Action action = actionFor(current);
    Change change = previous == null ? Change.noHistory() : compare(current, previous);
    String mode = previous == null ? "SINGLE_REPORT" : "FOLLOW_UP";
    String summary = previous == null
        ? "当前仅发现 1 份超声/彩超报告，先提供本次报告解读，暂不能判断历史变化。"
        : change.summary();

    return new UltrasoundFollowUpResult(
        mode,
        change.status(),
        summary,
        action.level(),
        action.suggestion(),
        currentEvidence,
        previousEvidence,
        historyItems(ultrasoundReports));
  }

  private boolean isUltrasound(ReportSnapshot snapshot) {
    String text = compact(snapshot.title(), snapshot.sourceType(), reportText(snapshot.payload()));
    return containsAny(text, "超声", "彩超", "b超", "b 超", "多普勒", "cdfi", "声像图");
  }

  private Change compare(ReportSnapshot current, ReportSnapshot previous) {
    String currentText = reportText(current.payload());
    String previousText = reportText(previous.payload());
    if (containsAny(currentText, "新发", "新增", "新见")) {
      return new Change("NEW", "本次报告较上次提示有新增描述，建议结合原文依据尽快让医生判断其意义。");
    }
    if (containsAny(currentText, "增大", "增多", "加重", "进展", "较前增大")
        || riskRank(currentText) > riskRank(previousText)) {
      return new Change("WORSENED", "本次报告较上次存在加重或风险提示增强的描述，建议尽快复查或就医确认。");
    }
    if (containsAny(currentText, "缩小", "减少", "减轻", "改善", "好转")
        || riskRank(currentText) < riskRank(previousText)) {
      return new Change("IMPROVED", "本次报告较上次有改善或风险提示减弱的描述，但仍需结合医生意见继续随访。");
    }
    if (normalizeText(currentText).equals(normalizeText(previousText))) {
      return new Change("STABLE", "本次报告与上次主要文字描述基本一致，报告级别暂未见明确进展。");
    }
    return new Change("UNKNOWN", "本次与上次报告存在文字差异，但仅凭报告级文本无法可靠判断变好或变差。");
  }

  private Action actionFor(ReportSnapshot current) {
    String text = reportText(current.payload());
    if (containsAny(text, "急诊", "立即", "破裂", "大量积液", "血栓")) {
      return new Action("IMMEDIATE_CARE", "报告存在高优先级提示，请立即就医或按报告建议处理。");
    }
    if (containsAny(text, "高度怀疑", "恶性", "bi-rads 4", "bi-rads 5", "ti-rads 4", "ti-rads 5",
        "建议穿刺", "进一步检查", "肿块", "占位")) {
      return new Action("SEEK_CARE_SOON", "报告存在需要医生进一步判断的提示，建议尽快就医复诊。");
    }
    if (containsAny(text, "结节", "囊肿", "低回声", "钙化", "增大", "新发", "复查", "随访")) {
      return new Action("RECHECK_SOON", "报告提示需要随访关注的内容，建议按医生要求尽快复查或预约门诊。");
    }
    return new Action("OBSERVE", "当前报告未识别到明确高优先级提示，可先观察并按常规医嘱随访。");
  }

  private List<UltrasoundFollowUpResult.EvidenceItem> evidenceItems(ReportSnapshot snapshot) {
    List<UltrasoundFollowUpResult.EvidenceItem> items = new ArrayList<>();
    JsonNode fields = snapshot.payload() == null ? null : snapshot.payload().path("fields");
    if (fields != null && fields.isArray()) {
      for (JsonNode field : fields) {
        String name = readText(field, "name");
        String value = readText(field, "value");
        String evidence = readText(field.path("evidence"), "snippet");
        String text = TextUtils.trimToNull(evidence) != null ? evidence : value;
        if (TextUtils.trimToNull(name) == null || TextUtils.trimToNull(text) == null) {
          continue;
        }
        items.add(new UltrasoundFollowUpResult.EvidenceItem(
            snapshot.recordId(),
            snapshot.recordDate(),
            name,
            trimText(text, 120)));
        if (items.size() >= MAX_EVIDENCE) {
          break;
        }
      }
    }
    if (items.isEmpty()) {
      String fallback = trimText(reportText(snapshot.payload()), 120);
      if (TextUtils.trimToNull(fallback) != null) {
        items.add(new UltrasoundFollowUpResult.EvidenceItem(
            snapshot.recordId(),
            snapshot.recordDate(),
            "报告文本",
            fallback));
      }
    }
    return List.copyOf(items);
  }

  private List<UltrasoundFollowUpResult.HistoryItem> historyItems(List<ReportSnapshot> reports) {
    List<UltrasoundFollowUpResult.HistoryItem> items = new ArrayList<>();
    for (ReportSnapshot report : reports.subList(0, Math.min(reports.size(), MAX_HISTORY))) {
      items.add(new UltrasoundFollowUpResult.HistoryItem(
          report.recordId(),
          report.recordDate(),
          report.title(),
          trimText(reportText(report.payload()), 80)));
    }
    return List.copyOf(items);
  }

  private String reportText(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    JsonNode fields = payload.path("fields");
    if (fields.isArray()) {
      for (JsonNode field : fields) {
        String name = readText(field, "name");
        String value = readText(field, "value");
        String snippet = readText(field.path("evidence"), "snippet");
        String segment = compact(name, value, snippet);
        if (!segment.isBlank()) {
          parts.add(segment);
        }
      }
    }
    String raw = readText(payload, "raw");
    if (!raw.isBlank()) {
      parts.add(raw);
    }
    return String.join("；", parts);
  }

  private int riskRank(String text) {
    if (containsAny(text, "bi-rads 5", "ti-rads 5", "恶性", "高度怀疑")) {
      return 4;
    }
    if (containsAny(text, "bi-rads 4", "ti-rads 4", "建议穿刺", "占位", "肿块")) {
      return 3;
    }
    if (containsAny(text, "结节", "钙化", "低回声", "血流")) {
      return 2;
    }
    if (containsAny(text, "囊肿", "未见明显异常")) {
      return 1;
    }
    return 0;
  }

  private boolean containsAny(String text, String... needles) {
    String normalized = normalizeText(text);
    for (String needle : needles) {
      if (normalized.contains(normalizeText(needle))) {
        return true;
      }
    }
    return false;
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
  }

  private String compact(String... values) {
    List<String> parts = new ArrayList<>();
    for (String value : values) {
      String normalized = TextUtils.trimToNull(value);
      if (normalized != null) {
        parts.add(normalized);
      }
    }
    return String.join(" ", parts);
  }

  private String readText(JsonNode node, String fieldName) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    JsonNode value = node.path(fieldName);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
  }

  private String trimText(String value, int maxLength) {
    String text = TextUtils.trimToNull(value);
    if (text == null) {
      return "";
    }
    return text.length() <= maxLength ? text : text.substring(0, maxLength);
  }

  public record ReportSnapshot(
      String recordId,
      String title,
      String recordDate,
      String sourceType,
      JsonNode payload) {}

  private record Change(String status, String summary) {
    static Change noHistory() {
      return new Change("NO_HISTORY", "暂无历史超声/彩超报告，不能判断变化。");
    }
  }

  private record Action(String level, String suggestion) {}
}
