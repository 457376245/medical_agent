# Agent Runtime 去 LangChain / LangGraph 迁移

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-31
- 最后更新日期：2026-05-31
- 相关请求：根据刚刚的方案迁移 langchain / langgraph；后续要求使用 requirement-doc-tracking 将改动生成文档
- 相关分支 / 提交 / PR：当前工作区未提交
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：将当前 `backend-agent` 中依赖 LangChain / LangGraph 的 Agent 实现，迁移为更轻量的 Agent 模式。
- 原始上下文：项目此前使用 LangGraph 构建 `context_preload -> agent -> tools -> context_sync -> agent` 状态图，并依赖 LangChain 消息、工具和 `langgraph-checkpoint-sqlite`。
- 后续补充：用户询问是否需要引入 Pydantic AI Agent 框架。当前结论是暂不引入 Pydantic AI Agent runtime，保留项目内 `AgentRuntime`，后续可在 Provider 层结构化输出场景单独试点 Pydantic AI。

## 3. 摘要

本需求已将 `backend-agent` 的 Agent 对话运行时从 LangChain / LangGraph 迁移为项目内轻量 `AgentRuntime`。新的 runtime 直接使用 OpenAI 官方 Python SDK 调用 OpenAI 兼容 Chat Completions，保留原有 SSE 对外事件、疾病档案上下文预加载、工具调用、工具失败短路、会话索引和 trace 脱敏能力。旧的 LangGraph 图、节点和 checkpoint 封装已删除，依赖和锁文件中已移除 LangChain / LangGraph 相关包。

## 4. 背景和目标

- 业务背景：`backend-agent` 是医疗智能体后端服务，提供对话式医疗助手、疾病档案上下文读取、文档解析工具、医疗文本生成工具和 MQ 任务处理。
- 用户 / 问题陈述：当前项目使用 LangChain / LangGraph 生态偏重，且在 Python 3.14 下存在第三方兼容警告。用户希望按轻量 Agent 方案迁移。
- 目标：
  - 移除业务运行路径中的 LangChain / LangGraph 依赖。
  - 保持 `/api/v1/chat` SSE 契约和会话 API 行为不变。
  - 保留现有工具、Provider、MQ 和 Java 上下文 API 契约。
  - 使用项目内可控的轻量 Agent runtime。
- 成功标准：
  - 代码、依赖、锁文件中不再使用 LangChain / LangGraph。
  - 全量测试通过。
  - 应用入口可导入。
  - 当前虚拟环境中旧 Agent 包不可导入。

## 5. 范围边界

### 本次做

- 替换 LangGraph 状态图为 `app.agent.runtime.AgentRuntime`。
- 替换 LangChain 消息类型为项目内 `AgentMessage` / `AgentToolCall`。
- 替换 LangChain 工具装饰器和 `ToolNode` 为项目内 `ToolSpec` / `tool_runner.py`。
- 用 `agent_runtime_states` 表保存每个 `thread_id` 的 Agent 运行态。
- 更新 `chat.py` 和 `sessions.py` 以消费轻量 runtime。
- 从 `pyproject.toml`、`uv.lock` 和本地 `.venv` 中移除 LangChain / LangGraph 相关依赖。
- 更新当前架构文档和测试。

### 本次不做

- 不重写 `ProviderGateway`、`LLMService.parse()`、`LLMService.generate()`、`DocumentParser`、`OSSStorageService`。
- 不改 MQ routing key、任务 payload、Java 后端接口、OSS 配置。
- 不引入 Pydantic AI Agent 框架。
- 不做旧 `data/checkpoints.db` 数据迁移；新的运行态写入 `data/memory.db` 中的 `agent_runtime_states`。
- 不调整 prompt 策略和医疗回答效果。

### 假设

- 前端只依赖现有 SSE 事件名和 payload，不依赖 LangGraph 内部事件结构。
- 历史 indexed session / turn 是主要会话恢复来源；旧 checkpoint 不作为必须迁移的数据源。
- OpenAI 兼容服务支持 Chat Completions streaming 和 function tool calling。

### 待确认问题

