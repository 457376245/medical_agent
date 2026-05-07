# 需求达标审查报告：Instructions / Prompt 策略层优化

## 1. 审查输入

- 需求实施追踪文档：`docs/requirements/2026-05-07-prompt-strategy-optimization.md`
- 工作区：`E:\Python_Product\medical_agent\backend-agent`
- 分支 / 提交：`master` / `2bc5d81`，存在未提交工作区改动（本需求代码、测试、追踪文档，以及与本需求无关的未跟踪文档 `docs/agent-project-review-path.md`）
- 审查时间：2026-05-07
- 审查类型：最终审查

## 2. 审查结论

- 结论：有条件通过
- 总体说明：8 项验收标准均能在当前代码和测试中找到支撑证据，全量 `uv run python -m pytest -q` 已在本次审查中复跑通过（`70 passed`），`git diff --check` 通过且仅有 LF/CRLF 转换警告。结论为“有条件通过”的原因是仍存在非阻塞交付条件：`ruff` 未安装导致静态 lint 无法确认，且真实 OpenAI 兼容服务联调与前端工具事件兼容性未在当前仓库内验证。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无 | - | 当前未发现会阻断交付的需求缺口或实现错误；全量测试 `70 passed`。 | 按第 7 节补齐非阻塞验证后再做发布前确认。 |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：聊天请求支持 typed metadata，并对 `workflow/scenario/audience/urgency_level` 做枚举校验，同时保留未知 metadata 字段。 | `ChatRequest.metadata` 从自由 dict 收束为兼容扩展的 typed metadata。 | `app/schemas/chat.py:9-44` 定义 Literal 枚举和 `AgentMetadata`，`model_config = ConfigDict(extra="allow")` 保留未知字段；`app/api/chat.py:146-153` 使用 `to_graph_metadata()` 进入 graph。 | `tests/test_api/test_chat_schema.py:9-32` 覆盖合法 typed metadata、未知扩展字段保留、非法 workflow 校验失败；全量 pytest 通过。 | 通过 |
| 标准 2：聊天请求支持附件列表，Agent prompt 能在需要解析附件时提示模型使用对应 `object_key` 和 `file_type`。 | 请求体新增 `attachments`，prompt composer 注入附件解析提示。 | `app/schemas/chat.py:48-71` 定义 `ChatAttachment` 与 `ChatRequest.attachments`；`app/api/chat.py:150-153` 将附件写入 graph metadata；`app/agent/prompting.py:85-87`、`app/agent/prompting.py:139-164` 生成附件提示并包含 `object_key` / `file_type`。 | `tests/test_api/test_chat_schema.py:35-48` 覆盖附件 schema；`tests/test_agent/test_prompting.py:8-51` 覆盖 prompt 中包含附件、上下文、场景和诊断字段；全量 pytest 通过。 | 通过 |
| 标准 3：Agent LLM 输入由集中 prompt composer 构造，覆盖基础 system prompt、附件提示、疾病档案上下文、场景/工作流提示、工具错误提示，并统一进入裁剪预算。 | 新增 Agent runtime prompt composer 并由 LLM 节点统一调用。 | `app/agent/prompting.py:72-136` 集中组装 system prompt、附件、疾病档案上下文、场景/工作流和工具失败提示，并调用 `trim_messages`；`app/agent/nodes.py:208-226` LLM 节点改用 `build_prompt_messages()`。`app/agent/context.py:118-138` 渲染关键字段并排序。 | `tests/test_agent/test_prompting.py:8-64` 覆盖 composer 内容与最近一轮工具错误识别；全量 pytest 通过。 | 通过 |
| 标准 4：工具注册拆分为系统预加载工具和模型可见工具，`fetch_disease_profile_context` 默认不再模型可见。 | 预加载工具与模型工具分层。 | `app/tools/registry.py:8-38` 定义 `PRELOAD_TOOLS`、`MODEL_TOOLS`、`get_preload_tools()`、`get_model_tools()`；`app/agent/nodes.py:183` 默认给 LLM 绑定 `get_model_tools()`，而 `app/agent/nodes.py:243-246` 工具执行节点仍可执行全部工具。 | `tests/test_tools/test_registry.py:10-25` 覆盖 context 工具仅预加载可见、模型工具包含文档解析和文本生成、执行工具为超集；全量 pytest 通过。 | 通过 |
| 标准 5：SSE 工具事件和保存的 trace 只暴露脱敏摘要，不暴露 patient id、OSS object key、完整上下文 JSON 或完整报告字段。 | 工具事件和会话 trace 使用脱敏 payload，turn metadata 去除不应公开字段。 | `app/api/tool_events.py:9-59` 对 context、parse、generate 工具输入/输出生成摘要；`app/api/chat.py:75-88` 去除持久化 turn metadata 中的 `patient_id` 和附件 `object_key`；`app/api/chat.py:222-252` SSE 与 trace 写入使用 `sanitize_tool_input/output()`。 | `tests/test_api/test_tool_events.py:6-44` 覆盖不暴露 profile/patient id、object key、完整字段和生成文本；`tests/test_api/test_agent_sessions.py:73-100` 覆盖 SSE 与会话 trace 不含 `records/a.pdf`；全量 pytest 通过。 | 通过 |
| 标准 6：相同工具和参数在同一轮失败后，再次调用时被短路为友好错误，不重复触发底层工具。 | 新增工具失败检测和参数哈希短路逻辑。 | `app/agent/prompting.py:28-69` 识别最近一轮工具失败并生成参数哈希；`app/agent/nodes.py:243-296` 在工具节点阻断同工具同参数重复失败调用并返回友好 `ToolMessage`。 | `tests/test_agent/test_tool_error_recovery.py:9-85` 覆盖错误识别、跨轮停止、多个错误和同参重复失败不触发底层工具；全量 pytest 通过。 | 通过 |
| 标准 7：provider 层解析、生成、分类 prompt 被集中到 `app/prompts/provider.py`，原有结构化输出行为保持不变。 | provider prompt 常量集中管理，LLMService 引用常量。 | `app/prompts/provider.py:5-51` 集中定义 provider prompt 和版本；`app/providers/llm.py:13-22` 引入常量，`app/providers/llm.py:341-344` 用解析 prompt 构造 system prompt，`app/providers/llm.py:162-171` 用生成 prompt 构造任务 prompt，`app/providers/llm.py:205-209` 用分类 prompt。 | `tests/test_providers/test_llm_service.py` 既有和新增测试随全量 pytest 通过，覆盖结构化输出 response_format、解析 prompt、报告分析 prompt、生成返回行为。 | 通过 |
| 标准 8：新增/更新测试覆盖本次策略层优化，并通过全量 pytest。 | 文档列出新增 schema、prompting、registry、tool_events、tool_error、agent_sessions 等测试。 | 相关测试文件均存在并与代码变更对应：`tests/test_api/test_chat_schema.py`、`tests/test_agent/test_prompting.py`、`tests/test_tools/test_registry.py`、`tests/test_api/test_tool_events.py`、`tests/test_agent/test_tool_error_recovery.py`、`tests/test_api/test_agent_sessions.py`。 | 本次审查执行 `uv run python -m pytest -q`：`70 passed, 72 warnings in 1.59s`；执行 `git diff --check`：退出码 0，仅 LF/CRLF 警告。 | 通过 |

