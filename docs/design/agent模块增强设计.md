# Agent 模块增强设计

## 元数据

- 状态：进行中
- 创建日期：2026-04-18
- 最后更新日期：2026-04-19

## 摘要

对 `backend-agent` Python 服务进行垂直方向增强，提升报告解析质量和 AI 对话精准度。已落地十项能力：场景模板注入、结构化输出强制、第一梯队快速修复、全模块代码精简、System Prompt 深度优化、Context 感知增强提示、对话历史窗口管理、指标异常根因推理场景、工具调用失败恢复、Agent 回答质量防护。

---

## 一、场景模板注入（已完成）

### 目标

根据用户当前操作上下文自动切换 AI 对话的引导模式，使回答更有针对性。

### 设计

在 `call_llm` 节点中读取 `state.metadata.scenario`，匹配到对应模板后作为额外 SystemMessage 注入到 LLM 调用的消息序列中。不改变 graph 结构。

消息注入顺序：

```
[SystemMessage: SYSTEM_MEDICAL_ASSISTANT]    ← 通用角色
[SystemMessage: context_message]             ← 患者/记录上下文（已有）
[SystemMessage: scenario_template]           ← 场景引导（新增）
[HumanMessage / AIMessage / ...]             ← 对话历史
```

### 支持的场景

| 场景键 | 名称 | 聚焦内容 |
|--------|------|----------|
| `report_interpretation` | 报告解读 | 异常指标解读、联动分析引用、后续建议 |
| `medication_review` | 用药审查 | 药物相互作用、指标与药物关联、剂量评估 |
| `clinical_summary` | 临床摘要 | 结构化摘要、趋势变化、状态评估 |

### 前端集成

`toRequestMetadata()` 自动根据上下文设置 scenario：选择了记录时 → `report_interpretation`，未选记录时 → 不设置（fallback 到通用模式）。

### 产出文件

| 文件 | 状态 | 说明 |
|------|------|------|
| `backend-agent/app/prompts/templates.py` | 重写 | 3 个场景引导型 system prompt + `get_scenario_prompt()` |
| `backend-agent/app/agent/nodes.py` | 修改 | `call_llm` 中注入 scenario template |
| `frontend/src/components/agent/types.ts` | 修改 | `AgentRequestMetadata` 新增 `scenario` 字段 |
| `frontend/src/components/agent/agent-utils.ts` | 修改 | `toRequestMetadata()` 自动设置 scenario |

---

## 二、结构化输出强制（已完成）

### 目标

通过 OpenAI JSON Schema 模式约束 LLM 解析输出，提升字段完整性和 `standardCode` 填充率，减少 JSON 格式层面的解析失败。

### 背景问题

| 问题 | 改动前表现 |
|------|------------|
| JSON 格式不保证 | LLM 偶发 markdown 包裹、漏逗号、截断，需复杂后处理 |
| `standardCode` 缺失 | 字段定义为 `optional`，LLM 经常不填，降低归一化命中率 |
| 字段命名不一致 | 偶尔写成 `reference_range` 等变体 |

### 设计

在 `_send_chat_completion_request` 的 payload 中注入 `response_format` 参数，使用 `json_schema` 类型约束输出：

- `standardCode` 标记为 `required`（LLM 必须填值或显式填 null）
- 所有字段名由 schema 固定，消除命名变体
- `strict: true` 确保 token 生成层面的合规

### 降级策略

通过环境变量 `OPENAI_STRUCTURED_OUTPUT` 控制（默认 `true`）：

- `true`：注入 `response_format`，system prompt 省略 JSON 格式指令
- `false`：不注入 `response_format`，system prompt 保留 "Return only valid JSON" 等指令

降级时无需改代码，`_load_json_object()` 的 markdown 栅栏处理和 `{...}` 提取作为 fallback 保留。

### 产出文件

| 文件 | 状态 | 说明 |
|------|------|------|
| `backend-agent/app/providers/llm.py` | 修改 | 新增 `_PARSE_OUTPUT_SCHEMA` 常量、`_use_structured_output` 属性，`_invoke_text` 接受 `response_format`，`_invoke_parse_content` 按开关注入 |
| `backend-agent/tests/test_providers/test_llm_service.py` | 修改 | 新增 2 个测试（启用/禁用验证） |

### 测试覆盖

| 测试 | 验证内容 |
|------|----------|
| `test_parse_injects_response_format_when_structured_output_enabled` | 启用时 payload 包含正确的 `response_format` |
| `test_parse_omits_response_format_when_structured_output_disabled` | 禁用时 payload 不含 `response_format`，system prompt 含格式指令 |

---

## 三、第一梯队快速修复（已完成）

### 目标

修复 agent 模块中影响可靠性和可维护性的低风险高收益问题。

### 3.1 MAX_TOOL_ROUNDS 工具调用轮次上限

**问题**：`should_continue` 仅检查最后一条消息是否含 `tool_calls`，若 LLM 持续生成工具调用则无法终止，可能导致无限循环。

**方案**：从最后一条 HumanMessage 向后计数 AIMessage（含 tool_calls）轮数，超过 `MAX_TOOL_ROUNDS`（环境变量，默认 10）时强制返回 `"end"`。

