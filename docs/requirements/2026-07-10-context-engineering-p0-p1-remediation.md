# Agent Context Engineering P0/P1 整改

## 1. 元数据

- 状态：进行中
- 负责人：composer-executor（实现与验证）/ Codex 主代理（独立审查与复审）
- 开始日期：2026-07-10
- 最后更新日期：2026-07-10
- 相关请求：使用子代理 composer 完成评估报告中的 3 个 P0 和 5 个 P1，并由主代理审核直至全部达标
- 相关分支 / 提交 / PR：当前工作区未提交；必须保留既有回答 evaluator 相关改动
- 需求达标审查报告：docs/requirements/2026-07-10-context-engineering-p0-p1-remediation-review.md

## 2. 原始需求

- 用户原始诉求：使用子代理 composer 完成修复 3 个 P0 问题和 5 个 P1 问题，并由主代理审核直至全部达标。
- 原始上下文：3 个 P0 和 5 个 P1 来自仓库根目录 `AI_PROJECT_LEARNING_ASSESSMENT.md`。P0 分别是身份/患者作用域/会话隔离、非可信数据与工具授权、上下文缓存新鲜度；P1 分别是实际 history/token budget、provenance/时间/引用、长期记忆确认与冲突、grounded evaluator、Context 可观测性。
- 后续补充：实现必须由 `composer-executor` 子代理负责；主代理不直接修改业务实现，只负责需求轨迹、diff 审查、验证、达标审查和将未达标项退回继续修复。

## 3. 摘要

本需求对医疗 Agent 的 Context Engineering 主链进行生产化整改。整改贯穿前端调用、Python FastAPI 认证与 session、OpenAI Agents SDK runtime、Java 权威业务上下文、SQLite 会话状态、OSS 工具授权、长期患者记忆、回答 evaluator 和 trace。目标不是增加 Agent/RAG 复杂度，而是让现有单 Agent 架构具备端到端身份隔离、可信数据分层、可失效上下文、实际生效的历史预算、事实 provenance、受控长期记忆、grounded 评估和无敏感正文的 Context 诊断证据。

## 4. 背景和目标

- 业务背景：项目处理患者报告、用药、过敏、症状、红旗和长期画像，Context 错配、陈旧、越权或被注入会直接影响医疗回答可靠性。
- 用户 / 问题陈述：现有工程功能骨架较完整，但 3 个 P0 阻断安全上线，5 个 P1 使 Context 质量、评估和诊断不足。
- 目标：
  - 所有 Agent API、业务会话、SDK history、runtime state、Java context 和 memory submit 使用同一个已验证 owner/patient scope。
  - 固定高优先级 instructions 与非可信业务数据分开，模型工具只能访问本轮已授权资源。
  - Context 有 revision、获取时间、可配置 TTL 和失败恢复，不再永久缓存 ID 或 unavailable。
  - 历史预算/压缩接入真实 Agents SDK 主链，删除失效的旧裁剪路径。
  - 关键医疗事实带 evidence/provenance/time/verification 信息，并在模型 Context 中可引用。
  - 长期记忆具有风险下限、置信度/evidence 门槛、冲突/更正和有效期语义。
  - evaluator 能读取实际脱敏 evidence/context，并有版本、超时和可回归 rubric。
  - trace 能证明本轮 Context 版本、年龄、缓存、token/usage、工具和评估状态，同时不记录 raw 医疗正文、JWT、OSS key 或患者标识明文。
- 成功标准：第 6 节 8 条功能验收标准和 2 条交付门禁全部有代码与测试证据，Requirement Doc Review 结论为“通过”。

## 5. 范围边界

### 本次做

- Python Agent API 认证、scope 解析和会话 owner 隔离。
- Java 增加最小的 Agent scope 解析/验证契约，并让 internal Agent API 在安全模式下 fail-closed。
- Python → Java context/memory 调用传播已验证 scope，不使用客户端 body 中的身份字段作为授权事实。
- 业务 Context 从高优先级 instructions 移到明确标注的本轮临时数据输入；工具参数绑定授权附件。
- Context revision/generated-at、TTL、失败重试和 trace。
- Agents SDK 真实 session history 限制/压缩/输入合并；清理迁移后失效的旧 composer/trim 代码。
- 关键事实 evidence/provenance/time/verification 渲染。
- 长期记忆自动确认门槛、风险下限、冲突/更正/有效期。
- evaluator grounded context、rubric version、超时和 trace。
- Context diagnostics、token usage 和无敏感正文的可观测字段。
- Python、Java、前端受影响契约的自动化测试和文档对齐。