- 生产环境是否需要清理旧 `data/checkpoints.db` 文件：TBD。
- 是否需要在 Provider 层引入 Pydantic AI 的 `output_type` 改造结构化解析：TBD。

## 6. 验收标准

- [x] 标准 1：`app/`、`tests/`、`pyproject.toml`、`uv.lock`、`.env.example` 和当前架构文档中不再引用 LangChain / LangGraph 运行依赖。
- [x] 标准 2：`/api/v1/chat` 仍返回 `session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件。
- [x] 标准 3：疾病档案上下文仍能根据 metadata 自动预加载，并写入 runtime state。
- [x] 标准 4：工具调用、工具结果脱敏、重复失败短路行为保留。
- [x] 标准 5：会话列表、详情、turn、trace 和 runtime state 使用 SQLite 保存。
- [x] 标准 6：全量测试通过，应用入口可导入，旧 Agent 包在当前虚拟环境中不可导入。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`
- 主要模块 / 文件：
  - 新增：`app/agent/runtime.py`、`app/agent/messages.py`、`app/agent/events.py`、`app/agent/tool_runner.py`
  - 删除：`app/agent/graph.py`、`app/agent/nodes.py`、`app/memory/checkpointer.py`
  - 更新：`app/api/chat.py`、`app/api/sessions.py`、`app/agent/prompting.py`、`app/agent/state.py`、`app/memory/store.py`、`app/tools/registry.py`、`app/tools/*.py`、`app/main.py`、`app/config.py`、`app/schemas/chat.py`
  - 更新测试：`tests/test_agent/*`、`tests/test_api/test_agent_sessions.py`、`tests/test_api/test_chat_schema.py`
  - 更新文档：`docs/project-structure.md`、`docs/agent-project-review-path.md`
- API / 路由：
  - `/api/v1/chat`：内部调用 runtime 变化，对外 SSE 契约保持。
  - `/api/v1/sessions`：fallback 恢复来源由旧 graph state 改为 runtime state。
- 数据库 / 表 / 字段：
  - 新增表：`agent_runtime_states`
  - 表字段：`thread_id`、`messages`、`active_context_signature`、`active_context_bundle`、`active_context_status`、`updated_at`
  - 删除会话时同步删除 `agent_runtime_states` 中对应记录。
- 配置：
  - `.env.example` 删除 LangSmith / LangChain 配置段。
  - `.env.example` 删除 `CHECKPOINT_DB_PATH`。
  - `config.py` 删除 `CHECKPOINT_DB_PATH` 和 LangChain 相关配置读取。
- 定时任务 / MQ / 外部依赖：
  - MQ 任务处理逻辑不变。
  - 外部 LLM 依赖仍为 OpenAI 兼容服务。
  - Python 依赖中移除 `langchain`、`langchain-openai`、`langgraph`、`langgraph-checkpoint-sqlite`。

## 8. 实施方案

- 方案概述：用项目内显式 `AgentRuntime` 替代 LangGraph 状态图。runtime 自行管理每轮对话的状态加载、上下文预加载、prompt 组装、OpenAI SDK streaming、工具执行、工具循环上限和状态持久化。
- 关键设计决定：
  - 不引入新的 Agent 框架，避免从 LangGraph 迁移到另一套 runtime 抽象。
  - 工具层用 `ToolSpec` 生成 OpenAI function tool schema，工具函数保持普通 Python 函数。
  - runtime state 合并到现有 `SqliteMemoryStore`，减少 `checkpoints.db` 和 `memory.db` 两套状态存储。
  - `chat.py` 只消费 `AgentStreamEvent`，不再理解底层模型 SDK 的 chunk 结构。
- 替代方案与取舍：
  - 使用 Pydantic AI Agent：暂不采用。它能增强结构化输出，但会再次引入 Agent 事件和运行态抽象，当前收益不足。
  - 使用 OpenAI Agents SDK：暂不采用。当前没有多 Agent handoff 或审批流需求。
  - 保留 LangGraph：不采用。不能满足本次移除 LangChain / LangGraph 的目标。
- 风险：
  - Chat Completions streaming tool call 的兼容性依赖当前 OpenAI 兼容服务实现。
  - 旧 checkpoint 数据不迁移，历史会话若只存在于 checkpoint 中将无法通过 fallback 恢复。
  - 新 runtime 是项目内代码，需要后续继续用测试覆盖工具循环边界。

