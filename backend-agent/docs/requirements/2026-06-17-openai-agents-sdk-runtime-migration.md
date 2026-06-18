# OpenAI Agents SDK Agent 工程重构

## 1. 元数据

- 状态：通过
- 负责人：Codex
- 开始日期：2026-06-17
- 最后更新日期：2026-06-17
- 相关请求：引入 OpenAI Agents SDK 并使用 OpenAI Agents SDK 重构当前项目中关于 agent 工程的部分
- 相关分支 / 提交 / PR：当前工作区未提交
- 需求达标审查报告：docs/requirements/2026-06-17-openai-agents-sdk-runtime-migration-review.md

## 2. 原始需求

- 用户原始诉求：引入 OpenAI Agents SDK，并用 OpenAI Agents SDK 重构 `backend-agent` 当前 agent 工程部分。
- 原始上下文：项目此前已从 LangChain / LangGraph 迁移到项目内轻量 `AgentRuntime`，当前用户要求进一步引入 OpenAI Agents SDK 作为 agent 编排框架。
- 后续补充：用户要求修改全局规则，使涉及重构时也需要调用 `requirement-doc-tracking`，并重新调用该 skill 生成本需求文档。

## 3. 摘要

本需求已将 `backend-agent` 的 Agent 对话编排从项目内手写模型循环迁移到 OpenAI Agents SDK。新的 `AgentRuntime` 作为项目适配层，内部使用 `Agent`、`Runner.run_streamed()`、`FunctionTool` 和 `AsyncSQLiteSession`；对外保持 `/api/v1/chat` SSE 事件和会话 API 契约基本不变。文档为事后补建，用于对齐已完成实现、验证记录和后续 Requirement Doc Review。

## 4. 背景和目标

- 业务背景：`backend-agent` 提供医疗 Agent 对话、疾病档案上下文预加载、文档解析工具、医疗文本生成工具、会话管理和 SSE 流式输出。
- 用户 / 问题陈述：希望当前 agent 工程使用 OpenAI Agents SDK，减少手写 agent 编排逻辑，并让会话历史和工具调用更贴近官方 SDK 模式。
- 目标：
  - 引入 OpenAI Agents SDK 依赖。
  - 用 OpenAI Agents SDK 重构 Agent 对话 runtime。
  - 保留现有 `/api/v1/chat` SSE 对外事件。
  - 保留疾病档案上下文预加载和工具调用能力。
  - 使用 SDK session 承担模型多轮历史。
  - 更新当前架构文档和测试。
- 成功标准：
  - 依赖和锁文件包含 OpenAI Agents SDK。
  - agent 对话路径不再使用手写 Chat Completions streaming tool-call 循环。
  - 模型可见工具转换为 Agents SDK `FunctionTool`。
  - SDK session 可作为模型历史来源，并支持会话删除清理。
  - 全量自动化测试通过。

## 5. 范围边界

### 本次做

- 将 `app.agent.runtime.AgentRuntime` 改造为 OpenAI Agents SDK 适配层。
- 将模型可见工具转换为 Agents SDK `FunctionTool`。
- 保留 `fetch_disease_profile_context` 作为 runtime 主动预加载工具，不暴露给模型主动选择。
- 新增 SDK session 配置 `AGENT_SESSION_DB_PATH`。
- 更新会话详情 fallback 和删除逻辑以支持 SDK session。
- 更新项目当前架构文档和 Review 路径文档。
- 更新测试覆盖 runtime stream、工具转换、会话 fallback 和 session 清理。
- 修改全局 `C:\Users\h4573\.codex\AGENTS.md`，增加重构触发 `requirement-doc-tracking` 的原则。

### 本次不做

- 不重写 `ProviderGateway`、`LLMService`、`DocumentParser`、OSS 存储或 MQ worker。
- 不把文档解析、文本生成、患者记忆抽取等 provider 链路迁移到 Responses API。
- 不新增多 Agent handoff、审批流、guardrail 或 hosted tools。
- 不改变前端依赖的 SSE 事件名。
- 不迁移旧历史数据到 SDK session 数据库。