## 5. 文档与代码一致性

- 文档准确的地方：
  - 文档列出的主要变更文件均能在工作区找到对应实现或测试，尤其是 `app/schemas/chat.py`、`app/agent/prompting.py`、`app/agent/nodes.py`、`app/tools/registry.py`、`app/api/chat.py`、`app/api/tool_events.py`、`app/prompts/provider.py` 和相关测试。
  - 文档关于“模型默认只绑定 `parse_document` 和 `generate_medical_text`，`fetch_disease_profile_context` 继续由图的 context preload 机制强制调用”的描述与 `app/tools/registry.py:8-38`、`app/agent/nodes.py:183`、`app/agent/nodes.py:243-246` 一致。
  - 文档关于 SSE/trace 脱敏、附件提示、provider prompt 集中化、重复失败短路的描述均有代码和测试证据支撑。
- 文档过时或不准确的地方：
  - 未发现影响验收结论的过时或不准确描述。
- 文档遗漏：
  - 文档已记录 `ruff`、真实外部 LLM 联调和前端兼容验证未完成；本审查确认这些仍是未验证项。
- 代码中存在但文档未记录的变更：
  - `docs/agent-project-review-path.md` 是工作区未跟踪文档，追踪文档已明确其与本需求无关；本审查未将其纳入本需求交付范围。

