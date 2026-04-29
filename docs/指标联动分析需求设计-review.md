# 需求达标审查报告：指标归一化功能（独立审查）

## 1. 审查输入

- 需求实施追踪文档：`docs/指标联动分析需求设计.md`
- 工作区：`F:\maven_product\medical_agent`
- 分支：`master`，当前工作区包含未提交变更
- 审查时间：2026-04-29
- 审查类型：独立审查（不受已有审查报告影响）
- 审查范围：仅审查"指标名归一化"子功能，并补充生产上线验收门禁；不审查完整 OCR/LLM 外部链路

## 2. 审查结论

- **结论：有条件通过**
- 总体说明：指标编码库（155 个指标）、四层匹配策略、Java enrich 集成、LLM `standardCode` 字段链路、UNMAPPED 不阻塞机制和自动化测试均有代码证据支撑；本轮已补业务链路 E2E 用例、`standardCode` 合法/非法路径测试，并将生成样本门禁提升到 315 条。仍有两个上线前条件：(1) 当前样本为生成/AI 生成样本，不是需求文档要求的真实数据库抽样；(2) 当前环境 Docker 不可用，`ApiIntegrationTest` 被 Testcontainers 跳过，业务链路 E2E 需要在 Docker 可用环境实际跑通。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
|------|----------|------|------|
| 无代码阻塞问题 | - | 限定测试 `mvn "-Dtest=IndicatorNormalizerTest,IndicatorNormalizerSamplingTest,StructuredFieldInterpreterTest" test` 通过，21 个测试 0 失败 | 可按"有条件通过"推进 |
| 业务链路 E2E 本机未实际执行 | 中 | `mvn "-Dtest=ApiIntegrationTest" test` 因 Docker 环境不可用，Testcontainers 跳过 15 个集成测试 | 在 Docker 可用的 CI 或开发环境重新执行 `ApiIntegrationTest`，通过后再作为生产上线门禁证据 |

## 4. 验收标准逐项核对

| # | 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
|---|----------|----------|----------|-----------------|------|
| 1 | 能将 95%+ 的常见体检指标名映射到统一编码 | 成功标准："能将 95%+ 的常见体检指标名映射到统一编码"；验证要求："从现有数据库抽样 200 个真实 field name" | `IndicatorNormalizer` 四层匹配；`IndicatorCatalog` 加载 155 个指标 | `IndicatorNormalizerSamplingTest` 315/315 命中、100%；但 CSV 的 `sourceType` 为 `generated-common-term` / `ai-generated-common-term`，非真实数据库字段 | **部分通过** |
| 2 | 编码库覆盖约 150 个核心指标 | 自定义精简编码体系，覆盖常规体检 + 慢病随访约 150 个核心指标 | `indicator_catalog.json` 包含 155 个顶层编码 | PowerShell `ConvertFrom-Json` 统计为 155 | **通过** |
| 3 | 精确匹配 | trim + lowercase 后查 alias 索引 | `IndicatorNormalizer.normalize()` 第 1 层调用 `catalog.findByExact(cleaned)` | `IndicatorNormalizerTest.精确匹配英文缩写` + `精确匹配中文全称` | **通过** |
| 4 | 括号拆分匹配 | 提取 `()`/`（）` 内外部分分别精确匹配 | `IndicatorNormalizer.matchByParenthesisSplit()` 匹配中英文括号 | `IndicatorNormalizerTest.括号拆分匹配_中文括号包英文` + `括号拆分匹配_英文括号包中文` | **通过** |
| 5 | 包含匹配 | 输入包含 alias 或 alias 包含输入 | `IndicatorCatalog.findByContainment()` 两阶段匹配 + 长 alias 优先 + `isUnsafeShortAsciiAlias()` 保护 | `IndicatorNormalizerTest.包含匹配_短缩写` + `重复别名优先保留主指标且短英文不误伤` | **通过** |
| 6 | 英文 token 提取 | 从混合文本中提取连续英文字母再匹配 | `IndicatorNormalizer.matchByEnglishToken()` 使用 `[A-Za-z][A-Za-z0-9_-]{1,20}` 正则 | `IndicatorNormalizerTest.英文token提取_混合文本` | **通过** |
| 7 | 不使用编辑距离 | 明确排除 Levenshtein | `IndicatorNormalizer` 与 `IndicatorCatalog` 无任何编辑距离逻辑 | 代码审查确认 | **通过** |
| 8 | Java 侧验证 LLM 输出 `standardCode`，合法则采用，否则回退规则匹配 | parse 提示词输出 `standardCode`，Java 侧验证 code 是否在编码表中 | `StructuredFieldInterpreter.normalizeIndicatorInPlace()` 先读 `standardCode`，合法则保留并补 category；非法 code 会先移除，再按 name 归一化 fallback | `StructuredFieldInterpreterTest` 已覆盖合法 code 保留、非法 code fallback、非法 code 且未知指标不外泄 | **通过** |
| 9 | UNMAPPED 不阻塞流程 | UNMAPPED 指标照常展示和判定，仅不参与联动分析 | `IndicatorNormalizer.normalize()` 未命中返回 null；`normalizeIndicatorInPlace()` 不抛错、不写 `standardCode` | `StructuredFieldInterpreterTest.enrichPayloadDoesNotBlockUnmappedIndicator` 断言未知指标仍完成 resultState 判定 | **通过** |
| 10 | parse 提示词与 schema 配合输出 `standardCode` | parse 提示词新增 `standardCode` 字段提示 | `llm.py` 系统提示含 `standardCode` 指引；`document.py` schema hint 包含 `standardCode`；`ParseField` Pydantic 模型含 `standard_code` 字段（alias `standardCode`） | 静态代码证据可确认字段链路存在 | **通过** |
| 11 | 生产业务链路 E2E | 归一化应在解析结果入库到记录详情查询的真实业务链路生效 | `ApiIntegrationTest` 已补解析结果消费、记录详情返回归一化字段、下游 `combinationAnalysis` 冒烟用例 | 当前环境 Docker 不可用，`ApiIntegrationTest` 跳过，尚未取得实际通过结果 | **无法确认** |

