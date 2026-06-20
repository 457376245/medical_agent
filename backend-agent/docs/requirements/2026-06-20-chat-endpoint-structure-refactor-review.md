# 需求达标审查报告：Chat SSE 端点结构精简重构

## 1. 审查输入

- 需求实施追踪文档：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-structure-refactor.md`
- 工作区：`F:\maven_product\medical_agent`
- 分支 / 提交：`master`，`git status` 显示 working tree clean
- 审查时间：2026-06-20
- 审查类型：最终审查

## 2. 审查结论

- 结论：通过
- 总体说明：本次重构严格为文件内职责拆分，未改变 `/api/v1/chat` 的对外 SSE 事件契约、会话/turn 持久化时机与内容、错误/取消降级语义。`chat()` 已成为清晰的编排函数，主要逻辑已提取到 6 个命名明确的 helper + 2 个轻量 dataclass。验收标准 1-4 满足；标准 5 在当前 `uv` 环境中测试已通过（`test_agent_sessions.py` 4 passed），原记录的 uuid7 失败为运行时环境限制，与重构无关。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无 | - | - | - |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：`POST /api/v1/chat` 仍返回 `session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件，事件 payload 保持兼容。 | 标准 1 | `chat()`:378 发出 `session`；`_handle_stream_event` 378-269 处理 token/tool_call/tool_result 并调用 `_sse_event`；`chat()`:415 发出 `done`；异常路径 436 发出 `error`。runtime 映射与 sanitize 保持原有 payload 结构（含拍平字段）。 | `test_agent_sessions.py` 断言包含所有事件类型，并验证 trace 脱敏；`test_chat_schema.py` 通过。 | 通过 |
| 标准 2：会话预写入、turn 持久化、session 摘要更新、trace_events 保存、错误记录行为保持不变（`_prepare_chat_context` / `_persist_stream_result`）。 | 标准 2 | `_prepare_chat_context`:174-186 执行 pre-upsert；`_persist_stream_result`:318-344 执行 enrich + save_turn + upsert + turn_count 同步；trace_events 由 `_handle_stream_event` 收集并传入 persist。 | `test_chat_stream_persists_session_index_and_turn_trace` 验证 session、turn、trace_events、context_signature、turn_count；测试通过。 | 通过 |
| 标准 3：客户端断开、Agent 异常、runtime final state 读取失败、患者记忆抽取失败等非 happy path 的降级语义保持不变。 | 标准 3 | `CancelledError`:423-426 设置 error 并 re-raise；其他异常:427-436 设置 friendly message、写 error trace、yield error 事件；`finally`:438-448 始终调用 `_cancel_pending...` + `_persist_stream_result`（含 memory 失败也仅 warning）。`_enrich_metadata...` 异常仅 debug 日志不阻断。 | Stub 测试覆盖正常流程；错误路径通过代码结构审查与之前行为对齐确认。 | 通过 |
| 标准 4：`chat()` 主函数职责更清晰，主要复杂逻辑被提取到命名明确的 helper 中。 | 标准 4 | 新增 `ChatTurnContext`、`ChatStreamState`；提取了 `_prepare_chat_context`、`_enrich_metadata_from_runtime_state`、`_handle_stream_event`、`_submit_patient_memory_extraction`、`_persist_stream_result`、`_cancel_pending_stream_task`。`chat()` 仅剩 ~98 行编排（prepare + 流式循环 + finally 委托）。 | 代码结构审查；`python -c` 导入所有 helper 成功；`py_compile` 通过。 | 通过 |
| 标准 5：相关测试通过（本机 `test_agent_sessions.py` 因 Python `uuid.uuid7` 不可用失败，与本次重构无关；`py_compile` 与 `test_chat_schema.py` 通过）。 | 标准 5 | `py_compile app/api/chat.py` 通过；`test_chat_schema.py` 3 passed。 | `uv run python -m pytest tests/test_api/test_chat_schema.py`：3 passed；`uv run python -m pytest tests/test_api/test_agent_sessions.py`：4 passed（当前 venv 支持 uuid7，重构未引入新失败）。 | 通过（环境差异已解决） |

## 5. 文档与代码一致性

- 文档准确的地方：
  - 变更清单（6 个 helper + 2 个 dataclass + 精简 `chat()`）与实际代码完全一致。
  - 实施计划 4 步与实际交付匹配。
  - 验收标准描述与代码中对应函数职责、事件类型、持久化调用点一一对应。
- 文档过时或不准确的地方：无。
- 文档遗漏：无。
- 代码中存在但文档未记录的变更：无（本次仅 `chat.py` 内部重构）。

## 6. 实现问题

- 问题：无显著实现问题。
- 严重级别：-
- 文件 / 行号：-
- 原因：-
- 建议：-

## 7. 测试与验证缺口

- 已有验证：
  - `py_compile` 通过。
  - `uv run python -m pytest tests/test_api/test_chat_schema.py`：3 passed。
  - `uv run python -m pytest tests/test_api/test_agent_sessions.py`：4 passed（覆盖 SSE 事件、会话预写、turn 落库、trace 脱敏、turn_count、metadata 清理）。
  - 所有 `_` helper 可导入，`chat()` 为 async 生成器编排结构。
- 缺失验证：
  - `ruff check`：当前环境未安装 ruff（与原需求文档记录一致）。
  - 未在真实 LLM + 患者记忆抽取器集成环境下执行端到端长连接测试。
  - 未覆盖生产级 keepalive 压力场景。
- 无法确认的验证：同上。
- 建议补充：
  - 在支持完整工具链的环境执行 `uv run ruff check app/api/chat.py`。
  - 在集成/UAT 环境使用真实模型跑一次 `/api/v1/chat` 确认 keepalive、取消、异常落库、记忆抽取全路径。

## 8. 风险与后续事项

- 交付风险：低。重构为纯内部提取，保留了原有 `_next_event`、keepalive、CancelledError 重新抛出、finally 落库等关键控制流；测试已通过核心契约。
- 后续事项：
  - 如需求文档所述，未来可考虑将 `_persist_stream_result` 迁移至独立 service（当前保留在 chat.py 内是合理的保守选择）。
  - 跟踪 `uuid.uuid7` 运行时兼容性（已在本环境解决）。
- 是否需要更新需求实施追踪文档：建议在原文档“标准 5”处补充说明“当前 uv 环境测试已通过”。

## 9. 最终建议

- 是否可以交付：可以。
- 交付前必须修复：无。
- 可后续优化：补齐 ruff 检查 + 真实环境端到端确认（非阻塞）。
