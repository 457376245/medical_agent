# Instructions / Prompt 策略层优化

## 1. 元数据

- 状态：有条件通过
- 负责人：Codex
- 开始日期：2026-05-07
- 最后更新日期：2026-05-07
- 相关请求：
  - “分析当前工程的Instructions / Prompt 策略层是如何设计和实现的，分别从业务和架构上考虑是否有可以优化部分”
  - “执行”
  - “$requirement-doc-tracking 根据刚刚的需求分析生成文档”
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：`docs/requirements/2026-05-07-prompt-strategy-optimization-review.md`

## 2. 原始需求

- 用户原始诉求：分析并优化当前 `backend-agent` 工程的 Instructions / Prompt 策略层，从业务和架构两个角度识别问题并落地改进。
- 原始上下文：
  - 当前工程是 FastAPI + LangGraph 医疗 Agent 服务。
  - 已存在基础 system prompt、场景 prompt、疾病档案上下文注入、工具调用说明和 provider 层解析/生成 prompt。
  - 用户要求先分析策略层设计与实现，再执行优化。
- 后续补充：
  - 用户明确要求执行前述优化计划。
  - 用户在实现完成后要求补充需求实施追踪文档。

## 3. 摘要

本需求对医疗 Agent 的 Instructions / Prompt 策略层进行一次 P0/P1 优先级优化：收紧对话元数据和附件输入契约，集中化 Agent prompt 组装，拆分系统预加载工具与模型可见工具，脱敏 SSE 工具事件和会话 trace，集中管理 provider 层 prompt，并对疾病档案关键字段渲染增加异常优先级。代码实现和测试已完成，当前文档按实际交付状态对齐，等待独立 Requirement Doc Review。

## 4. 背景和目标

- 业务背景：
  - 医疗 Agent 需要在慢病随访、报告解读、复诊准备、用药审查等场景中稳定遵循医疗安全边界。
  - 当前 prompt 策略已经具备基础分层，但关键业务意图依赖自由 `metadata`，工具事件存在敏感信息外露风险，provider prompt 和 Agent prompt 分散维护。
- 用户 / 问题陈述：
  - 需要让 prompt 策略层更可控、更可测试、更符合医疗隐私和业务工作流要求。
- 目标：
  - 将用户意图、附件、上下文、场景、错误恢复等 prompt 输入变成明确、可验证的策略层组件。
  - 减少模型误调用工具和重复失败工具调用。
  - 降低前端 SSE 和会话 trace 泄露患者上下文、OSS 对象键、完整报告字段的风险。
  - 将 provider prompt 从基础设施代码中抽出，形成统一 prompt 管理入口。
- 成功标准：
  - 现有聊天接口保持兼容，新增 typed metadata 和附件输入。
  - Agent LLM 输入由集中 composer 构造，并覆盖基础角色、附件、上下文、场景和工具错误提示。
  - `fetch_disease_profile_context` 不再默认暴露给模型主动选择。
  - SSE 工具事件和会话 trace 不暴露 raw tool input/output。
  - provider 层 prompt 常量集中到 `app/prompts/`。
  - 定向测试和全量测试通过。

## 5. 范围边界

### 本次做

- 新增 typed chat metadata，并保留未知字段兼容旧调用方。
- 新增附件输入模型，供 prompt composer 生成附件提示。
- 新增 Agent runtime prompt composer。
- 拆分预加载工具与模型可见工具。
- 脱敏 SSE `tool_call` / `tool_result` 事件及会话 trace。
- 抽离 provider 层 prompt 常量。
- 对疾病档案关键字段做轻量异常优先排序。
- 增加对应单元和 API 测试。

### 本次不做

- 不引入外部 prompt 管理平台。
- 不改造 LangGraph 总体状态图形态。
- 不新增多 Agent 路由。
- 不改造 Java 后端上下文 API 协议，仅兼容读取可选异常/严重度字段。
- 不重构 memory/checkpointer 存储。
- 不实现完整 prompt 评测集或线上监控面板。

### 假设

- 前端可以接受 `tool_call` / `tool_result` 事件名不变但 payload 变为脱敏摘要。
- 现有调用方可以继续传自由 `metadata`，但已知字段会受到枚举校验。
- raw tool output 仍可在 LangGraph 内部消息中供模型使用；本次只限制对外 SSE 和会话 trace 暴露。
- Java 上下文 API 可能提供 `resultState`、`severity`、`alertLevel`、`isAbnormal`，也可能不提供；缺失时保持原有排序。