### 假设

- Agent 对话主路径可以使用官方 OpenAI Responses API。
- 现有 `agent_sessions` / `agent_session_turns` 继续作为业务展示和审计历史来源。
- SDK session 主要服务于模型多轮上下文，不替代业务会话索引。
- OpenAI Agents SDK 的 tracing 配置可通过环境变量关闭敏感数据采集。

### 待确认问题

- 生产环境是否已准备真实官方 OpenAI API Key 和 Responses API 可用模型：TBD。
- 是否需要后续迁移 provider 层 Chat Completions 调用到 Responses API：TBD。
- 是否需要迁移旧 `agent_runtime_states.messages` 到 SDK session：TBD。

## 6. 验收标准

- [x] 标准 1：项目依赖包含 `openai-agents`，并升级到与 SDK 兼容的 `openai` / `fastapi` 版本。
- [x] 标准 2：Agent 对话 runtime 使用 OpenAI Agents SDK `Agent`、`Runner.run_streamed()` 和 `AsyncSQLiteSession`。
- [x] 标准 3：模型可见工具通过 Agents SDK `FunctionTool` 暴露，预加载上下文工具不模型可见。
- [x] 标准 4：`/api/v1/chat` SSE 事件保持 `session`、`token`、`tool_call`、`tool_result`、`done`、`error`。
- [x] 标准 5：会话详情在业务 turn 不存在时可从 SDK session fallback，删除会话时同步清理 SDK session。
- [x] 标准 6：当前架构文档反映 OpenAI Agents SDK 真实实现。
- [x] 标准 7：全量测试通过，应用入口可导入。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`
- 主要模块 / 文件：
  - `app/agent/runtime.py`：改为 OpenAI Agents SDK runtime 适配层。
  - `app/tools/registry.py`：新增 `FunctionTool` 转换和工具重复失败短路。
  - `app/agent/prompting.py`：新增 `build_agent_instructions()`。
  - `app/api/sessions.py`：新增 SDK session fallback 和删除清理。
  - `app/config.py`、`.env.example`：新增 `AGENT_SESSION_DB_PATH` 和 Agents tracing 配置示例。
  - `pyproject.toml`、`uv.lock`：新增 / 升级 SDK 相关依赖。
  - `docs/project-structure.md`、`docs/agent-project-review-path.md`：更新当前架构描述。
  - `C:\Users\h4573\.codex\AGENTS.md`：新增重构触发 requirement tracking 规则。
- API / 路由：
  - `/api/v1/chat`：内部 runtime 变更，对外 SSE 契约保持。
  - `/api/v1/sessions/{thread_id}`：业务历史缺失时 fallback 读取 SDK session。
  - `DELETE /api/v1/sessions/{thread_id}`：同步清理 SDK session。
- 数据库 / 表 / 字段：
  - 现有 `agent_sessions`、`agent_session_turns`、`agent_runtime_states` 保留。
  - 新增 SDK session SQLite 文件路径配置：`AGENT_SESSION_DB_PATH`，默认 `data/agent_sessions.db`。
- 配置：
  - `AGENT_SESSION_DB_PATH=data/agent_sessions.db`
  - `OPENAI_AGENTS_TRACE_INCLUDE_SENSITIVE_DATA=false`
- 定时任务 / MQ / 外部依赖：
  - MQ 任务处理不变。
  - 新增 Python 依赖 `openai-agents==0.17.5`。
  - 升级 `openai==2.42.0`、`fastapi==0.136.1`。

## 8. 实施方案

- 方案概述：保留项目对外 `AgentRuntime.stream()` 入口，内部使用 OpenAI Agents SDK 创建单 Agent，使用 `Runner.run_streamed()` 执行模型流式对话，使用 `AsyncSQLiteSession` 保存模型历史，将 SDK stream event 映射回项目已有 `AgentStreamEvent`。
- 关键设计决定：
  - 保持 `/api/v1/chat` 和 session API 外部契约稳定。
  - 保留业务会话表作为前端展示和审计来源，SDK session 只负责模型上下文。
  - `fetch_disease_profile_context` 继续由 runtime 根据 metadata 主动预加载，避免模型主动拉取患者上下文。
  - Provider 层维持现状，避免把 runtime 框架迁移扩大为全 AI provider 迁移。
- 替代方案与取舍：
  - 完全删除 `AgentRuntime` 外壳：未采用。保留外壳可减少 API 层和测试大面积变动。
  - 使用 OpenAI-compatible Chat Completions 适配模型：未采用。用户在计划阶段选择官方 Responses 路径。
  - SDK session 完全替代业务会话表：未采用。业务列表、trace 脱敏和前端展示仍依赖现有表。
- 风险：
  - 真实 Responses API 尚未端到端联调。
  - `openai-agents` 带来 FastAPI / Starlette 依赖升级，需要关注运行环境兼容。
  - SDK stream event 类型若后续版本变化，事件映射层需要同步更新。

## 9. 实施计划

1. 升级依赖并确认 OpenAI Agents SDK 本地 API 形态。
2. 重构 `AgentRuntime` 为 SDK 适配层。
3. 将工具注册转换为 SDK `FunctionTool`。
4. 接入 SDK session fallback 和删除清理。
5. 更新配置、测试和当前架构文档。
6. 运行定向测试、全量测试、导入检查和残留旧实现搜索。
7. 补建本需求文档并标记待审查。

## 10. 进度日志

- 2026-06-17：完成依赖升级，锁定 `openai-agents==0.17.5`、`openai==2.42.0`、`fastapi==0.136.1`。
- 2026-06-17：确认 SDK API 路径：`Agent`、`Runner.run_streamed()`、`FunctionTool`、`agents.extensions.memory.async_sqlite_session.AsyncSQLiteSession`。
- 2026-06-17：完成 `AgentRuntime` 重构，接入 SDK stream、SDK session 和 context preload。
- 2026-06-17：完成工具转换、会话 fallback、session 清理和配置更新。
- 2026-06-17：完成架构文档、Review 路径文档和测试更新。
- 2026-06-17：全量测试通过，应用入口导入通过。
- 2026-06-17：根据用户补充要求修改全局 `AGENTS.md`，补建本需求实施追踪文档。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `pyproject.toml`、`uv.lock` | 新增 OpenAI Agents SDK，升级 OpenAI / FastAPI 依赖 | 标准 1 |
| `app/agent/runtime.py` | 使用 `Agent`、`Runner.run_streamed()`、`AsyncSQLiteSession` 重构 runtime | 标准 2、4、5 |
| `app/tools/registry.py` | 新增 SDK `FunctionTool` 转换和同参失败短路 | 标准 3 |
| `app/agent/prompting.py` | 新增 `build_agent_instructions()` 供 SDK Agent 使用 | 标准 2 |
| `app/api/sessions.py` | 新增 SDK session fallback 和删除清理 | 标准 5 |
| `app/config.py`、`.env.example` | 新增 SDK session 和 tracing 相关配置 | 标准 1、5 |
| `docs/project-structure.md`、`docs/agent-project-review-path.md` | 更新当前架构和 Review 路径 | 标准 6 |
| `tests/test_agent/*`、`tests/test_tools/test_registry.py`、`tests/test_api/test_agent_sessions.py` | 覆盖 SDK runtime、工具转换和 session fallback | 标准 2、3、4、5、7 |
| `C:\Users\h4573\.codex\AGENTS.md` | 增加重构触发 requirement tracking 原则 | 用户补充要求 |

## 12. 验证与测试

- 计划检查：
  - 定向 agent / API / tools 测试。
  - 全量 pytest。
  - 应用入口导入和 SDK 版本确认。
  - 搜索旧 Chat Completions runtime 残留。
  - `git diff --check`。
- 已完成检查：
  - `uv run python -m pytest -q tests/test_tools/test_registry.py tests/test_agent tests/test_api/test_agent_sessions.py`：`36 passed, 6 warnings`
  - `uv run python -m pytest -q`：`81 passed, 6 warnings`
  - `uv run python -c "import app.main; import agents; print('import ok', agents.__version__)"`：输出 `import ok 0.17.5`
  - `git diff --check`：通过，仅有 Git LF/CRLF 提示
  - `rg -n "chat\.completions|AsyncOpenAI|OpenAI SDK chat\.completions|execute model tool calls|to_openai_tool|轻量 AgentRuntime" app tests docs\project-structure.md docs\agent-project-review-path.md -S`：无匹配
  - `uv run python -m pytest -q tests/test_agent tests/test_api/test_agent_sessions.py tests/test_tools/test_registry.py`：`35 passed, 6 warnings`
- 未运行 / 尚未验证：
  - 未使用真实官方 OpenAI API Key 对 `/api/v1/chat` 做 Responses API 端到端联调。
  - 未启动前端做浏览器级 SSE 验证。
  - 未验证生产环境 FastAPI / Starlette 升级后的部署兼容性。
  - 未运行 `ruff`，因为当前环境提示 `program not found`。
- 未验证原因：
  - 真实外部服务和前端环境不在本地自动化测试范围内。
  - 当前项目 dev 依赖未安装 `ruff` 可执行文件。

## 13. 风险与后续事项

- 剩余风险：
  - 真实 Responses API、模型参数和 SDK stream event 在生产环境中的行为需要联调确认。
  - SDK session 数据库与业务会话表分离，后续排查会话问题时需要同时查看两处存储。
  - 依赖升级带来的 Starlette / FastAPI 行为差异需要在集成环境观察。
- 后续事项：
  - 使用真实 OpenAI API Key 做 `/api/v1/chat` 普通问答、工具调用、错误处理三类联调。
  - 决定是否将 provider 层也迁移到 Responses API。
  - 评估是否把 `ruff` 加入 dev 依赖或调整验证命令。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：
  - Agent 对话仍通过 `/api/v1/chat` SSE 输出 token、工具调用、工具结果和完成事件。
  - 疾病档案上下文仍根据 metadata 自动预加载。
  - 文档解析和医疗文本生成仍作为模型可见工具。
  - 会话列表、详情、turn 和 trace 仍由现有业务表支持。
- 已交付的技术实现：
  - Agent runtime 使用 OpenAI Agents SDK。
  - 模型历史使用 SDK `AsyncSQLiteSession`。
  - 工具通过 Agents SDK `FunctionTool` 暴露。
  - 当前架构文档已同步到实际实现。
- 与原始计划的差异：
  - 需求文档未在第一轮实现前创建；本次根据用户补充要求事后补建，并同步修改全局规则避免后续重构漏用。
  - `fetch_disease_profile_context` 仍使用项目内 `tool_runner` 执行预加载，不作为模型可见 SDK tool。
- 验收标准满足情况：
  - 标准 1 到标准 7 均已满足，证据见验证与测试章节。
- 证据与验证：
  - 全量 pytest 通过。
  - 应用入口导入和 SDK 版本确认通过。
  - 当前 agent runtime 旧 Chat Completions 手写循环关键字搜索无匹配。
- 未验证事项：
  - 真实 OpenAI Responses API 端到端联调。
  - 前端浏览器级 SSE 验证。
  - 生产部署兼容性验证。
- 后续工作：
  - 做真实外部服务联调。
  - 进行独立 Requirement Doc Review。

## 15. Requirement Doc Review 交接

- 审查状态：已审查
- 建议审查报告路径：`docs/requirements/2026-06-17-openai-agents-sdk-runtime-migration-review.md`
- 审查重点：
  - 确认 agent 对话主路径是否真实使用 OpenAI Agents SDK。
  - 确认 SSE 事件、会话 API、工具调用和上下文预加载行为是否保持兼容。
  - 确认 SDK session 与业务会话表的职责边界是否清晰。
  - 确认依赖升级对现有 FastAPI、Starlette、测试和部署无不可接受影响。
- 已知需要审查的问题：
  - 真实 OpenAI Responses API 尚未端到端联调。
  - 需求文档为事后补建，不具备实施前创建记录。