### 本次不做

- 不新增多 Agent、handoff、向量数据库、通用 RAG 框架或 Prompt 管理平台。
- 不迁移 Provider/MQ 主链，不统一重写 Responses API 与 Chat Completions Provider。
- 不为单实例开发场景提前引入 Redis、PostgreSQL session store 或分布式锁。
- 不重做前端 Agent UI；仅适配必要的认证、事件或引用字段契约。
- 不清理与本需求无关的现有技术债和未提交改动。

### 假设

- Java 继续作为 JWT、tenant/user/patient 归属和医疗业务数据的权威来源；Python 不复制 JWT 密钥和验证实现。
- Python 可用已有标准库 HTTP 客户端调用 Java scope 验证接口，不新增认证框架依赖。
- `openai-agents==0.17.5` 当前安装版本支持的 session input callback、session settings、usage/tracing 能力优先复用；若某项 API 不存在，composer 必须用该版本已有的最小等价能力并在文档记录差异。
- 安全模式下缺少 internal API key 属于配置错误；本地显式关闭安全时可以保留可测试的开发行为，但不能隐式 fail-open。
- 既有无 owner 的 SQLite 历史不自动归属任何用户；默认隔离/不可见，避免错误认领。
- Context cache 默认优先正确性：TTL 可配置且默认不允许跨 turn 无限复用；`unavailable` 不作为成功缓存。

### 待确认问题

- 无阻塞待确认项。实现细节由 composer 在不改变验收语义的前提下选择最小方案；任何范围偏差必须先更新本文档。

## 6. 验收标准

- [ ] 标准 1（P0-1 身份与隔离）：`/api/v1/chat` 和全部 `/api/v1/sessions` 操作必须验证 Bearer token；scope 由 Java 验证并返回 tenant/user/patient，body metadata 不能覆盖身份；业务 session/turn/runtime state 和 SDK session 均绑定 owner；跨用户/跨患者 list/read/resume/rename/delete/chat 被拒绝且不能覆盖既有 thread；Java internal Agent API 在安全模式且 key 缺失/错误时 fail-closed。
- [ ] 标准 2（P0-2 可信边界与工具授权）：固定 instructions 不含报告正文、个人背景、附件 object key 等动态非可信数据；业务 bundle/附件作为明确的临时低权限数据输入；`parse_document` 仅接受本轮已授权附件 key；工具输出/文档中的指令不能扩大权限；长期记忆抽取不依赖 assistant 自述来制造用户事实；SSE/trace/session 不泄露 JWT、raw object key、patient/user/tenant 明文或完整 bundle。
- [ ] 标准 3（P0-3 Context 新鲜度）：Java context 返回稳定 `context_revision` 和 `generated_at`；Python state 保存 revision/fetched-at；cache 使用可配置 TTL，底层数据变化能在规定窗口内刷新；`unavailable`/临时失败不会保存为可跳过后续请求的成功签名；同一 profile/record 更新后可获取新 revision；trace 记录 status/revision-age/cache hit-miss/retry 的脱敏诊断。
- [ ] 标准 4（P1-1 History/token budget）：`CONVERSATION_WINDOW_MAX_TOKENS` 或替代配置真实影响 `Runner.run_streamed()` 主链；使用当前 Agents SDK 支持的 session limit/input callback/compaction 或等价机制选择历史，保留固定规则、当前问题、高风险医疗事实和最近 turn，清理/摘要旧工具结果；长字段有预算；迁移后仅测试使用的旧 `build_prompt_messages/_trim_messages` 路径和误导配置被删除或重新接入，不允许继续形成假保护。
- [ ] 标准 5（P1-2 Provenance/时间/引用）：关键报告字段、趋势、用药、过敏、红旗和长期记忆在 context bundle/模型输入中具有稳定 evidence ID、来源类型/引用、观察或更新时间、确认状态；待确认/过期/冲突事实不会被渲染为已确认当前事实；固定 Prompt 要求基于 evidence ID 引用关键医学结论；至少有 renderer/Prompt 测试证明这些字段进入正确的低权限 Context。
- [ ] 标准 6（P1-3 长期记忆）：模型提供的 risk 不能降低字段固有风险下限；自动确认仅限明确允许的低风险个人偏好/背景，且同时满足 confidence 和 evidence 门槛；症状、红旗、健康目标、用药、过敏、诊断和医生交代不自动确认；重复候选被抑制；更正/冲突使用 `supersedes_memory_id` 和有效期或等价显式状态，确认新事实后旧事实不再作为当前事实进入 Context；相关 Java/Python 测试覆盖。
- [ ] 标准 7（P1-4 Grounded evaluator）：evaluator 输入包含本轮实际使用的脱敏 context/evidence 摘要，而非只含客户端 metadata；rubric/prompt 有版本；调用有明确超时和 unavailable 降级，不阻断主回答；结果能区分 groundedness、遗漏高风险事实、把待确认记忆当事实等问题；既有 SSE/trace/history 展示保持兼容并有测试。
- [ ] 标准 8（P1-5 Context 可观测性）：每个 Agent run 记录 model/prompt/context/evaluator 版本、context status/revision/age/cache、session item/压缩或裁剪、模型 request/input/output/cached token、工具状态/延迟和 evaluator 状态/延迟；SDK trace 使用明确 workflow/group 标识并显式关闭 sensitive data；业务 diagnostics 只保存低敏摘要，不含用户消息、回答正文、报告字段值、JWT、object key 或 owner 明文；测试验证 diagnostics 字段和脱敏。
- [ ] 标准 9（兼容与回归）：现有 chat SSE token/tool/error/done、session CRUD、疾病上下文、长期记忆、回答 evaluation、文档解析/生成工具和前端解析行为无未记录回归；Python 定向与全量测试、Java 定向与全量测试、前端测试/类型检查按可用环境通过。
- [ ] 标准 10（交付与审查）：需求文档最终与真实代码、接口、表/字段、配置和验证结果一致；独立 Requirement Doc Review 对标准 1-10 均判定“通过”，不存在未修复阻塞项。

