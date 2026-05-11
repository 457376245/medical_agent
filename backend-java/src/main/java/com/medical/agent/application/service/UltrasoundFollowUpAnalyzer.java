package com.medical.agent.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medical.agent.domain.util.TextUtils;
import com.medical.agent.domain.vo.UltrasoundFollowUpResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UltrasoundFollowUpAnalyzer {
  private static final int MAX_EVIDENCE = 3;
  private static final int MAX_HISTORY = 4;
  private static final Pattern MM_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:mm|毫米)", Pattern.CASE_INSENSITIVE);

  private static final List<FindingSpec> LIVER_SPECS = List.of(
      new FindingSpec("肝硬化影像表现", List.of("肝硬化", "肝脏形态", "肝形态", "包膜", "形态欠规则", "形态不规则"), true),
      new FindingSpec("肝实质回声", List.of("肝实质", "肝区回声", "回声增粗", "粗光点", "粗回声", "欠均匀"), true),
      new FindingSpec("门静脉主干", List.of("门静脉", "门脉"), true),
      new FindingSpec("胆管", List.of("胆管", "肝内胆管", "肝外胆管"), true),
      new FindingSpec("明确占位/结节", List.of("占位", "肿块", "结节", "低回声灶", "高回声灶"), true),
      new FindingSpec("腹水", List.of("腹水", "积液", "液性暗区"), true),
      new FindingSpec("侧支循环/脐静脉", List.of("脐静脉", "侧支循环", "侧枝循环", "再通", "开放"), true),
      new FindingSpec("脾脏", List.of("脾脏", "脾厚", "脾长", "脾大"), true),
      new FindingSpec("胰腺显示质量", List.of("胰腺", "胰头", "胰体", "胰尾"), false));

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
    List<UltrasoundFollowUpResult.FindingRow> findingRows = findingRows(current, previous);
    List<UltrasoundFollowUpResult.MissingInput> missingInputs = missingInputs(current, findingRows);
    List<UltrasoundFollowUpResult.RiskModule> riskModules = riskModules(current, findingRows, missingInputs);
    Action action = actionFor(current, findingRows);
    Change change = previous == null ? Change.noHistory() : compare(current, previous, findingRows);
    String mode = previous == null ? "SINGLE_REPORT" : "FOLLOW_UP";
    String patientSummary = patientSummary(current, previous, findingRows, missingInputs, change);
    String clinicalSummary = clinicalSummary(current, previous, findingRows);
    String summary = patientSummary.isBlank()
        ? (previous == null
            ? "当前仅发现 1 份超声/彩超报告，先提供本次报告解读，暂不能判断历史变化。"
            : change.summary())
        : patientSummary;

    return new UltrasoundFollowUpResult(
        mode,
        change.status(),
        summary,
        action.level(),
        action.suggestion(),
        currentEvidence,
        previousEvidence,
        historyItems(ultrasoundReports),
        patientSummary,
        clinicalSummary,
        confidenceLevel(findingRows, missingInputs),
        findingRows,
        riskModules,
        missingInputs,
        nextQuestionsForDoctor(current, findingRows, missingInputs));
  }

  private boolean isUltrasound(ReportSnapshot snapshot) {
    String text = compact(snapshot.title(), snapshot.sourceType(), reportText(snapshot.payload()));
    return containsAny(text, "超声", "彩超", "b超", "b 超", "多普勒", "cdfi", "声像图");
  }

  private List<UltrasoundFollowUpResult.FindingRow> findingRows(ReportSnapshot current, ReportSnapshot previous) {
    if (!isLiverUltrasound(current, previous)) {
      return genericFindingRows(current, previous);
    }
    List<UltrasoundFollowUpResult.FindingRow> rows = new ArrayList<>();
    for (FindingSpec spec : LIVER_SPECS) {
      Finding currentFinding = findingFor(current, spec);
      Finding previousFinding = previous == null ? Finding.notAvailable() : findingFor(previous, spec);
      if (currentFinding.status().equals("NOT_MENTIONED") && previousFinding.status().equals("NOT_MENTIONED")) {
        continue;
      }
      String trend = trendFor(spec, currentFinding, previousFinding, previous == null);
      rows.add(new UltrasoundFollowUpResult.FindingRow(
          spec.module(),
          currentFinding.value(),
          previousFinding.value(),
          currentFinding.status(),
          previousFinding.status(),
          trend,
          evidenceLevelFor(currentFinding, previousFinding),
          explanationFor(spec, currentFinding, previousFinding, trend),
          evidenceRefs(currentFinding, previousFinding)));
    }
    return List.copyOf(rows);
  }

  private List<UltrasoundFollowUpResult.FindingRow> genericFindingRows(ReportSnapshot current, ReportSnapshot previous) {
    FindingSpec spec = new FindingSpec("报告结论", List.of("提示", "结论", "诊断", "所见"), false);
    Finding currentFinding = findingFor(current, spec);
    Finding previousFinding = previous == null ? Finding.notAvailable() : findingFor(previous, spec);
    return List.of(new UltrasoundFollowUpResult.FindingRow(
        spec.module(),
        currentFinding.value(),
        previousFinding.value(),
        currentFinding.status(),
        previousFinding.status(),
        previous == null ? "INSUFFICIENT_INFO" : "CANNOT_JUDGE",
        "TEXT_ONLY",
        previous == null ? "暂无历史同类报告，不能判断变化。" : "通用超声报告仅做文本级提示，建议结合专科医生判断。",
        evidenceRefs(currentFinding, previousFinding)));
  }

  private boolean isLiverUltrasound(ReportSnapshot current, ReportSnapshot previous) {
    String text = compact(current.title(), reportText(current.payload()),
        previous == null ? "" : previous.title(), previous == null ? "" : reportText(previous.payload()));
    return containsAny(text, "肝", "门静脉", "胆管", "胆囊", "脾", "腹水", "脐静脉", "肝硬化");
  }

  private Finding findingFor(ReportSnapshot snapshot, FindingSpec spec) {
    if (snapshot == null) {
      return Finding.notAvailable();
    }
    List<String> segments = reportSegments(snapshot.payload());
    List<String> matched = segments.stream()
        .filter(segment -> containsAny(segment, spec.keywords().toArray(String[]::new)))
        .toList();
    if (matched.isEmpty()) {
      return new Finding("未提及", "NOT_MENTIONED", List.of());
    }

    String joined = String.join("；", matched);
    List<UltrasoundFollowUpResult.EvidenceItem> evidence = matched.stream()
        .limit(2)
        .map(text -> new UltrasoundFollowUpResult.EvidenceItem(
            snapshot.recordId(), snapshot.recordDate(), spec.module(), trimText(text, 120)))
        .toList();
    String value = valueFor(spec, joined);
    String status = statusFor(spec, joined);
    return new Finding(TextUtils.trimToNull(value) == null ? trimText(joined, 80) : value, status, evidence);
  }

  private String valueFor(FindingSpec spec, String text) {
    if ("门静脉主干".equals(spec.module())) {
      Double value = firstNumberMm(text);
      return value == null ? trimText(text, 80) : formatNumber(value) + "mm";
    }
    if ("侧支循环/脐静脉".equals(spec.module())) {
      Double value = firstNumberMm(text);
      if (value != null) {
        return "脐静脉/侧支线索，约 " + formatNumber(value) + "mm";
      }
    }
    return trimText(text, 80);
  }

  private String statusFor(FindingSpec spec, String text) {
    if (containsAny(text, "显示不清", "显示欠清", "显示不满意", "受限", "未显示清楚")) {
      return "UNCLEAR";
    }
    if ("明确占位/结节".equals(spec.module()) && containsAny(text, "可疑", "占位", "肿块", "结节")) {
      return containsNegative(text) ? "ABSENT" : "SUSPICIOUS";
    }
    if (containsNegative(text)) {
      return "ABSENT";
    }
    if (containsAny(text, "扩张", "增宽", "再通", "开放", "腹水", "肝硬化", "欠规则", "增粗", "粗糙", "欠均匀")) {
      return "PRESENT";
    }
    return spec.defaultPositive() ? "PRESENT" : "NOT_MENTIONED";
  }

  private String trendFor(FindingSpec spec, Finding current, Finding previous, boolean noPrevious) {
    if (noPrevious) {
      return "INSUFFICIENT_INFO";
    }
    if ("UNCLEAR".equals(current.status())) {
      return "LIMITED_QUALITY";
    }
    if ("侧支循环/脐静脉".equals(spec.module())
        && "NOT_MENTIONED".equals(current.status())
        && ("PRESENT".equals(previous.status()) || "SUSPICIOUS".equals(previous.status()))) {
      return "INSUFFICIENT_INFO";
    }
    if ("门静脉主干".equals(spec.module())) {
      Double currentMm = firstNumberMm(current.value());
      Double previousMm = firstNumberMm(previous.value());
      if (currentMm != null && previousMm != null) {
        double delta = currentMm - previousMm;
        if (delta >= 2.0) {
          return "POSSIBLE_WORSENED";
        }
        if (delta <= -2.0) {
          return "POSSIBLE_IMPROVED";
        }
        return "BASICALLY_STABLE";
      }
    }
    if ("NOT_MENTIONED".equals(current.status()) || "NOT_MENTIONED".equals(previous.status())) {
      return "INSUFFICIENT_INFO";
    }
    if (current.status().equals(previous.status())) {
      return "BASICALLY_STABLE";
    }
    if (("PRESENT".equals(current.status()) || "SUSPICIOUS".equals(current.status()))
        && "ABSENT".equals(previous.status())) {
      return "POSSIBLE_WORSENED";
    }
    if ("ABSENT".equals(current.status())
        && ("PRESENT".equals(previous.status()) || "SUSPICIOUS".equals(previous.status()))) {
      return "POSSIBLE_IMPROVED";
    }
    return "CANNOT_JUDGE";
  }

  private String evidenceLevelFor(Finding current, Finding previous) {
    if ("UNCLEAR".equals(current.status())) {
      return "LIMITED";
    }
    if ("NOT_MENTIONED".equals(current.status()) || "NOT_MENTIONED".equals(previous.status())) {
      return "LOW";
    }
    return "MEDIUM";
  }

  private String explanationFor(FindingSpec spec, Finding current, Finding previous, String trend) {
    if ("门静脉主干".equals(spec.module()) && "BASICALLY_STABLE".equals(trend)) {
      return "门静脉内径较上次无明显增宽，小幅差异可能来自测量和操作者差异。";
    }
    if ("侧支循环/脐静脉".equals(spec.module()) && "INSUFFICIENT_INFO".equals(trend)) {
      return "既往如提示脐静脉扩张，本次未提及不能理解为已经消失，建议让医生复核。";
    }
    if ("LIMITED_QUALITY".equals(trend)) {
      return "报告提示该部位显示不清，本次趋势可信度受限。";
    }
    if ("INSUFFICIENT_INFO".equals(trend)) {
      return "报告未完整描述该模块，不能把未提及当作正常或好转。";
    }
    if ("BASICALLY_STABLE".equals(trend)) {
      return "本次与上次描述方向基本一致，暂未见明确恶化证据。";
    }
    if ("POSSIBLE_WORSENED".equals(trend)) {
      return "本次出现或增强相关异常线索，建议结合医生意见确认。";
    }
    if ("POSSIBLE_IMPROVED".equals(trend)) {
      return "本次相关异常描述减少，但仍需结合报告完整性和医生复核。";
    }
    return "仅凭报告文本不能可靠判断该项变化。";
  }

  private List<UltrasoundFollowUpResult.EvidenceItem> evidenceRefs(Finding current, Finding previous) {
    List<UltrasoundFollowUpResult.EvidenceItem> refs = new ArrayList<>();
    refs.addAll(current.evidence());
    refs.addAll(previous.evidence());
    return refs.stream().limit(3).toList();
  }

  private Change compare(
      ReportSnapshot current,
      ReportSnapshot previous,
      List<UltrasoundFollowUpResult.FindingRow> findingRows) {
    if (findingRows.stream().anyMatch(row -> "POSSIBLE_WORSENED".equals(row.trendStatus()))) {
      return new Change("POSSIBLE_WORSENED", "本次存在局部可能进展线索，建议结合医生复核和相关检验判断。");
    }
    if (findingRows.stream().anyMatch(row -> "BASICALLY_STABLE".equals(row.trendStatus()))
        && findingRows.stream().noneMatch(row -> "POSSIBLE_WORSENED".equals(row.trendStatus()))) {
      return new Change("BASICALLY_STABLE", "本次局部可比项目暂未见明确恶化证据，但整体病情仍需结合检验和症状判断。");
    }

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

  private Action actionFor(ReportSnapshot current, List<UltrasoundFollowUpResult.FindingRow> findingRows) {
    String text = reportText(current.payload());
    if (containsAny(text, "急诊", "立即", "破裂", "大量积液", "血栓", "呕血", "黑便")) {
      return new Action("IMMEDIATE_CARE", "报告存在高优先级提示，请立即就医或按报告建议处理。");
    }
    if (findingRows.stream().anyMatch(row -> "明确占位/结节".equals(row.module()) && "SUSPICIOUS".equals(row.currentStatus()))
        || containsAny(text, "高度怀疑", "恶性", "bi-rads 4", "bi-rads 5", "ti-rads 4", "ti-rads 5",
            "建议穿刺", "进一步检查", "肿块", "占位")) {
      return new Action("SEEK_CARE_SOON", "报告存在需要医生进一步判断的提示，建议尽快就医复诊。");
    }
    if (findingRows.stream().anyMatch(row -> "腹水".equals(row.module()) && "PRESENT".equals(row.currentStatus()))
        || containsAny(text, "结节", "囊肿", "低回声", "钙化", "增大", "新发", "复查", "随访", "脐静脉")) {
      return new Action("RECHECK_SOON", "报告提示需要随访关注的内容，建议按医生要求尽快复查或预约门诊。");
    }
    return new Action("OBSERVE", "当前报告未识别到明确高优先级提示，可先观察并按常规医嘱随访。");
  }

  private List<UltrasoundFollowUpResult.MissingInput> missingInputs(
      ReportSnapshot current,
      List<UltrasoundFollowUpResult.FindingRow> findingRows) {
    if (!isLiverUltrasound(current, null)) {
      return List.of();
    }
    List<UltrasoundFollowUpResult.MissingInput> items = new ArrayList<>();
    addMissing(items, "血小板", "判断门静脉高压和脾功能亢进线索时需要结合。", "血常规");
    addMissing(items, "白蛋白", "判断肝合成功能和失代偿风险时需要结合。", "肝功能");
    addMissing(items, "总胆红素", "判断黄疸和肝功能变化时需要结合。", "肝功能");
    addMissing(items, "PT/INR", "判断凝血功能和肝功能储备时需要结合。", "凝血");
    addMissing(items, "肌酐", "判断肾功能和失代偿并发症风险时需要结合。", "肾功能");
    addMissing(items, "钠", "判断失代偿相关电解质风险时需要结合。", "电解质");
    addMissing(items, "AFP", "肝硬化随访中用于补齐肝癌筛查链条。", "肿瘤标志物");
    addMissing(items, "胃镜", "评估是否存在食管胃底静脉曲张。", "内镜");
    addMissing(items, "症状记录", "需要确认腹胀、黑便/呕血、嗜睡、脚肿、尿少、发热等警讯。", "症状");
    return List.copyOf(items);
  }

  private void addMissing(
      List<UltrasoundFollowUpResult.MissingInput> items,
      String name,
      String reason,
      String category) {
    items.add(new UltrasoundFollowUpResult.MissingInput(name, reason, category));
  }

  private List<UltrasoundFollowUpResult.RiskModule> riskModules(
      ReportSnapshot current,
      List<UltrasoundFollowUpResult.FindingRow> rows,
      List<UltrasoundFollowUpResult.MissingInput> missingInputs) {
    if (!isLiverUltrasound(current, null)) {
      return List.of();
    }
    List<String> missingNames = missingInputs.stream().map(UltrasoundFollowUpResult.MissingInput::name).toList();
    List<String> hccEvidence = new ArrayList<>();
    hccEvidence.add(rowSummary(rows, "明确占位/结节", "本次未见明确占位描述"));
    List<String> portalEvidence = new ArrayList<>();
    portalEvidence.add(rowSummary(rows, "门静脉主干", "本次门静脉信息不足"));
    portalEvidence.add(rowSummary(rows, "侧支循环/脐静脉", "本次侧支循环/脐静脉信息不足"));
    portalEvidence.add(rowSummary(rows, "脾脏", "本次脾脏信息不足"));
    List<String> decompEvidence = new ArrayList<>();
    decompEvidence.add(rowSummary(rows, "腹水", "本次腹水信息不足"));

    return List.of(
        new UltrasoundFollowUpResult.RiskModule(
            "肝癌筛查完整性",
            hasSuspiciousMass(rows) ? "warning" : "watch",
            hasSuspiciousMass(rows)
                ? "本次存在占位/结节相关线索，需结合 AFP 和必要时增强 CT/MRI 由专科判断。"
                : "仅凭本次彩超不能确认肝癌筛查链条完整，需结合 AFP 和定期随访时间。",
            hccEvidence,
            pickMissing(missingNames, "AFP")),
        new UltrasoundFollowUpResult.RiskModule(
            "门静脉高压线索",
            hasPresent(rows, "侧支循环/脐静脉") || hasPresent(rows, "腹水") ? "warning" : "watch",
            "需把门静脉、脾脏、侧支循环/脐静脉、腹水与血小板和胃镜结果合并判断。",
            portalEvidence,
            pickMissing(missingNames, "血小板", "胃镜")),
        new UltrasoundFollowUpResult.RiskModule(
            "失代偿警讯",
            hasPresent(rows, "腹水") ? "warning" : "watch",
            "彩超只能提供部分线索，仍需主动记录腹胀、黑便/呕血、嗜睡、脚肿、尿少、发热等症状。",
            decompEvidence,
            pickMissing(missingNames, "白蛋白", "总胆红素", "PT/INR", "肌酐", "钠", "症状记录")));
  }

  private String rowSummary(List<UltrasoundFollowUpResult.FindingRow> rows, String module, String fallback) {
    return rows.stream()
        .filter(row -> module.equals(row.module()))
        .findFirst()
        .map(row -> module + "：" + row.currentValue() + "（" + row.currentStatus() + "）")
        .orElse(fallback);
  }

  private boolean hasSuspiciousMass(List<UltrasoundFollowUpResult.FindingRow> rows) {
    return rows.stream().anyMatch(row -> "明确占位/结节".equals(row.module()) && "SUSPICIOUS".equals(row.currentStatus()));
  }

  private boolean hasPresent(List<UltrasoundFollowUpResult.FindingRow> rows, String module) {
    return rows.stream().anyMatch(row -> module.equals(row.module()) && "PRESENT".equals(row.currentStatus()));
  }

  private List<String> pickMissing(List<String> values, String... names) {
    List<String> result = new ArrayList<>();
    for (String name : names) {
      if (values.contains(name)) {
        result.add(name);
      }
    }
    return result;
  }

  private String patientSummary(
      ReportSnapshot current,
      ReportSnapshot previous,
      List<UltrasoundFollowUpResult.FindingRow> rows,
      List<UltrasoundFollowUpResult.MissingInput> missingInputs,
      Change change) {
    if (!isLiverUltrasound(current, previous)) {
      return change.summary();
    }
    String cirrhosis = hasPresent(rows, "肝硬化影像表现") ? "本次彩超仍提示肝硬化/慢性肝病影像表现。" : "本次彩超未提取到明确肝硬化描述。";
    String trend = change.summary();
    String portal = rows.stream()
        .filter(row -> "门静脉主干".equals(row.module()))
        .findFirst()
        .map(row -> "门静脉主干" + row.currentValue() + "，" + trendLabel(row.trendStatus()) + "。")
        .orElse("");
    String missing = missingInputs.isEmpty()
        ? ""
        : "整体风险还需要结合血小板、白蛋白、胆红素、PT/INR、肌酐、钠、AFP、胃镜和症状记录。";
    return compact(cirrhosis, portal, trend, missing);
  }

  private String clinicalSummary(
      ReportSnapshot current,
      ReportSnapshot previous,
      List<UltrasoundFollowUpResult.FindingRow> rows) {
    if (!isLiverUltrasound(current, previous)) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (UltrasoundFollowUpResult.FindingRow row : rows) {
      if (parts.size() >= 5) {
        break;
      }
      parts.add(row.module() + ":" + row.currentValue() + "/" + row.trendStatus());
    }
    return String.join("；", parts);
  }

  private String confidenceLevel(
      List<UltrasoundFollowUpResult.FindingRow> rows,
      List<UltrasoundFollowUpResult.MissingInput> missingInputs) {
    boolean limited = rows.stream().anyMatch(row -> "LIMITED_QUALITY".equals(row.trendStatus()));
    boolean insufficient = rows.stream().anyMatch(row -> "INSUFFICIENT_INFO".equals(row.trendStatus()));
    if (limited || missingInputs.size() >= 6) {
      return "LOW";
    }
    if (insufficient || !missingInputs.isEmpty()) {
      return "MEDIUM_LOW";
    }
    return "MEDIUM";
  }

  private List<String> nextQuestionsForDoctor(
      ReportSnapshot current,
      List<UltrasoundFollowUpResult.FindingRow> rows,
      List<UltrasoundFollowUpResult.MissingInput> missingInputs) {
    if (!isLiverUltrasound(current, null)) {
      return List.of("这份超声报告中需要重点随访哪些发现？", "下次复查建议多久进行？");
    }
    List<String> questions = new ArrayList<>();
    if (rows.stream().anyMatch(row -> "侧支循环/脐静脉".equals(row.module()) && "INSUFFICIENT_INFO".equals(row.trendStatus()))) {
      questions.add("既往提示脐静脉扩张/侧支循环，本次是否仍存在？");
    }
    questions.add("是否需要结合 AFP 或增强 CT/MRI 补齐肝癌筛查？");
    questions.add("是否需要近期复查血小板、肝功能、凝血、肾功能和电解质？");
    questions.add("是否需要胃镜评估食管胃底静脉曲张？");
    questions.add("如果近期有腹胀、黑便/呕血、嗜睡、脚肿、尿少或发热，是否需要提前就诊？");
    return questions;
  }

  private String trendLabel(String status) {
    return switch (status) {
      case "BASICALLY_STABLE" -> "较上次未见明确恶化";
      case "POSSIBLE_WORSENED" -> "较上次可能进展";
      case "POSSIBLE_IMPROVED" -> "较上次可能减轻";
      case "LIMITED_QUALITY" -> "检查质量限制判断";
      case "INSUFFICIENT_INFO" -> "信息不足";
      default -> "需结合医生判断";
    };
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
    return String.join("；", reportSegments(payload));
  }

  private List<String> reportSegments(JsonNode payload) {
    if (payload == null || !payload.isObject()) {
      return List.of();
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
      parts.addAll(List.of(raw.split("[；;。\\n]+")));
    }
    return parts.stream()
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
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

  private boolean containsNegative(String text) {
    return containsAny(text, "未见", "未发现", "未探及", "未显示", "无明显", "未扩张", "未见明显");
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

  private Double firstNumberMm(String text) {
    Matcher matcher = MM_PATTERN.matcher(text == null ? "" : text);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Double.parseDouble(matcher.group(1));
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private String formatNumber(double value) {
    if (value == Math.rint(value)) {
      return String.valueOf((int) value);
    }
    return String.format(Locale.ROOT, "%.1f", value);
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

  private record FindingSpec(String module, List<String> keywords, boolean defaultPositive) {}

  private record Finding(
      String value,
      String status,
      List<UltrasoundFollowUpResult.EvidenceItem> evidence) {
    static Finding notAvailable() {
      return new Finding("无历史报告", "NOT_MENTIONED", List.of());
    }
  }
}