**产出文件**：

| 文件 | 说明 |
|------|------|
| `backend-agent/app/agent/nodes.py` | `should_continue` 新增轮次计数逻辑 |
| `backend-agent/app/config.py` | 新增 `MAX_TOOL_ROUNDS` 配置 |

### 3.2 工具注册表死代码清理

**问题**：`registry.py` 中存在 `CONSULTATION_TOOLS`、`REPORT_TOOLS` 和场景映射等从未使用的代码。

**方案**：删除未使用的分类映射，`get_tools()` 简化为无参数调用，直接返回 `list(ALL_TOOLS)`。

**产出文件**：

| 文件 | 说明 |
|------|------|
| `backend-agent/app/tools/registry.py` | 删除死代码，简化 API |

### 3.3 datetime.utcnow() 修复

**问题**：`datetime.utcnow()` 在 Python 3.12 已弃用，返回 naive datetime，存在时区歧义。

**方案**：全局替换为 `datetime.now(timezone.utc)`。

**产出文件**：

| 文件 | 说明 |
|------|------|
| `backend-agent/app/api/chat.py` | 替换 2 处 |
| `backend-agent/app/memory/store.py` | 替换 2 处 |
| `backend-agent/app/memory/models.py` | default_factory 中已使用正确写法 |

### 3.4 SSE 心跳保活

**问题**：LLM 执行工具调用时可能长时间无输出，反向代理（Nginx 等）会因 idle timeout 断开 SSE 连接。

**方案**：将 `async for` 改为 `asyncio.wait` + Future 模式，15 秒无事件时发送 `: keepalive\n\n` SSE 注释帧。使用 `_STREAM_END` 哨兵 + `_next_event()` 辅助函数安全处理 `StopAsyncIteration`（规避 PEP 479 RuntimeError）。

**产出文件**：

| 文件 | 说明 |
|------|------|
| `backend-agent/app/api/chat.py` | 重构 `event_stream()` 流式逻辑，新增 `_STREAM_END`、`_next_event()`、`_SSE_KEEPALIVE_INTERVAL` |

---

## 四、全模块代码精简（已完成）

### 目标

删除 `backend-agent` 中所有已废弃、未使用或计划但未实现的代码，降低维护负担。

### 精简统计

- **净删除行数**：约 260 行
- **涉及文件**：10 个（1 个完整删除，9 个局部清理）
- **测试结果**：精简后 25/25 测试通过

### 详细变更

| 文件 | 变更 | 说明 |
|------|------|------|
| `app/schemas/task.py` | 整文件删除 | 与 `main.py` 重复的 TaskRequest schema |
| `app/schemas/chat.py` | 删除 `ChatEvent`、`SessionInfo` | 未被任何模块导入 |
| `app/providers/llm.py` | 删除 `GenerateAgentOutput` 类 | Agent 对话链未使用 |
| `app/config.py` | 删除 12 个常量 | `OPENAI_PARSE_MODEL`、`OPENAI_GENERATE_MODEL`、`OPENAI_VISION_MODEL`、`OPENAI_FALLBACK_MODEL`、`OPENAI_TEMPERATURE`、`OPENAI_TRUST_ENV`、`OPENAI_PROXY`、`OPENAI_RETRY_WITH_ENV_PROXY`、`SESSION_IDLE_TIMEOUT_SECONDS`、`LANGCHAIN_ENDPOINT`、`MAX_DOWNLOAD_BYTES`、`MAX_PDF_TEXT_CHARS` |
| `app/memory/models.py` | 删除 3 个模型 | `ConversationSummary`、`PatientContext`、`MedicalFact`（长期记忆功能未实现） |
| `app/memory/store.py` | 删除 6 个方法 + 3 张 SQL 表 | 对应上述 3 个模型的 CRUD 和持久化 |
| `app/providers/gateway.py` | 删除测试模拟钩子 | `simulate` 参数检测（timeout/external_error/biz_error） |
| `app/services/disease_profile_context.py` | 删除 tuple 防御代码 | `_http_get_json` 始终返回 `dict`，无需 tuple 解包 |
| `app/api/chat.py` | 内联 `_derive_context_signature` | 移除单行包装函数，直接调用 `context_signature_from_metadata` |
| `tests/test_agent/test_context_flow.py` | 修复 mock 类型 | `fake_get_json` 返回类型从 `tuple[int, dict]` 改为 `dict[str, Any]` |

---

## 五、System Prompt 深度优化（已完成）

### 目标

增强 `SYSTEM_MEDICAL_ASSISTANT` 提示词的引导精度，减少 LLM 不必要的工具调用和泛泛回答。

### 设计

保留原有 5 条核心原则，追加三个新段落：

