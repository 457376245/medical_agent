# backend-agent 项目 Review 路径

> 更新日期：2026-05-31
> 适用范围：`backend-agent` 当前 Python FastAPI + 轻量 AgentRuntime 服务

## Review 前提

- 当前重点是 Python 后端 `backend-agent`。
- 需要掌握的主线是：服务启动、Agent 对话、会话记忆、工具调用、Provider 调用、MQ 任务处理、测试验证。
- 当前 Agent 编排已迁移为项目内轻量 runtime，不再依赖外部重型 Agent 框架。

## 技术地图

| 方向 | 当前实现 |
| --- | --- |
| Web 服务 | FastAPI + Uvicorn |
| Agent 编排 | `app.agent.runtime.AgentRuntime` |
| LLM 接入 | OpenAI 官方 Python SDK 与 OpenAI 兼容 Chat Completions |
| 流式输出 | Server-Sent Events |
| 会话与运行态 | `SqliteMemoryStore` + `agent_runtime_states` |
| 异步任务 | RabbitMQ + `aio-pika` |
| 文件存储 | 阿里云 OSS |
| 文档解析 | `pypdf` 文本提取 + PyMuPDF 图片渲染 + Vision LLM |
| 测试 | pytest |

## 推荐阅读顺序

1. `pyproject.toml`：确认 Python 版本和运行依赖。
2. `.env.example`：确认运行需要的外部配置。
3. `app/main.py`：理解启动、依赖注入、memory store 和 runtime 初始化。
4. `app/api/chat.py`：理解 SSE 对话入口、事件转换、turn 持久化。
5. `app/agent/runtime.py`：理解上下文预加载、模型流式调用、工具循环和轮数上限。
6. `app/agent/messages.py`、`app/agent/events.py`、`app/agent/state.py`：理解框架无关的内部模型。
7. `app/agent/prompting.py`、`app/agent/context.py`：理解系统 prompt、附件提示、上下文消息和消息裁剪。
8. `app/tools/registry.py`：确认模型可见工具和系统预加载工具。
9. `app/tools/*.py`：理解工具如何桥接 Provider 或 Java 上下文 API。
10. `app/providers/gateway.py`、`app/providers/llm.py`、`app/providers/document.py`、`app/providers/storage.py`：理解解析/生成任务链路。
11. `app/memory/store.py`：理解会话、turn、trace、runtime state 的 SQLite 存储。
12. `app/api/sessions.py`：理解会话列表、详情、重命名、删除。
13. `app/mq/consumer.py`、`app/workers/*.py`：理解 MQ 和内部 HTTP 任务共用 worker 的边界。
14. `tests/`：按修改范围回归验证。

## Agent SSE 对话链路

```text
Client
  -> app/api/chat.py
  -> AgentRuntime.stream(thread_id, user_message, metadata)
  -> load agent_runtime_states
  -> fetch_disease_profile_context when metadata context signature changed
  -> build_prompt_messages
  -> OpenAI SDK chat.completions stream
  -> execute model tool calls through tool_runner
  -> emit token/tool_call/tool_result events
  -> save turn + trace + session summary + runtime state
```

## 工具边界

| 工具 | 触发场景 | 下游依赖 |
| --- | --- | --- |
| `fetch_disease_profile_context` | runtime 根据 metadata 自动预加载疾病档案上下文 | Java 后端内部 API |
| `parse_document` | 用户明确要求读取、分析或解读附件/文档 | `ProviderGateway.execute_with_resilience("parse")` |
| `generate_medical_text` | 用户要求生成摘要、用药方案、报告分析草稿 | `ProviderGateway.execute_with_resilience("generate")` |

工具注册在 `app/tools/registry.py`，每个工具由 `ToolSpec` 描述，并转换为 OpenAI tool schema。

## 存储边界

| 数据 | 存储位置 | 作用 |
| --- | --- | --- |
| 会话索引 | `agent_sessions` | 会话列表、标题、疾病档案元数据、最后消息预览 |
| 对话轮次 | `agent_session_turns` | 用户/助手消息、脱敏 trace、公开 metadata |
| Agent 运行态 | `agent_runtime_states` | 消息历史和当前疾病档案上下文缓存 |

删除会话时会同时删除 indexed session、turn 和 runtime state。

## 修改后验证矩阵

| 修改范围 | 至少运行 |
| --- | --- |
| `app/api/chat.py` | `uv run python -m pytest tests/test_api tests/test_agent -q` |
| `app/agent/*` | `uv run python -m pytest tests/test_agent tests/test_api -q` |
| `app/tools/*` | `uv run python -m pytest tests/test_tools tests/test_agent -q` |
| `app/providers/*` | `uv run python -m pytest tests/test_providers tests/test_workers -q` |
| `app/memory/*` | `uv run python -m pytest tests/test_api tests/test_agent -q` |
| `app/mq/*` 或 `app/workers/*` | `uv run python -m pytest tests/test_workers tests/test_providers -q` |

全量验证：

```powershell
uv run python -m pytest -q
```

## 不要踩的边界

- 不要让 `providers/` 反向依赖 `agent/`、`tools/`、`api/`。
- 不要把业务下载、LLM 调用、重试逻辑塞进 `tools/`。
- 不要在普通问答中强制调用文档解析或文本生成工具。
- 不要把 raw object key、patient id、完整上下文 JSON 暴露到 SSE 或会话 trace。
- 不要随意改 MQ routing key、结果字段名、结构化解析字段名，这些通常是跨服务契约。
- 不要把 prompt 策略调优和 runtime 架构迁移混在同一次验证里。
