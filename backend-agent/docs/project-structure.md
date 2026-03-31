# backend-agent 项目结构规划

> 版本: v2.0 | 更新日期: 2026-02-25
> 状态: 设计稿 — Agent 模块引入

---

## 一、项目概述

`backend-agent` 是一个基于 FastAPI 的 Python 后端服务，承担两大职责：

1. **任务处理服务**（已有）：通过 RabbitMQ / HTTP 接收文档解析和医疗文本生成任务
2. **医疗 Agent 服务**（新增）：提供基于 LLM 的对话式医疗助手，支持工具调用、长短期记忆、SSE 流式输出

两套能力共存于同一 FastAPI 应用中，共享底层 `providers/` 服务层。

---

## 二、技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| Web 框架 | FastAPI + Uvicorn | 0.115+ |
| Agent 框架 | LangChain + LangGraph | 1.2.x / 1.0.x |
| LLM 集成 | langchain-google-genai (Gemini) | 4.2.x |
| 记忆持久化 | SQLite (langgraph-checkpoint-sqlite) | 3.0.x |
| 异步 SQLite | aiosqlite | 0.20+ |
| 消息队列 | aio-pika (RabbitMQ) | 9.4.x |
| 对象存储 | oss2 (阿里云 OSS) | 2.19.x |
| 文档解析 | pypdf + PyMuPDF | 5.x / 1.24.x |
| 数据校验 | Pydantic v2 | 2.9.x |
| 可观测性 | OpenTelemetry | 1.27.x |

---

## 三、目录结构

```
backend-agent/
├── .env                        # 环境变量（密钥、连接串等，gitignore）
├── .env.example                # 环境变量模板
├── pyproject.toml              # uv 项目配置与 Python 依赖
├── data/                       # 运行时数据目录（gitignore）
│   ├── checkpoints.db          # LangGraph 短期记忆 (SQLite)
│   └── memory.db               # 长期记忆存储 (SQLite)
├── docs/                       # 项目文档
│   └── project-structure.md    # 本文件
├── tests/                      # 测试用例
│   ├── __init__.py
│   ├── test_agent/             # Agent 模块测试
│   ├── test_tools/             # 工具测试
│   ├── test_memory/            # 记忆系统测试
│   └── test_api/               # API 端点测试
└── app/                        # 应用源码根目录
    ├── __init__.py
    ├── main.py                 # FastAPI 应用入口、生命周期、DI 装配
    ├── config.py               # 非环境配置（模型参数、工具开关、业务常量）
    ├── utils.py                # 通用工具函数
    │
    ├── api/                    # HTTP 接口层
    │   ├── __init__.py
    │   ├── chat.py             # SSE 流式对话端点
    │   └── sessions.py         # 会话管理端点（创建/恢复/列表/删除）
    │
    ├── agent/                  # Agent 核心引擎
    │   ├── __init__.py
    │   ├── state.py            # Agent 状态定义 (TypedDict)
    │   ├── graph.py            # LangGraph 状态图构建
    │   └── nodes.py            # 图节点实现（LLM 调用、工具分派、结果处理）
    │
    ├── memory/                 # 记忆系统
    │   ├── __init__.py
    │   ├── checkpointer.py     # 短期记忆：AsyncSqliteSaver 封装
    │   ├── store.py            # 长期记忆：MemoryStore 协议 + SQLite 实现
    │   └── models.py           # 记忆数据模型（会话摘要、患者上下文等）
    │
    ├── tools/                  # Agent 工具集
    │   ├── __init__.py
    │   ├── registry.py         # 工具注册表（集中注册、按场景筛选）
    │   ├── document_parse.py   # 工具：文档解析（调用 providers/document + storage）
    │   └── text_generate.py    # 工具：医疗文本生成（调用 providers/llm）
    │
    ├── prompts/                # 提示词管理
    │   ├── __init__.py
    │   ├── system.py           # 系统提示词常量
    │   └── templates.py        # 场景提示词模板（问诊、报告解读、用药建议等）
    │
    ├── schemas/                # 数据模型（Pydantic）
    │   ├── __init__.py
    │   ├── chat.py             # 对话相关模型（ChatRequest, ChatEvent 等）
    │   └── task.py             # 任务相关模型（TaskPayload 等，已有逻辑迁入）
    │
    ├── providers/              # 外部服务客户端（已有）
    │   ├── __init__.py
    │   ├── storage.py          # 阿里云 OSS 文件下载
    │   ├── document.py         # PDF/图片解析
    │   ├── llm.py              # LLM 调用（Gemini 结构化输出）
    │   └── gateway.py          # 弹性编排器（重试、退避、错误分类）
    │
    ├── workers/                # MQ 任务处理器（已有）
    │   ├── __init__.py
    │   ├── parse_worker.py     # 文档解析 worker
    │   └── generate_worker.py  # 文本生成 worker
    │
    └── mq/                     # RabbitMQ 消费者（已有）
        ├── __init__.py
        └── consumer.py         # MQ 连接、队列声明、消息路由
```