## 9. 实施计划

1. 核查当前 LangChain / LangGraph 使用点和测试覆盖。
2. 新增框架无关的 Agent 消息、事件、工具和状态模型。
3. 实现 `AgentRuntime`，接入 OpenAI SDK、上下文预加载、工具循环和状态存储。
4. 修改 `chat.py`、`sessions.py`、`main.py` 使用新 runtime。
5. 移除旧图、节点、checkpoint 代码和依赖。
6. 更新测试、锁文件、当前架构文档并运行验证。

## 10. 进度日志

- 2026-05-31：完成 LangChain / LangGraph 使用点核查，确认依赖集中在 Agent 图、节点、工具、消息和 checkpoint。
- 2026-05-31：新增 `AgentRuntime`、`AgentMessage`、`AgentStreamEvent`、`ToolSpec`、`tool_runner`。
- 2026-05-31：`chat.py` 从 `graph.astream_events()` 切换到 `runtime.stream()`。
- 2026-05-31：`SqliteMemoryStore` 新增 `agent_runtime_states` 表读写，并在删除会话时清理 runtime state。
- 2026-05-31：删除旧 `graph.py`、`nodes.py`、`checkpointer.py`，移除 LangChain / LangGraph 依赖并执行 `uv sync`。
- 2026-05-31：更新测试、当前架构文档和 review 路径文档。
- 2026-05-31：补建本需求实施追踪文档，状态标记为待审查。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `app/agent/runtime.py` | 新增轻量 Agent 运行循环，负责状态加载、上下文预加载、OpenAI SDK streaming、工具循环和状态保存 | 标准 1、2、3、4、5 |
| `app/agent/messages.py` | 新增框架无关消息和工具调用模型，并提供 OpenAI message 转换 | 标准 1、2 |
| `app/agent/events.py` | 新增 runtime 对外流式事件模型 | 标准 2 |
| `app/agent/tool_runner.py` | 新增工具执行和重复失败短路逻辑 | 标准 4 |
| `app/agent/prompting.py` | 移除 LangChain message / trim 依赖，改用项目消息模型 | 标准 1、4 |
| `app/agent/state.py` | 从 LangGraph TypedDict 改为 `AgentRuntimeState` | 标准 1、5 |
| `app/api/chat.py` | 从 LangGraph 事件消费改为 `AgentRuntime.stream()`，保持 SSE 输出和持久化 | 标准 2、5 |
| `app/api/sessions.py` | 会话详情 fallback 改为读取 runtime state | 标准 5 |
| `app/memory/store.py` | 新增 `agent_runtime_states` schema 和读写方法 | 标准 5 |
| `app/tools/registry.py` | 从 LangChain `BaseTool` 改为 `ToolSpec` 和 OpenAI tool schema | 标准 1、4 |
| `app/tools/*.py` | 移除 LangChain `@tool` 装饰器，保留普通工具函数 | 标准 1、4 |
| `app/main.py` | 初始化 `AgentRuntime`，移除 checkpointer / graph 初始化和 LangSmith 配置日志 | 标准 1、5 |
| `app/config.py`、`.env.example` | 移除 checkpoint 和 LangChain 相关配置 | 标准 1 |
| `pyproject.toml`、`uv.lock` | 移除 LangChain / LangGraph 依赖 | 标准 1、6 |
| `tests/test_agent/*`、`tests/test_api/*` | 更新测试以覆盖新 runtime、消息裁剪、工具失败短路、SSE 会话持久化 | 标准 2、3、4、5、6 |
| `docs/project-structure.md`、`docs/agent-project-review-path.md` | 更新当前架构说明，避免继续指向旧框架 | 标准 1 |

## 12. 验证与测试

- 计划检查：
  - 全量 pytest。
  - 应用入口导入。
  - 全仓当前代码和当前架构文档范围内搜索旧框架标识。
  - 当前虚拟环境确认旧包不可导入。
  - `git diff --check`。
