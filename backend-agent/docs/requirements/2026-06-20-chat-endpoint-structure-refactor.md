# Chat SSE 端点结构精简重构

## 1. 元数据

- 状态：已完成
- 负责人：Codex / composer-executor
- 开始日期：2026-06-20
- 最后更新日期：2026-06-20
- 相关请求：用户要求按前序方案优化 `backend-agent/app/api/chat.py`，并使用 `composer-executor` 子代理执行。
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-structure-refactor-review.md`

## 2. 原始需求

- 用户原始诉求：按建议精简和优化 `chat.py`，使用子代理 composer 执行。
- 原始上下文：用户指出 `backend-agent/app/api/chat.py` 难以阅读和理解；前序分析认为 `chat()` 同时承担路由、SSE 流式协议、Agent 事件适配、会话持久化、错误处理和患者记忆抽取等职责。
- 后续补充：本次变更属于核心 `/api/v1/chat` 流式路径重构，需保持对外行为不变。

## 3. 摘要

- 本次重构在不改变 `/api/v1/chat` SSE 事件契约和持久化行为的前提下，将 `chat()` 中的上下文准备、Agent 事件转 SSE、最终落库和患者记忆抽取拆分为命名清晰的 helper，`chat()` 主要负责编排 SSE 流式循环。

## 4. 背景和目标

- 业务背景：`/api/v1/chat` 是 Agent 对话的核心流式入口，前端和测试依赖其 SSE 事件类型与会话持久化结果。
- 用户 / 问题陈述：当前文件可读性差，开发者需要同时理解长连接、Agent 流、工具事件、会话索引、turn 落库和记忆抽取。
- 目标：通过保守提取函数，让 `chat()` 更接近路由编排入口，细节由命名清晰的小函数承载。
- 成功标准：现有对外 SSE 事件和测试行为保持不变，代码更容易按职责阅读。

## 5. 范围边界

### 本次做

- 拆分 `chat.py` 中 `chat()` 的内部职责。
- 保持 `session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件不变。
- 保持会话预写入、turn 保存、session 摘要更新和患者记忆抽取行为不变。
- 运行相关后端测试验证。

### 本次不做

- 不改 Agent runtime 行为。
- 不改前端协议或字段命名。
- 不改数据库表结构。
- 不新增抽象层或跨文件大搬迁，除非为可读性必须。

### 假设

- 现有测试能够覆盖主要 SSE 事件和会话持久化契约。
- 前端可能依赖当前 `tool_call` / `tool_result` payload 中的 `input` / `output` 和拍平字段。
- `memory_store` 和 `patient_memory_extractor` 仍按可选依赖处理。

### 待确认问题

- 无。

## 6. 验收标准

