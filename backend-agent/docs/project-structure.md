# backend-agent 项目结构

> 更新日期: 2026-06-17

`backend-agent` 是 FastAPI 后端服务，承担两类职责：

1. 通过 RabbitMQ / HTTP 处理文档解析和医疗文本生成任务。
2. 提供医疗 Agent 对话服务，支持疾病档案上下文预加载、工具调用、会话记忆和 SSE 流式输出。

## 技术栈

| 层次 | 当前实现 |
| --- | --- |
| Web 服务 | FastAPI + Uvicorn |
| Agent 编排 | OpenAI Agents SDK + `app.agent.runtime.AgentRuntime` 适配层 |
| Agent LLM 接入 | OpenAI Responses API |
| Provider LLM 接入 | OpenAI 兼容 Chat Completions |
| 流式输出 | Server-Sent Events |
| 会话与运行态存储 | SQLite + `aiosqlite` |
| 异步任务 | RabbitMQ + `aio-pika` |
| 文件存储 | 阿里云 OSS |
| 文档解析 | `pypdf` 文本提取 + PyMuPDF 图片渲染 + Vision LLM |
| 数据校验 | Pydantic v2 |
| 可观测性 | OpenTelemetry |

## 目录结构

```text
app/
  main.py                 FastAPI 入口、依赖装配、生命周期、内部任务端点
  config.py               环境变量读取和应用配置
  api/
    chat.py               SSE 对话端点
    sessions.py           会话 CRUD 端点
    tool_events.py        工具事件脱敏
  agent/
    runtime.py            OpenAI Agents SDK runtime 适配层
    messages.py           Agent 消息和工具调用模型
    events.py             Runtime 对外流式事件
    state.py              每个 thread_id 的疾病档案上下文运行态
    tool_runner.py        系统预加载工具执行辅助
    prompting.py          Agent instructions 和旧消息裁剪辅助
    context.py            疾病档案上下文签名、解析、系统消息构造
  tools/
    registry.py           工具注册表和 Agents SDK FunctionTool 转换
    disease_profile_context.py  从 Java 后端获取疾病档案上下文
    document_parse.py     Agent 文档解析工具
    text_generate.py      Agent 医疗文本生成工具
  providers/
    gateway.py            Provider 弹性编排、重试、错误分类
    llm.py                OpenAI 兼容调用、解析/生成提示词、结构化输出
    document.py           PDF/图片转 OpenAI 多模态输入
    storage.py            OSS 下载和文件限制
  memory/
    store.py              SQLite 会话索引、turn、trace 和 Agent 上下文运行态存储
    models.py             会话、turn、trace 数据模型
  workers/
    parse_worker.py       MQ/内部 HTTP 文档解析任务处理
    generate_worker.py    MQ/内部 HTTP 文本生成任务处理
  mq/
    consumer.py           RabbitMQ 连接、队列订阅、结果发布
tests/
  test_api/
  test_agent/
  test_providers/
  test_workers/
```

## Agent 对话链路

1. `POST /api/v1/chat` 创建或恢复 `thread_id`。
2. `AgentRuntime` 从 `SqliteMemoryStore` 读取疾病档案上下文运行态。
3. 如 metadata 中的疾病档案签名变化，runtime 先调用 `fetch_disease_profile_context` 并缓存上下文。
4. `prompting.py` 组装本轮 Agents SDK instructions：系统提示词、附件提示、疾病档案上下文和场景提示。
5. Runtime 通过 OpenAI Agents SDK `Runner.run_streamed()` 调用 Responses API。
6. Agents SDK 使用 `AsyncSQLiteSession` 管理模型对话历史，并执行模型可见工具；超过 `MAX_TOOL_ROUNDS` 后停止。
7. `chat.py` 将 runtime 事件转换为 SSE：`session`、`token`、`tool_call`、`tool_result`、`done`、`error`。
8. 结束时保存 turn、trace、session 摘要和上下文 runtime state。

## 存储

| 表 | 作用 |
| --- | --- |
| `agent_sessions` | 会话列表、标题、疾病档案元数据、最后消息预览 |
| `agent_session_turns` | 用户/助手轮次、脱敏 trace、公开 metadata |
| `agent_runtime_states` | 每个 `thread_id` 的当前疾病档案上下文缓存 |
| `agent_sessions.db` | OpenAI Agents SDK session 历史，供模型多轮上下文使用 |

`MEMORY_DB_PATH` 默认指向 `data/memory.db`，`AGENT_SESSION_DB_PATH` 默认指向 `data/agent_sessions.db`。

## 修改边界

- `api/` 只处理 HTTP/SSE 契约和持久化，不直接调用 Provider。
- `agent/` 负责编排 LLM、上下文和工具，不承载文档下载、OCR 或业务重试。
- `tools/` 只把底层服务包装为模型可调用工具。
- `providers/` 负责 OSS、文档解析、LLM 调用和重试分类。
- MQ worker 与 HTTP 内部任务共用 ProviderGateway，不依赖 Agent runtime。