| 段落 | 内容 |
|------|------|
| 工具使用策略 | 明确 3 个工具的使用时机和前提条件，指示对话类问题不调用工具 |
| 上下文数据使用规范 | 引用关键字段具体数值、结合趋势分析、说明数据局限性 |
| 回答结构与推理规范 | 三段式结构（发现→解读→建议）、异常优先、关联分析、紧急程度分级 |

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/prompts/system.py` | 扩展 `SYSTEM_MEDICAL_ASSISTANT` 常量 |
| `backend-agent/tests/test_prompts/test_system_prompt.py` | 新增 3 个验证测试 |

---

## 六、Context 感知增强提示（已完成）

### 目标

根据上下文数据的实际状态（解析进度、字段完整度、趋势可用性）动态生成 LLM 引导指令，替代原有静态数据格式化。

### 新增引导规则

| 规则 | 触发条件 | 引导内容 |
|------|----------|----------|
| 解析中 | `parse_status == "pending"` | 告知用户数据不完整，需等待解析 |
| 解析失败 | `parse_status == "failed"` | 基于有限信息分析，建议重新上传 |
| 无选中报告 | `selected_record` 为空且有档案 | 提供档案概况，提示选择报告 |
| 有关键字段 | key_fields 渲染非空 | 引用具体数值，对比参考范围 |
| 有趋势数据 | trend_summary 渲染非空 | 分析变化方向，结合趋势讨论 |

### partial 状态增强

检查具体缺失项（报告分析、关键指标、趋势数据），生成精确的缺失信息列表取代原来的泛化提示。

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/agent/context.py` | 重写 `build_context_system_message()`，新增动态引导逻辑 |
| `backend-agent/tests/test_agent/test_context_guidance.py` | 新增 8 个测试 |

---

## 七、对话历史窗口管理（已完成）

### 目标

防止长对话超出 LLM 上下文窗口限制，通过 token 预算自动裁剪历史消息。

### 设计

在 `call_llm` 中使用 `langchain_core.messages.trim_messages`，按近似 token 计数裁剪对话历史：

- `strategy="last"`：保留最近消息
- `include_system=True`：保留 SystemMessage
- `start_on="human"`：裁剪后首条非系统消息为 HumanMessage，避免孤立 ToolMessage
- 默认 token 预算 100,000（环境变量 `CONVERSATION_WINDOW_MAX_TOKENS` 可调）

裁剪仅作用于 `state["messages"]` 中的对话历史，`call_llm` 注入的 system prompt、context message、scenario template 不受影响。

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/config.py` | 新增 `CONVERSATION_WINDOW_MAX_TOKENS` 配置 |
| `backend-agent/app/agent/nodes.py` | `call_llm` 中新增 `trim_messages` 裁剪逻辑 |
| `backend-agent/tests/test_agent/test_message_trimming.py` | 新增 4 个测试 |

---

## 八、指标异常根因推理场景（已完成）

### 目标

新增 `abnormal_reasoning` 场景模板，为多指标异常的因果推理提供结构化框架引导。

### 设计

在 `_SCENARIO_REGISTRY` 中注册第四个场景模板，提供 7 步推理框架：列出异常→识别关联模式→提出根因假设→评估证据→建议确认检查→申明辅助参考性质→处理孤立异常。

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/prompts/templates.py` | 新增 `ABNORMAL_REASONING` 常量及注册 |
| `backend-agent/tests/test_prompts/test_templates.py` | 新增 4 个测试 |

---

## 九、工具调用失败恢复（已完成）

### 目标

当工具返回错误时，引导 LLM 生成用户友好的错误说明，而非重复重试或透传技术错误。

### 设计

双层防护：

1. **System Prompt 静态引导**：追加「工具调用错误处理」段落，指导 LLM 不重复重试、转化为用户友好说明、给出替代建议
2. **动态错误检测**：`call_llm` 中新增 `_detect_recent_tool_errors()` 扫描最近 ToolMessage 的 `Error:` 前缀，检测到错误时注入 `[注意]` SystemMessage 明确指示 LLM 不重试

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/prompts/system.py` | 追加错误处理引导段落 |
| `backend-agent/app/agent/nodes.py` | 新增 `_detect_recent_tool_errors()` + 错误提示注入 |
| `backend-agent/tests/test_agent/test_tool_error_recovery.py` | 新增 4 个测试 |
| `backend-agent/tests/test_prompts/test_system_prompt.py` | 新增 1 个测试 |

---

## 十、Agent 回答质量防护（已完成）

### 目标

防止 LLM 返回空内容（非工具调用场景），提供最基本的质量兜底。

### 设计

在 `call_llm` 中，LLM 调用后检查响应：若 `content` 为空且无 `tool_calls`，追加 nudge 提示并重试一次。最多重试一次，不会无限循环。

### 产出文件

| 文件 | 说明 |
|------|------|
| `backend-agent/app/agent/nodes.py` | `call_llm` 中新增空回复检测 + 单次重试 |

---

## 后续计划

| 方向 | 优先级 | 说明 |
|------|--------|------|
| 纵向趋势智能解读 | 中 | 跨报告趋势分析工具，识别指标变化方向和速率（需 Java 端新增聚合 API） |
| 多报告智能对比 | 中 | 对比两份报告的差异，关注新增异常和趋势变化（需 Java 端支持多记录上下文） |
| 医学知识 RAG | 低 | 接入医学知识库作为工具，减少 LLM 幻觉 |