---

## 四、模块职责定义

### 4.1 `app/api/` — HTTP 接口层

**职责**：定义所有 HTTP 端点，处理请求/响应序列化，不包含业务逻辑。

| 文件 | 职责 |
|------|------|
| `chat.py` | SSE 流式对话端点。接收用户消息，调用 Agent 图，以 `text/event-stream` 格式逐 token 返回 |
| `sessions.py` | 会话 CRUD 端点。创建新会话（返回 `thread_id`）、恢复历史会话、列出用户会话、删除会话 |

**设计约束**：
- 路由前缀 `/api/v1/chat`、`/api/v1/sessions`
- 现有的 `/internal/parse` 和 `/internal/generate` 端点保留在 `main.py` 中
- `/health` 端点保留在 `main.py` 中

### 4.2 `app/agent/` — Agent 核心引擎

**职责**：定义 Agent 的状态机和执行流程，是整个对话系统的编排中枢。

| 文件 | 职责 |
|------|------|
| `state.py` | 定义 `AgentState(TypedDict)`：消息历史、工具调用结果、元数据等 |
| `graph.py` | 用 LangGraph `StateGraph` 构建 Agent 执行图，编译为可运行的图实例 |
| `nodes.py` | 图中各节点的实现函数：调用 LLM 节点、工具执行节点、结果合并节点 |

**设计约束**：
- 不直接调用 `providers/` 层，通过 `tools/` 间接调用
- 不持有记忆存储引用，通过 `checkpointer` 注入
- 状态定义应保持稳定，新增字段向后兼容

### 4.3 `app/memory/` — 记忆系统

**职责**：管理 Agent 的短期和长期记忆，提供统一的存储抽象。

| 文件 | 职责 |
|------|------|
| `checkpointer.py` | 短期记忆。封装 `AsyncSqliteSaver`，管理 SQLite 连接生命周期，按 `thread_id` 隔离会话状态 |
| `store.py` | 长期记忆。定义 `MemoryStore` 协议（Protocol），提供 SQLite 实现。支持存储/检索患者上下文、对话摘要、关键医疗信息 |
| `models.py` | 记忆相关的 Pydantic 模型：`ConversationSummary`、`PatientContext`、`MedicalFact` 等 |

**设计约束**：
- `MemoryStore` 使用 `typing.Protocol` 定义接口，便于未来切换后端（PostgreSQL、Redis 等）
- SQLite 文件统一存放在 `data/` 目录
- 短期记忆（checkpoint）和长期记忆使用独立的 SQLite 数据库文件
- 所有数据库操作使用异步接口（`aiosqlite`）

### 4.4 `app/tools/` — Agent 工具集

**职责**：将底层服务能力封装为 Agent 可调用的工具函数。

| 文件 | 职责 |
|------|------|
| `registry.py` | 工具注册表。集中管理所有可用工具，支持按场景/角色筛选工具子集 |
| `document_parse.py` | 文档解析工具。Agent 调用此工具解析上传的医疗文档，内部调用 `providers/storage.py` + `providers/document.py` |
| `text_generate.py` | 文本生成工具。Agent 调用此工具生成医疗报告/摘要，内部调用 `providers/llm.py` |

**设计约束**：
- 每个工具使用 LangChain `@tool` 装饰器或继承 `BaseTool`
- 工具函数签名必须有完整的 docstring（LLM 依赖此信息决定何时调用）
- 工具只做"桥接"，核心逻辑在 `providers/` 层
- 新增工具只需新建文件 + 在 `registry.py` 注册，不修改 Agent 核心代码