## 7. 受影响的系统和文件

- 项目 / 服务：`frontend`、`backend-agent`、`backend-java`。
- 主要模块 / 文件（初始预计）：
  - Python：`app/api/chat.py`、`app/api/sessions.py`、`app/schemas/chat.py`、`app/agent/runtime.py`、`app/agent/context.py`、`app/agent/prompting.py`、`app/agent/state.py`、`app/agent/evaluator.py`、`app/tools/registry.py`、`app/tools/document_parse.py`、`app/services/disease_profile_context.py`、`app/services/patient_memory.py`、`app/memory/models.py`、`app/memory/store.py`、`app/config.py`、`app/main.py`、`.env.example`。
  - Java：认证/scope controller/service/DTO、`InternalAgentApiGuard`、`AgentContextController`、`AgentDiseaseProfileContextService`、context response DTO、`PatientMemoryService`、memory entity/migration、相关配置。
  - Frontend：原则上复用现有 `agentFetch`；仅在 SSE diagnostics/evidence 契约需要时修改 Agent types/parser。
  - 测试：Python agent/api/memory/services/tools 测试、Java auth/internal context/memory 测试、必要前端测试。
- API / 路由：
  - 新增或扩展 Java Bearer scope 验证接口，返回 tenant/user/patient scope；具体 URL 由实现确定并记录。
  - `POST /api/v1/chat` 与 `/api/v1/sessions/**` 从“接受但不验证 Authorization”变为强制认证。
  - Java internal context/memory API 增加可信 internal scope 传播规则。
  - Java context response 增加 `contextRevision`、`generatedAt` 和 provenance/evidence 字段。
- 数据库 / 表 / 字段：
  - Python SQLite `agent_sessions`、`agent_session_turns`、`agent_runtime_states` 增加 owner scope key；runtime state 增加 context revision/fetched-at/诊断所需字段。老行保持未归属且不可见。
  - Java `patient_memory_entries` 使用现有 `supersedes_memory_id`，并按最小实现增加 validity/current-state 所需字段或等价状态；必须记录实际 migration。
- 配置：internal key fail-closed/开发例外、context TTL、history/token/compaction、memory auto-confirm confidence、evaluator timeout、sensitive tracing。
- 定时任务 / MQ / 外部依赖：不改 MQ；依赖 Java scope/context API 和 OpenAI Agents SDK 当前版本；不新增第三方依赖，除非 composer 证明现有能力无法安全完成并先更新本文档。

## 8. 实施方案