## 6. 实现问题

- 问题：未发现阻塞实现问题。
- 严重级别：无。
- 文件 / 行号：不适用。
- 原因：各项验收标准均有直接代码路径和测试覆盖，且全量测试通过。
- 建议：保留当前实现，发布前补齐第 7 节的非阻塞验证项。

## 7. 测试与验证缺口

- 已有验证：
  - `uv run python -m pytest -q`：通过，`70 passed, 72 warnings in 1.59s`。
  - `git diff --check`：通过，退出码 0，仅 Git LF/CRLF 转换警告。
  - 单元/API 测试覆盖 typed metadata、附件 schema、prompt composer、工具分层、SSE/trace 脱敏、重复失败短路、provider prompt 行为。
- 缺失验证：
  - `uv run ruff check .`：未能运行，当前环境报错 `Failed to spawn: ruff` / `program not found`。
  - 未做真实 OpenAI 兼容服务联调，无法确认真实模型对合并 system prompt、附件提示、工具错误提示的端到端行为。
  - 未做前端兼容验证，无法确认前端是否依赖旧 `tool_call.input.object_key` 或完整 `tool_result.output` 展示调试信息。
- 无法确认的验证：
  - 真实外部 LLM 质量、线上网络/代理条件下的稳定性、前端展示兼容性。
- 建议补充：
  - 恢复或安装项目 lint 工具后执行 `uv run ruff check .`。
  - 以一份 PDF/图片报告和一个疾病档案上下文样例做端到端手工联调，确认模型仅在需要时调用 `parse_document`，且 SSE/会话详情无敏感 raw payload。
  - 与前端确认新工具事件摘要 payload，必要时增加前端适配或过渡字段。

## 8. 风险与后续事项

- 交付风险：
  - 风险主要集中在集成环境：前端若依赖旧 raw payload，需要同步改造；真实模型对 prompt 合并后的行为需通过外部联调确认。
  - `workflow/scenario/audience/urgency_level` 枚举严格化可能让传入错误枚举值的调用方更早暴露 422 校验错误，该行为符合需求但需要调用方知晓。
- 后续事项：
  - 补齐 lint、真实 LLM、前端兼容验证。
  - 如追踪文档所述，后续可将 `AGENT_PROMPT_VERSION`、`PROVIDER_PROMPT_VERSION` 写入会话或外部 trace，并补充 prompt 行为评测集。
- 是否需要更新需求实施追踪文档：
  - 需要；本报告生成后应仅回写追踪文档中的审查结论、审查状态和审查报告路径。

## 9. 最终建议

- 是否可以交付：可以有条件交付。当前代码实现满足追踪文档定义的 8 项验收标准，未发现阻塞问题。
- 交付前必须修复：无必须修复的阻塞项。
- 可后续优化：补齐 `ruff`、真实 LLM 联调、前端兼容验证；根据上线观测决定是否增加 prompt version trace 和 prompt 行为评测集。