### 4.5 `app/prompts/` — 提示词管理

**职责**：集中管理所有提示词模板，避免硬编码在业务逻辑中。

| 文件 | 职责 |
|------|------|
| `system.py` | 系统级提示词常量。定义 Agent 的角色定位、行为边界、输出格式要求 |
| `templates.py` | 场景提示词模板。按业务场景组织（问诊引导、报告解读、用药建议等），支持变量插值 |

**设计约束**：
- 所有提示词使用 Python 字符串常量，便于类型检查和 IDE 补全
- 提示词命名使用 `SCREAMING_SNAKE_CASE`（如 `SYSTEM_MEDICAL_ASSISTANT`）
- 模板变量使用 Python f-string 或 `str.format()` 占位

### 4.6 `app/schemas/` — 数据模型

**职责**：定义所有请求/响应/内部传输的 Pydantic 数据模型。

| 文件 | 职责 |
|------|------|
| `chat.py` | 对话相关模型：`ChatRequest`（用户输入）、`ChatEvent`（SSE 事件）、`SessionInfo`（会话元数据） |
| `task.py` | 任务相关模型：`TaskPayload`（MQ 任务载荷）等，从现有代码中整理迁入 |

### 4.7 `app/config.py` — 配置管理

**职责**：集中管理非环境相关的应用配置。

**包含内容**：
- 模型参数（默认模型名、温度、max_tokens）
- Agent 行为参数（最大工具调用轮次、会话超时时间）
- 工具开关（启用/禁用特定工具）
- 业务常量（文件大小限制、PDF 文本截断长度等，从现有代码中迁入）

**不包含**（保留在 `.env` 中）：
- API 密钥、数据库连接串、MQ 地址等敏感/环境相关配置

### 4.8 `app/providers/` — 外部服务客户端（已有）

**职责**：封装所有外部服务的调用细节。是底层基础设施层，被 `tools/` 和 `workers/` 共同复用。

| 文件 | 职责 |
|------|------|
| `storage.py` | 阿里云 OSS 文件下载，含文件大小校验 |
| `document.py` | PDF/图片内容提取，多引擎降级（pypdf → PyMuPDF） |
| `llm.py` | LangChain + Gemini 结构化输出调用 |
| `gateway.py` | 弹性编排器：重试、指数退避、错误分类（`BIZ_*` / `EXT_*`） |

### 4.9 `app/workers/` — MQ 任务处理器（已有）

**职责**：处理来自 RabbitMQ 的异步任务，不参与 Agent 对话链路。

### 4.10 `app/mq/` — RabbitMQ 消费者（已有）

**职责**：管理 MQ 连接、队列声明、消息路由，将消息分派给对应 worker。

---

## 五、模块依赖关系

```
                          ┌──────────────────────────────┐
                          │          main.py             │
                          │  (FastAPI app + DI + 生命周期) │
                          └──────┬────────────┬──────────┘
                                 │            │
                    ┌────────────▼──┐    ┌────▼────────────┐
                    │   api/        │    │   mq/consumer   │
                    │ (SSE + 会话)   │    │ (RabbitMQ)      │
                    └───────┬───────┘    └────┬────────────┘
                            │                 │
                    ┌───────▼───────┐    ┌────▼────────────┐
                    │   agent/      │    │   workers/      │
                    │ (状态图+节点)  │    │ (parse/generate)│
                    └──┬─────┬──────┘    └────┬────────────┘
                       │     │                │
              ┌────────▼┐ ┌──▼──────┐         │
              │ tools/  │ │memory/  │         │
              │(工具封装)│ │(记忆存储)│         │
              └────┬────┘ └─────────┘         │
                   │                          │
              ┌────▼──────────────────────────▼──┐
              │          providers/               │
              │  (OSS, Document, LLM, Gateway)   │
              └──────────────────────────────────┘
                              │
              ┌───────────────▼───────────────────┐
              │     外部服务 (Gemini, OSS, etc.)    │
              └───────────────────────────────────┘
```