- 方案概述：保留单 Agent、Java 权威业务聚合和双层 session 职责。Python 在 API 边界通过 Java 验证 Bearer + patient 归属，获得不可由 body 覆盖的 scope；scope 绑定所有存储和 SDK session，并通过 internal key 传播到 Java。固定 Prompt 只承载政策，动态 bundle 通过 SDK 本轮 input shaping 临时注入。runtime 使用 revision/TTL 管理 context，使用 SDK 原生历史选择/压缩和 usage/tracing，长期记忆与 evaluator 使用同一 evidence/provenance 数据。
- 关键设计决定：
  - 不在 Python 复制 JWT secret/算法，复用 Java 权威认证。
  - owner storage key 可使用 tenant/user/patient 的不可逆摘要；原始 ID 只在当前请求内存和可信 internal 请求中使用，不进入公开 trace。
  - 显式 thread_id 只能恢复已归属当前 owner 的会话；新会话由服务端创建，防止同 ID 覆盖他人会话。
  - Java internal key 在安全模式 fail-closed；internal scope header 只有通过 key 后才能写入 request scope。
  - 动态医疗数据不进入高优先级 instructions；使用 SDK input callback/filter 形成不持久化重复 bundle 的临时数据 item。
  - Context TTL 默认以正确性为先；`unavailable` 始终允许后续恢复。
  - 不保留两套历史裁剪实现；以真实 SDK 主链为唯一来源。
  - evaluator 继续作为只读评估，不成为医疗事实来源或记忆写入源。
- 替代方案与取舍：
  - Python 自行验证 JWT：拒绝，避免双密钥/双算法和权限漂移。
  - 所有请求先走 Java 反向代理：本次不做，改动面大；使用一个最小 scope 验证接口即可。
  - 向量库/多 Agent：拒绝，不能解决当前隔离、新鲜度和 provenance 问题。
  - 新增通用 Context framework：拒绝，复用 Pydantic/dataclass、Agents SDK 和现有 bundle。
  - 每轮同步大规模 judge：不扩大；先 grounded、超时、可回归，成本策略由指标决定。
- 风险：
  - Python/Java 契约和 SQLite schema 同时变化，旧测试/本地数据库需兼容迁移。
  - Agents SDK session input/compaction API 需以锁定版本实际签名为准。
  - scope 验证增加一次 Java 调用；先保证安全，后续只有指标证明时才做短 TTL token cache。
  - 双 session 存储可能在异常中断时漂移；本次必须保证 owner 隔离和可删除，不扩展为分布式事务。

## 9. 实施计划

1. P0-1：增加 Java scope 验证与可信 internal scope，Python API auth dependency，owner-scoped SQLite/SDK session，并补隔离测试。
2. P0-2：拆分固定 instructions/临时数据 input，绑定附件 allowlist，收紧记忆抽取来源并补注入/泄漏测试。
3. P0-3：增加 context revision/generated-at、state fetched-at/TTL、unavailable retry 和 cache diagnostics 测试。
4. P1-1：把 history/token budget 接到真实 SDK runner，清理旧裁剪死代码，补长历史/工具结果测试。
5. P1-2/P1-3：补 provenance/evidence/time/verification，长期记忆 risk/confidence/evidence/supersede/validity 规则和测试。
6. P1-4/P1-5：grounded evaluator、timeout/version、Context diagnostics/usage/trace 脱敏和测试。
7. 运行 Python、Java、前端定向与全量验证，更新本文档为真实交付状态并标记待审查。
8. 主代理执行 Requirement Doc Review；任何不通过项交回 composer 修复，重复验证直至全部通过。

## 10. 进度日志

- 2026-07-10：主代理完成静态 Context Engineering 评估，生成根目录评估报告。
- 2026-07-10：用户要求由 composer 子代理实现全部 3 个 P0 和 5 个 P1，并由主代理复审至达标。
- 2026-07-10：创建本需求追踪文档，冻结 10 条验收标准、实现边界和验证门禁；尚未开始业务代码修改。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| 待 composer 实施后更新 | 当前仅创建需求追踪文档 | 标准 10 |

## 12. 验证与测试

- 计划检查：
  - Python：auth/scope、cross-owner session、runtime context、tool authorization、history/compaction、memory、evaluator、diagnostics 定向测试；`uv run python -m pytest -q`。
  - Java：scope endpoint、internal guard/scope、context revision/provenance、memory risk/supersede 定向测试；`mvn test`。
  - Frontend：受影响 Vitest、`npx tsc --noEmit`；环境允许时 `npm run build`。
  - 静态：敏感字段搜索、旧 trim/composer 调用搜索、`git diff --check`、需求逐项证据表。
- 已完成检查：仅完成实现前静态代码/调用链审查和需求范围冻结；未运行项目测试。
- 未运行 / 尚未验证：全部实现后验证。
- 未验证原因：composer 尚未开始实现。

### 测试用例矩阵