- 已完成检查：
  - `uv run python -m pytest -q`：`79 passed, 71 warnings in 1.80s`
  - `uv run python -c "import app.main; print('import ok')"`：输出 `import ok`
  - `uv run python -c "import importlib.util; assert importlib.util.find_spec('langchain') is None; assert importlib.util.find_spec('langgraph') is None; print('old agent packages absent')"`：输出 `old agent packages absent`
  - `rg -n "langchain|langgraph|LANGCHAIN|LangGraph|LangChain|CHECKPOINT_DB_PATH|checkpointer|agent_graph|to_graph" -S app tests pyproject.toml uv.lock .env.example docs\project-structure.md docs\agent-project-review-path.md`：无匹配
  - `git diff --check`：通过，仅有 Git LF/CRLF 提示
  - `uv sync`：卸载了 `langchain`、`langchain-core`、`langchain-openai`、`langgraph`、`langgraph-checkpoint`、`langgraph-checkpoint-sqlite`、`langgraph-prebuilt`、`langgraph-sdk`、`langsmith` 等旧依赖
- 未运行 / 尚未验证：
  - 未连接真实 OpenAI 兼容服务做端到端流式工具调用联调。
  - 未启动前端做浏览器级 SSE 验证。
  - 未验证生产数据目录中旧 `checkpoints.db` 的清理策略。
- 未验证原因：
  - 当前任务聚焦代码迁移和自动化回归；真实外部服务、前端和生产数据需要对应环境。

## 13. 风险与后续事项

- 剩余风险：
  - OpenAI 兼容服务若不完整支持 streaming tool call delta，runtime 需加兼容分支。
  - 旧 checkpoint 不迁移可能影响只存在于 checkpoint 的历史会话 fallback。
  - Pydantic AI 未引入，结构化输出稳定性仍沿用现有 Provider 层实现。
- 后续事项：
  - 使用真实 OpenAI 兼容服务对 `/api/v1/chat` 做一次附件解析工具调用联调。
  - 评估是否在 `LLMService.parse()` 局部引入 Pydantic AI `output_type`。
  - 明确生产环境旧 `data/checkpoints.db` 是否保留、归档或删除。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：
  - Agent 对话仍支持 SSE 流式返回、疾病档案上下文预加载、文档解析工具、文本生成工具、会话列表和会话详情。
- 已交付的技术实现：
  - Agent 编排由项目内 `AgentRuntime` 承担，底层模型调用使用 OpenAI 官方 Python SDK。
  - 工具 schema 由 `ToolSpec` 输出为 OpenAI function tool schema。
  - runtime state 存储在 `agent_runtime_states`。
  - 旧 LangChain / LangGraph 代码和依赖已移除。
- 与原始计划的差异：
  - 原先讨论过 Pydantic AI + 官方 SDK 的方向；实际交付选择官方 SDK + 项目内 runtime，Pydantic AI Agent 暂不引入。
  - 未迁移旧 checkpoint 数据。
- 验收标准满足情况：
  - 标准 1 到标准 6 均已满足，证据见验证与测试章节。
- 证据与验证：
  - 全量 pytest 通过。
  - 应用入口导入通过。
  - 旧依赖包不可导入。
  - 当前代码、测试、依赖、锁文件、环境模板和当前架构文档范围搜索无旧框架标识。
- 未验证事项：
  - 真实外部 LLM 服务流式工具调用联调。
  - 前端浏览器端 SSE 验证。
  - 生产旧 checkpoint 文件清理。
- 后续工作：
  - 做一次端到端联调。
  - 评估 Provider 层结构化输出是否引入 Pydantic AI。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-05-31-agent-runtime-langchain-langgraph-migration-review.md`
- 审查重点：
  - 确认 LangChain / LangGraph 代码和依赖是否彻底移除。
  - 确认 SSE、工具调用、上下文预加载和会话持久化行为是否与迁移前等价。
  - 确认新增 `agent_runtime_states` 存储和删除逻辑是否满足会话恢复需求。
  - 确认不引入 Pydantic AI Agent 的取舍是否与当前需求一致。
- 已知需要审查的问题：
  - 真实 OpenAI 兼容服务 streaming tool call 尚未联调。
  - 旧 checkpoint 数据未迁移。