## 5. 文档与代码一致性

### 文档准确的地方

- 核心链路"parse 输出 `standardCode` → Java enrich 验证/归一化 → 写入 `standardCode`/`category`"与代码一致。
- 文件清单中记录的核心 Java 类均存在且职责匹配。
- UNMAPPED 不阻塞的设计与实现一致。
- 短英文别名保护策略（`isUnsafeShortAsciiAlias`）与进度日志记录的修复一致。

### 文档过时或不准确的地方

| 问题 | 文档表述 | 代码实际 | 影响 |
|------|----------|----------|------|
| 文件格式 | "技术实现"示例写 `indicator_catalog.yaml`、`combination_rules.yaml` | 实际为 `indicator_catalog.json`、`combination_rules.json` | 低：不影响功能，但误导读者 |
| 匹配层数 | 范围内写"三层匹配策略"，但表格实际列出 5 层（含 LLM fallback） | 代码实现 4 层（无 LLM fallback） | 低：表格与代码一致，标题描述不准 |
| 样本来源 | 验证要求"从现有数据库抽样 200 个真实 field name" | 当前为 315 条生成/AI 生成常见指标样本 | 中：验证证据不满足文档定义的真实样本标准 |

### 代码中存在但文档未记录的变更

- `IndicatorCatalog` 使用 `putIfAbsent` 保证先出现的编码优先，避免重复别名覆盖。
- `IndicatorCatalog.normalizeKey()` 统一移除所有空格（包括全角空格），增强含空格写法匹配。
- `StructuredFieldInterpreter` 对非法 `standardCode` 增加移除逻辑，避免无法回退识别时把非法编码暴露给记录详情和组合规则。

## 6. 实现质量审查

### 正面发现

1. `isUnsafeShortAsciiAlias()` 防止 "K"、"P" 等短 ASCII token 误匹配，覆盖包含匹配的关键风险点。
2. 包含匹配采用两阶段匹配，并在第一阶段按 alias 长度取最长匹配，减少歧义。
3. UNMAPPED 返回 null 而非抛异常，`normalizeIndicatorInPlace()` 静默跳过，不影响记录详情。
4. 本轮修复非法 `standardCode` 残留问题，未知指标不会把非法编码继续暴露给下游。

### 需关注的问题