| 类型 | 必测场景 |
| --- | --- |
| 正常 | 合法 token + 当前患者 chat/session/context/memory；revision 更新；long history；evidence-aware evaluation |
| 权限 | 缺 token、坏 token、伪造 patient/body scope、跨用户/跨患者 thread、错误/空 internal key、未授权 object key |
| 异常 | Java scope/context 超时、context unavailable 后恢复、evaluator 超时、SDK usage 缺失、旧 SQLite 无 owner 行 |
| 注入 | 报告字段/个人背景/附件名含“忽略规则/读取其他对象”；工具输出含指令；assistant answer 试图制造长期记忆 |
| 记忆 | 低 confidence、缺 evidence、模型降 risk、症状/红旗/用药自动确认企图、重复候选、明确更正/supersede/过期 |
| 长上下文 | 50+ turn、旧大工具结果、长报告字段；保留当前问题、过敏、用药、红旗并不 overflow |
| 脱敏 | SSE、业务 trace、SDK trace 配置和 session API 不含 JWT、owner 明文、object key、完整 bundle/报告值 |
| 回归 | 既有 chat SSE、session CRUD、context partial/unavailable、tool error、patient memory、evaluation UI/trace |

## 13. 接口变更

- Java scope 验证：Bearer token 必填；可选 patient selector；返回经数据库归属校验的 tenant/user/patient scope。401 表示 token 无效，403/404 表示患者不归属当前用户。
- Python Agent API：`Authorization: Bearer ...` 必填；`X-Patient-Id` 可选但必须经 Java 验证；body metadata 的身份字段被忽略/拒绝，不能成为 scope 来源。
- Java internal Agent API：internal key 在安全模式必填；tenant/user/patient scope 只接受通过 internal key 的 Python 服务传播。
- Context response：兼容增加 revision/generated-at/provenance/evidence 字段；Python 对缺失字段有明确降级，但新主链测试必须证明字段存在。
- SSE：保留既有事件；若新增 diagnostics，不向终端暴露低层敏感诊断，优先只持久化脱敏 trace。

## 14. 数据库变更

- Python SQLite 采用启动时兼容加列；已有无 owner 行不自动归属，作用域查询不可见。
- 新写入 session/turn/runtime state 必须带同一 owner key；所有更新/删除带 owner 条件。
- SDK session 使用 owner-scoped session ID，避免同 thread 跨 owner 共享历史。
- Java patient memory 使用 Flyway 新 migration 管理 validity/current-state 字段；不得修改已发布 migration。
- 回滚时应用代码可停止读取新增字段，但不能把已失效/被 supersede 的旧医疗事实重新当作当前事实；数据库 migration 默认前向兼容，不做破坏性回滚。

## 15. 上线与回滚

- 发布顺序：先 Java scope/internal context/memory 兼容接口与 migration，再 Python Agent auth/context runtime，最后必要前端契约。
- 配置：发布前设置 Java/Python 一致的 internal key；确认 security enabled；设置 context TTL、history/token budget、memory threshold、evaluator timeout、sensitive trace=false。
- 上线验证：合法/非法 token、跨患者、context revision、unavailable 恢复、未授权附件、长会话、记忆确认、evaluator timeout、trace 脱敏。
- 回滚条件：合法用户普遍 401/403、context 无法加载、session 无法恢复、SDK history 异常、医疗事实错误失效或敏感数据进入 trace。
- 回滚原则：Java 兼容字段和 migration 先保留；回滚 Python 时不得恢复无认证 Agent API。安全相关 P0 不允许通过配置退回 fail-open。

## 16. 风险与后续事项

- 剩余风险：待实现和验证后更新。
- 后续事项：只有 trace 证明召回、共享 session 或 prompt cache 有需求时，才另立需求处理 RAG/Redis/cache；不属于本次达标条件。
- 阻塞项：无。

## 17. 最终一致性检查

- 已交付的业务行为：尚未实施。
- 已交付的技术实现：仅需求追踪文档。
- 与原始计划的差异：无。
- 验收标准满足情况：标准 1-9 待实现，标准 10 待最终审查。
- 证据与验证：根目录评估报告和当前静态代码证据。
- 未验证事项：全部实现与运行验证。
- 后续工作：交由 composer-executor 实施。

## 18. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：docs/requirements/2026-07-10-context-engineering-p0-p1-remediation-review.md
- 审查重点：3 个 P0 的端到端安全性；5 个 P1 是否真实接入主链而非只新增死代码/配置；owner scope、Context revision、history budget、memory supersede、grounded evaluator 和 diagnostics 的测试强度；既有未提交 evaluator 变更是否被保留并正确演进。
- 已知需要审查的问题：当前工作区在本需求前已有未提交的 evaluator、前端 trace 和 2026-06-22 需求文档改动，审查必须区分既有改动与本次改动，不能覆盖或误归因。