### 待确认问题

- 前端是否依赖原始 `tool_call.input.object_key` 或完整 `tool_result.output` 展示调试信息。
- 后续是否需要把 prompt version 写入持久化会话元数据或 LangSmith trace。

## 6. 验收标准

- [x] 标准 1：聊天请求支持 typed metadata，并对 `workflow/scenario/audience/urgency_level` 做枚举校验，同时保留未知 metadata 字段。
- [x] 标准 2：聊天请求支持附件列表，Agent prompt 能在需要解析附件时提示模型使用对应 `object_key` 和 `file_type`。
- [x] 标准 3：Agent LLM 输入由集中 prompt composer 构造，覆盖基础 system prompt、附件提示、疾病档案上下文、场景/工作流提示、工具错误提示，并统一进入裁剪预算。
- [x] 标准 4：工具注册拆分为系统预加载工具和模型可见工具，`fetch_disease_profile_context` 默认不再模型可见。
- [x] 标准 5：SSE 工具事件和保存的 trace 只暴露脱敏摘要，不暴露 patient id、OSS object key、完整上下文 JSON 或完整报告字段。
- [x] 标准 6：相同工具和参数在同一轮失败后，再次调用时被短路为友好错误，不重复触发底层工具。
- [x] 标准 7：provider 层解析、生成、分类 prompt 被集中到 `app/prompts/provider.py`，原有结构化输出行为保持不变。
- [x] 标准 8：新增/更新测试覆盖本次策略层优化，并通过全量 pytest。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`
- 主要模块 / 文件：
  - `app/schemas/chat.py`
  - `app/agent/prompting.py`
  - `app/agent/nodes.py`
  - `app/agent/context.py`
  - `app/tools/registry.py`
  - `app/api/chat.py`
  - `app/api/tool_events.py`
  - `app/prompts/provider.py`
  - `app/providers/llm.py`
  - `app/services/disease_profile_context.py`
- API / 路由：
  - `POST /api/v1/chat`：请求体新增可选 `attachments`；`metadata` 从自由 dict 收束为兼容扩展的 typed metadata。
  - `POST /api/v1/chat` SSE：`tool_call` / `tool_result` 事件名保持不变，payload 改为脱敏摘要。
  - `GET /api/v1/sessions/{thread_id}`：turn trace 中的工具事件数据变为脱敏摘要。
- 数据库 / 表 / 字段：
  - 无 schema 迁移。
  - 会话 turn 的 `metadata` 和 `trace_events` 存储内容发生脱敏策略变化。
- 配置：
  - 无新增环境变量。
- 定时任务 / MQ / 外部依赖：
  - 无新增定时任务。
  - MQ 解析/生成链路未改变。
  - OpenAI 兼容服务调用方式未改变。
  - Java 疾病档案上下文 API 未改协议，仅在 Python 映射层兼容读取可选字段。

## 8. 实施方案

- 方案概述：
  - 在 API schema 层明确用户意图与附件输入。
  - 在 Agent 层新增 prompt composer，统一决定最终发给 LLM 的 system/context/scenario/error prompt。
  - 在工具注册层拆分系统预加载工具和模型可见工具。
  - 在 API 层对工具事件做统一脱敏。
  - 在 prompts 层集中 provider prompt 常量。
- 关键设计决定：
  - `AgentMetadata` 使用 Pydantic `extra="allow"`，避免破坏已有自由 metadata 扩展。
  - 附件 `object_key` 只注入给模型 prompt 和内部工具链路，不在 SSE/trace 对外泄露。
  - prompt composer 将多个动态 prompt 合并为一个 system message，避免 `trim_messages` 在多 system message 情况下丢失后续系统提示。
  - 工具执行节点保留所有工具可执行能力，但模型默认只绑定 `parse_document` 和 `generate_medical_text`。
  - `fetch_disease_profile_context` 继续由图的 context preload 机制强制调用。
- 替代方案与取舍：
  - 未采用外部 prompt 平台：当前需求更适合小步集中化，避免新增运行时依赖。
  - 未按场景进一步收窄 `parse_document` / `generate_medical_text`：先解决上下文工具重复暴露问题，降低兼容风险。
  - 未把 raw tool trace 完全删除出 LangGraph checkpoint：模型仍需要工具结果继续推理，本次只管对外暴露面。
- 风险：
  - 如果前端依赖旧 `tool_call.input` 或 `tool_result.output` 的原始内容，需同步前端适配。
  - typed metadata 对已知字段会严格校验，传入未知枚举值会返回校验错误。
  - prompt composer 合并 system message 后，极少数模型对多 system message 和单 system message 的行为可能略有差异。

## 9. 实施计划

1. 增加 typed metadata 与附件 schema，保持下游 graph metadata 为普通 dict。
2. 新增 provider prompt 常量模块，迁移 `LLMService` 中的内联 prompt。
3. 新增 Agent prompt composer，改造 LLM 节点调用。
4. 拆分工具注册组，模型默认不暴露 context preload 工具。
5. 新增 SSE 工具事件脱敏 helper，并接入聊天端点和 trace 保存。
6. 增加工具重复失败短路逻辑。
7. 补充单元/API 测试并运行定向和全量验证。

## 10. 进度日志

- 2026-05-07：完成现状分析，确认当前 prompt 策略由 system prompt、场景/工作流 prompt、疾病档案上下文、工具 docstring 和 provider prompt 共同组成。
- 2026-05-07：确认 P0/P1 优先优化范围：typed metadata、附件输入、prompt composer、工具分层、SSE 脱敏、provider prompt 集中化。
- 2026-05-07：完成代码实现并通过全量 pytest。
- 2026-05-07：按用户要求补充本需求实施追踪文档，并与实际交付状态对齐。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `app/schemas/chat.py` | 新增 `AgentMetadata`、枚举类型、`ChatAttachment`，`ChatRequest` 增加 `attachments`。 | 标准 1、2 |
| `app/api/chat.py` | 将 typed metadata 转为 graph dict；接入附件；SSE 工具事件使用脱敏 payload；保存 turn 时脱敏 metadata/trace。 | 标准 2、5 |
| `app/api/tool_events.py` | 新增工具输入/输出脱敏摘要函数。 | 标准 5 |
| `app/agent/prompting.py` | 新增 Agent prompt composer、prompt version、工具失败检测、工具参数哈希。 | 标准 3、6 |
| `app/agent/nodes.py` | LLM 节点改用 prompt composer；模型绑定 `get_model_tools()`；工具节点增加重复失败短路。 | 标准 3、4、6 |
| `app/tools/registry.py` | 拆分 `PRELOAD_TOOLS`、`MODEL_TOOLS`、`get_preload_tools()`、`get_model_tools()`。 | 标准 4 |
| `app/prompts/provider.py` | 新增 provider prompt 常量和版本常量。 | 标准 7 |
| `app/providers/llm.py` | 改为引用 provider prompt 常量，保持调用结构和 response format 不变。 | 标准 7 |
| `app/agent/context.py` | 对关键字段按异常/严重程度做轻量优先排序。 | 标准 3 |
| `app/services/disease_profile_context.py` | 映射可选异常/严重度字段供上下文渲染使用。 | 标准 3 |
| `tests/test_api/test_chat_schema.py` | 覆盖 typed metadata、未知扩展字段、非法 workflow、附件 schema。 | 标准 1、2、8 |
| `tests/test_agent/test_prompting.py` | 覆盖 prompt composer 的附件、上下文、工作流优先级、受众/紧急度提示。 | 标准 3、8 |
| `tests/test_tools/test_registry.py` | 覆盖 context 工具仅预加载可见、模型工具保留文档解析和文本生成。 | 标准 4、8 |
| `tests/test_api/test_tool_events.py` | 覆盖上下文、文档解析、文本生成工具事件脱敏。 | 标准 5、8 |
| `tests/test_agent/test_tool_error_recovery.py` | 增加相同工具参数重复失败短路测试。 | 标准 6、8 |
| `tests/test_api/test_agent_sessions.py` | 增加 SSE 和会话 trace 不暴露 OSS object key 的断言。 | 标准 5、8 |

## 12. 验证与测试

- 计划检查：
  - 定向运行 prompt、agent、api、tools、providers 相关测试。
  - 全量运行 pytest。
  - 运行 diff 空白检查。
  - 尝试运行 ruff。
- 已完成检查：
  - `uv run python -m pytest -q tests/test_prompts tests/test_agent tests/test_api tests/test_tools tests/test_providers`：通过，过程中定向集曾在 Python 3.11 `.venv` 下因既有 `uuid.uuid7()` 环境不匹配失败；切换为项目要求的 Python 3.14 后通过。
  - `uv run python -m pytest -q`：通过，`70 passed`。
  - `git diff --check`：通过，仅出现 Git LF/CRLF 转换警告。
- 未运行 / 尚未验证：
  - `uv run ruff check .` 未成功运行。
  - 未做真实 OpenAI 兼容服务联调。
  - 未做前端兼容性验证。
- 未验证原因：
  - 当前项目环境未安装 `ruff`，命令失败为 `program not found`。
  - 本次改动集中在策略层和 API 事件脱敏，未配置真实外部服务联调。
  - 当前仓库仅包含 `backend-agent`，未修改前端。

## 13. 风险与后续事项

- 剩余风险：
  - 前端如果依赖旧工具事件 raw payload，需要同步调整展示逻辑。
  - 当前 prompt version 已在 composer/provider 常量中定义，但尚未持久化到会话记录或外部 trace。
  - `workflow/scenario/audience/urgency_level` 枚举严格化可能暴露调用方传参错误。
  - `ruff` 未安装，静态 lint 尚未验证。
- 后续事项：
  - 与前端确认 `tool_call` / `tool_result` 新 payload。
  - 将 `AGENT_PROMPT_VERSION`、`PROVIDER_PROMPT_VERSION` 写入会话或 LangSmith trace。
  - 增加基于病例样例的 prompt 行为评测集。
  - 安装或恢复项目 lint 工具后执行 `ruff check .`。
- 阻塞项：
  - 无阻塞项；当前代码实现和测试已完成。

## 14. 最终一致性检查

- 已交付的业务行为：
  - 聊天请求可明确表达业务场景、工作流、受众、紧急度和附件。
  - Agent 对话 prompt 会稳定合并基础医疗安全边界、疾病档案上下文、场景/工作流和工具错误恢复提示。
  - 前端和会话详情看到的是工具进度/结果摘要，不再看到 raw object key、患者 ID、完整上下文 JSON 或完整报告字段。
  - 上下文关键字段在具备异常/严重度标记时优先展示风险更高的内容。
- 已交付的技术实现：
  - 新增 typed schema、prompt composer、provider prompt 常量、tool event sanitizer。
  - 改造 LLM 节点、工具注册、聊天 API 和 provider LLM prompt 引用。
  - 增加重复失败工具调用短路。
- 与原始计划的差异：
  - 原计划提到可将 prompt diagnostics 持久化；本次仅在 composer 返回 diagnostics 并用于日志，未落库。
  - 原计划提到 graph 可拆分 preload tool node 和 model tool node；实际实现保持执行节点可执行全部工具，仅模型绑定时隐藏 preload 工具，以减少图改造风险。
- 验收标准满足情况：
  - 8 项验收标准均已满足。
- 证据与验证：
  - 全量 `uv run python -m pytest -q` 通过，`70 passed`。
  - `git diff --check` 通过。
- 未验证事项：
  - `ruff` 未运行成功。
  - 未做真实 LLM 联调。
  - 未做前端兼容验证。
- 后续工作：
  - 独立执行 Requirement Doc Review。
  - 根据前端适配情况决定是否增加过渡兼容字段。
  - 补充 prompt 行为评测集和 prompt version trace。

## 15. Requirement Doc Review 交接

- 审查状态：已审查
- 需求达标审查报告：`docs/requirements/2026-05-07-prompt-strategy-optimization-review.md`
- 审查重点：
  - 文档验收标准是否与实际代码行为一致。
  - `POST /api/v1/chat` 的 typed metadata 与附件输入是否保持兼容。
  - SSE 工具事件和会话 trace 是否确实不泄露敏感字段。
  - `fetch_disease_profile_context` 是否只作为系统预加载工具，不再默认模型可见。
  - provider prompt 抽离是否未改变解析/生成/分类行为。
  - 重复失败工具调用短路是否不会阻断正常不同参数或不同工具调用。
- 已知需要审查的问题：
  - `ruff` 未安装，静态 lint 未验证。
  - 未做真实外部 LLM 和前端联调。
  - 工作区存在与本需求无关的未跟踪文档 `docs/agent-project-review-path.md`，本需求文档未将其列为交付代码变更。