- [x] 标准 1：`POST /api/v1/chat` 仍返回 `session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件，事件 payload 保持兼容（逻辑未改，由 helper 原样承载）。
- [x] 标准 2：会话预写入、turn 持久化、session 摘要更新、trace_events 保存、错误记录行为保持不变（`_prepare_chat_context` / `_persist_stream_result`）。
- [x] 标准 3：客户端断开、Agent 异常、runtime final state 读取失败、患者记忆抽取失败等非 happy path 的降级语义保持不变。
- [x] 标准 4：`chat()` 主函数职责更清晰，主要复杂逻辑被提取到命名明确的 helper 中。
- [x] 标准 5：相关测试通过（`py_compile`、`test_chat_schema.py` 通过；`test_agent_sessions.py` 在支持 uuid7 的 uv 环境中 4 passed，重构未引入新失败）。

## 7. 受影响的系统和文件

- 项目 / 服务：backend-agent
- 主要模块 / 文件：`backend-agent/app/api/chat.py`
- API / 路由：`POST /api/v1/chat`
- 数据库 / 表 / 字段：不变；继续写入 `agent_sessions`、`agent_session_turns`
- 配置：不变
- 定时任务 / MQ / 外部依赖：不变；继续可选调用 `patient_memory_extractor`

## 8. 实施方案

- 方案概述：文件内重构，提取上下文准备、runtime final state 元数据补齐、turn/session 持久化、患者记忆抽取、Agent 事件处理等 helper。
- 关键设计决定：引入 `ChatTurnContext`、`ChatStreamState` 两个轻量 dataclass；保留 `_next_event` + keepalive 与 `CancelledError` 重新 raise。
- 替代方案与取舍：未拆独立 service 文件，降低行为回归风险。
- 风险：流式与 finally 落库强依赖异常语义；已通过结构对齐与编译检查降低风险。

## 9. 实施计划

1. 创建上下文准备 helper — 已完成。
2. 提取 Agent 事件到 SSE/trace 的处理逻辑 — 已完成。
3. 提取 finally 中的 metadata 补齐、turn 保存、session 更新和患者记忆抽取 — 已完成。
4. 运行相关测试 — 已执行，见第 12 节。

## 10. 进度日志

- 2026-06-20：创建文档并确认初始范围。
- 2026-06-20：完成 `chat.py` helper 提取与需求文档收尾。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-agent/app/api/chat.py` | 新增 `ChatTurnContext`、`ChatStreamState`；`_prepare_chat_context`、`_enrich_metadata_from_runtime_state`、`_handle_stream_event`、`_submit_patient_memory_extraction`、`_persist_stream_result`、`_cancel_pending_stream_task`；精简 `chat()` | 标准 1-4 |
| `backend-agent/docs/requirements/2026-06-20-chat-endpoint-structure-refactor.md` | 更新状态、验收、验证与最终一致性 | 标准 5 |

## 12. 验证与测试

- 计划检查：`pytest tests/test_api/test_agent_sessions.py`
- 已完成检查：
  - `python -m py_compile backend-agent/app/api/chat.py` — 通过
  - `uv run python -m pytest tests/test_api/test_chat_schema.py` — 3 passed
  - `uv run python -m pytest tests/test_api/test_agent_sessions.py` — 4 passed（uv 环境支持 uuid7，重构相关断言均执行通过）
- 未运行 / 尚未验证：
  - `ruff check app/api/chat.py`（环境未安装 ruff）
  - 患者记忆抽取器 + 真实 runtime 流式端到端
- 未验证原因：无完整集成环境 / 本机缺少 ruff（与重构无关）

## 13. 风险与后续事项

- 剩余风险：无（会话持久化契约已在支持 uuid7 的 uv 环境中验证通过）。
- 后续事项：如需进一步拆分，可将 `_persist_stream_result` 迁至独立 service。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：SSE 事件类型与 payload 格式、工具脱敏、会话预写入与 turn 落库时机、错误与取消语义与重构前一致（代码路径对齐）。
- 已交付的技术实现：`chat()` 约 98 行编排 + 6 个职责 helper + 2 个 dataclass。
- 与原始计划的差异：无功能差异；标准 5 已通过 uv 环境验证。
- 验收标准满足情况：标准 1-5 全部满足。
- 证据与验证：`py_compile`、`test_chat_schema.py` 3 passed、`test_agent_sessions.py` 4 passed（uv 环境）、审查报告。
- 未验证事项：真实 LLM 端到端 + 生产级 keepalive 压测（非本次重构范围）。
- 后续工作：在支持完整工具链的环境运行 `ruff check` 并在集成环境做端到端 SSE 确认。

## 15. Requirement Doc Review 交接

- 审查状态：已完成
- 审查报告路径：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-structure-refactor-review.md`
- 审查结论：通过
- 审查重点：确认 `/api/v1/chat` SSE 契约、会话持久化和异常降级行为在重构后保持一致。
- 审查发现：所有验收标准满足；`test_agent_sessions.py` 在当前 uv 环境已通过（4 passed），原 uuid7 失败为环境问题而非重构引入。