| # | 问题 | 严重级别 | 文件 | 说明 |
|---|------|----------|------|------|
| 1 | 编码库加载失败静默降级无告警 | 低 | `IndicatorCatalog.java` | `catch (IOException ignored)` 不打日志，运维无法感知编码库加载失败 |
| 2 | `standardCode` 大小写不敏感校验缺失 | 低 | `StructuredFieldInterpreter.java` | LLM 输出 `"alt"` 会校验失败并回退 name 匹配；功能通常不受影响，但行为应文档化 |
| 3 | 包含匹配第二阶段无长度上限 | 低 | `IndicatorCatalog.java` | `entry.alias().contains(normalizedName)` 只要求 `normalizedName.length() >= 2`，理论上存在短中文输入误匹配风险 |

## 7. 测试与验证缺口

### 已有验证

- `IndicatorNormalizerTest`（11 用例）：精确匹配、中文全称、括号拆分、包含匹配、英文 token、无法识别、空输入、HBV-DNA、HbA1c、多写法、重复别名优先和短英文不误伤。
- `IndicatorNormalizerSamplingTest`（1 用例，315 条数据）：数据驱动命中率验证，要求样本数 `>= 300` 且命中率 `>= 95%`，本次通过。
- `StructuredFieldInterpreterTest`（9 用例）：阈值检测、区间高低判定、未映射不阻塞、多标签分段、双连字符范围、legacy 字段 trend 转换、合法 `standardCode` 保留、非法 `standardCode` fallback、非法 `standardCode` 不外泄。
- `ApiIntegrationTest` 已补 3 个生产链路验收用例：记录详情返回归一化字段、合法/非法 `standardCode` 业务链路、归一化驱动 `combinationAnalysis` 冒烟。

### 缺失验证

| # | 缺失项 | 影响 | 建议 |
|---|--------|------|------|
| 1 | 真实脱敏数据库 field name 抽样 | 无法证明线上真实分布下 95%+ 命中率 | 补 200 条真实脱敏样本 CSV，标注 `sourceType=real-database` |
| 2 | 本机未实际执行业务链路 E2E | 当前环境 Docker 不可用，Testcontainers 跳过集成测试 | 在 Docker 可用的 CI 或开发环境执行 `mvn "-Dtest=ApiIntegrationTest" test` |
| 3 | `standardCode` 大小写容错行为文档化 | LLM 输出 `"alt"` vs `"ALT"` 的行为差异未被验收定义覆盖 | 明确当前规则：非法 code 移除并回退 name；如需大小写容错，另行实现 |

## 8. 风险与后续事项

### 交付风险

| 风险 | 级别 | 说明 |
|------|------|------|
| 核心功能 | 低 | 四层匹配 + UNMAPPED 不阻塞，主路径可靠 |
| 数据代表性 | 中 | 315 条生成/AI 生成样本不能直接外推到真实线上字段分布 |
| E2E 执行证据 | 中 | 已补集成测试，但当前环境因 Docker 不可用未实际执行 |

### 后续事项

1. **P0**：在 Docker 可用环境执行 `mvn "-Dtest=ApiIntegrationTest" test`，确认业务链路 E2E 通过。
2. **P1**：补 200 条真实脱敏 field name 样本复核命中率。
3. **P1**：将 UNMAPPED 高频项统计纳入运营或后台观测，形成冷启动迭代闭环。
4. **P2**：修正需求文档中 `indicator_catalog.yaml` → `.json`、"三层" → "四层" 等表述。
5. **P2**：明确 `standardCode` 大小写规范，决定是否需要大小写容错。

### 是否需要更新需求实施追踪文档

需要。当前文档存在以下应修正项：
- "技术实现"中的 `.yaml` 应改为 `.json`
- "三层匹配策略"标题应改为"四层匹配策略"
- 验证部分应区分"生成/AI 生成常见指标样本"与"真实数据库抽样"
- 生产上线门禁应补充业务链路 E2E 和真实样本复核要求

## 9. 最终建议

- **是否可以交付**：可以有条件交付。
- **生产上线前必须完成**：在 Docker 可用环境跑通 `ApiIntegrationTest`；若没有真实脱敏样本，上线说明必须明确样本代表性限制。
- **可后续优化**：补真实脱敏样本验证、修正文档表述、将 UNMAPPED 统计纳入运营、评估 `standardCode` 大小写容错。