**依赖规则**：
1. `api/` → `agent/`：接口层调用 Agent 图
2. `agent/` → `tools/` + `memory/`：Agent 使用工具和记忆
3. `tools/` → `providers/`：工具调用底层服务
4. `workers/` → `providers/`：Worker 直接调用底层服务（不经过 Agent）
5. `providers/` → 外部服务：最底层，无内部依赖
6. **禁止反向依赖**：`providers/` 不得依赖 `agent/`、`tools/`、`api/`

---

## 六、数据流

### 6.1 Agent 对话流（新增）

```
客户端 ──SSE──▶ api/chat.py
                    │
                    ▼
              agent/graph.py  (编译后的 LangGraph 图)
                    │
              ┌─────┼─────┐
              ▼     ▼     ▼
          LLM 节点  工具节点  结果节点
              │     │
              │     ▼
              │  tools/*.py ──▶ providers/*.py
              │
              ▼
         memory/checkpointer.py ──▶ data/checkpoints.db
         memory/store.py        ──▶ data/memory.db
                    │
                    ▼
              SSE 流式响应 ──▶ 客户端
```

### 6.2 MQ 任务流（已有，不变）

```
RabbitMQ ──▶ mq/consumer.py ──▶ workers/*.py ──▶ providers/gateway.py
                                                       │
                                                       ▼
                                                 providers/llm.py
                                                 providers/storage.py
                                                 providers/document.py
                                                       │
                                                       ▼
                                                  RabbitMQ (结果)
```

---

## 七、配置分层

| 配置类型 | 存放位置 | 示例 |
|----------|---------|------|
| 敏感/环境相关 | `.env` | `GOOGLE_API_KEY`, `RABBITMQ_URL`, `OSS_ACCESS_KEY_ID` |
| 应用行为配置 | `config.py` | 默认模型名、温度、最大工具调用轮次、会话超时时间 |
| 业务常量 | `config.py` | `MAX_DOWNLOAD_BYTES`, `MAX_PDF_TEXT_CHARS` |
| 并发/资源配置 | `.env` | `WORKER_THREAD_POOL_SIZE`, `MAX_CONCURRENT_TASKS` |
| SQLite 路径 | `config.py`（默认值）+ `.env`（可覆盖） | `CHECKPOINT_DB_PATH=data/checkpoints.db` |

---

## 八、扩展指南

### 新增一个 Agent 工具

1. 在 `app/tools/` 下创建新文件（如 `medication_lookup.py`）
2. 使用 `@tool` 装饰器定义工具函数，编写完整 docstring
3. 在 `app/tools/registry.py` 中注册该工具
4. 无需修改 `agent/`、`api/` 或 `main.py`

### 新增一个对话场景

1. 在 `app/prompts/templates.py` 中添加场景提示词常量
2. 如需专用工具子集，在 `app/tools/registry.py` 中定义新的工具组
3. 如需独立的 Agent 配置，在 `app/config.py` 中添加场景参数

### 切换记忆存储后端

1. 在 `app/memory/store.py` 中新增实现类（如 `PostgresMemoryStore`）
2. 确保实现 `MemoryStore` 协议的所有方法
3. 在 `main.py` 的 DI 装配处切换实例化
4. 短期记忆可切换为 `langgraph-checkpoint-postgres`

### 引入多 Agent 编排（未来）

1. `app/agent/` 下按 Agent 拆分文件（如 `imaging_agent.py`, `pharmacy_agent.py`）
2. 新增 `app/agent/router.py` 实现 Agent 路由/分诊逻辑
3. 各 Agent 共享 `tools/`、`memory/`、`providers/` 层

---

## 九、LangChain 升级计划

当前版本 `langchain==0.2.16` 需升级至 `1.2.x`，这是破坏性升级。

### 升级步骤

1. **Phase 1 — 依赖升级**：更新 `pyproject.toml`，使用 `uv sync` 安装新版本
2. **Phase 2 — 修复现有代码**：适配 `providers/llm.py` 中的 breaking changes
3. **Phase 3 — 新增 Agent 模块**：基于 LangChain 1.2 + LangGraph 1.0 构建

### 新增依赖

```
langchain==1.2.10
langchain-google-genai==4.2.1
langgraph==1.0.9
langgraph-checkpoint-sqlite==3.0.3
aiosqlite==0.20.0
```
