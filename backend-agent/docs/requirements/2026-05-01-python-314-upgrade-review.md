# 需求达标审查报告：Python 3.14 升级与有意义新特性重构

## 1. 审查输入

- 需求实施追踪文档：`docs/requirements/2026-05-01-python-314-upgrade.md`
- 工作区：`F:\maven_product\medical_agent\backend-agent`
- 分支 / 提交：`master`，工作区未提交变更
- 审查时间：2026-05-01
- 审查类型：最终审查

## 2. 审查结论

- 结论：有条件通过
- 总体说明：6 条验收标准的核心实现均已到位，Python 3.14 升级、UUID7 重构和 async facade 均有代码和测试证据支撑。但需求实施追踪文档未同步更新验收标准 5/6 的完成状态，且存在一份超出记录范围的 `app/main.py` 变更未在文档中说明。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无阻塞问题 | - | - | - |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：Python 版本约束和锁文件升级到 3.14 | 文档标记 `[x]` 已完成 | `pyproject.toml`: `requires-python = ">=3.14,<3.15"`；`uv.lock`: `requires-python = "==3.14.*"` | `uv run --python 3.14 pytest` 通过（文档记录 55 passed） | 通过 |
| 标准 2：至少一个 Python 3.14 新特性用于真实代码 | 文档标记 `[x]` 已完成 | `app/ids.py:6-8` 使用 `uuid.uuid7()`；`app/api/chat.py:129`、`app/api/sessions.py:119`、`app/memory/store.py:254`、`app/agent/nodes.py:78` 均调用 `new_ordered_id()` / `new_prefixed_ordered_id()` | `tests/test_api/test_agent_sessions.py` 中 `test_create_session_returns_uuid7_thread_id` 断言 `version == 7`；`test_context_flow.py` 断言 context tool call ID 为 UUID7 | 通过 |
| 标准 3：会话创建、聊天流持久化和删除行为保持通过 | 文档标记 `[x]` 已完成 | UUID7 仅替换 ID 生成，API 响应结构和数据库字段类型未变 | `tests/test_api/test_agent_sessions.py` 中 `test_chat_stream_persists_session_index_and_turn_trace` 和 `test_delete_session_removes_indexed_data` 覆盖会话全生命周期 | 通过 |
| 标准 4：使用 Python 3.14 执行测试并记录结果 | 文档标记 `[x]` 已完成 | `uv.lock` 锁定 3.14 | 文档进度日志记录 `55 passed, 72 warnings in 1.64s`；审查时未重跑测试，但锁文件和依赖一致 | 通过 |
| 标准 5：ProviderGateway 提供统一 async facade | 文档标记 `[ ]` 未勾选 | `app/providers/gateway.py:214-221` 已实现 `aexecute_with_resilience()`，内部使用 `asyncio.to_thread()` | `tests/test_providers/test_gateway_ocr.py:test_gateway_async_facade_returns_sync_result` 覆盖 | 通过（代码已实现，但文档未同步） |
| 标准 6：parse/generate worker 通过 async facade 调用，有测试覆盖 | 文档标记 `[ ]` 未勾选 | `app/workers/parse_worker.py:55` 和 `app/workers/generate_worker.py:45` 均调用 `self.gateway.aexecute_with_resilience()`；git diff 确认移除了直接 `asyncio.to_thread()` 调用 | `tests/test_workers/test_async_gateway_boundary.py` 中 `test_parse_worker_uses_gateway_async_facade` 和 `test_generate_worker_uses_gateway_async_facade` 使用 mock gateway 验证调用路径 | 通过（代码已实现，但文档未同步） |

## 5. 文档与代码一致性

- 文档准确的地方：
  - Python 版本升级范围、UUID7 重构范围、Pydantic 升级原因、受影响文件列表、进度日志、验证记录均与代码一致。
- 文档过时或不准确的地方：
  - 验收标准 5 和 6 在文档中仍标记为 `[ ]`（未完成），但代码和测试均已到位。文档第 14 节"最终一致性检查"仍写"4 条验收标准均已满足"，应更新为 6 条。
  - 文档第 14 节"已交付的技术实现"未提及 `ProviderGateway.aexecute_with_resilience()` 和 worker 侧改造。
- 文档遗漏：
  - `app/main.py` 存在 uvicorn reload 配置重构（路径解析、`PYTHONPATH` 注入、`reload_dirs`/`reload_excludes` 参数），该变更未出现在文档第 7 节"受影响的系统和文件"或第 11 节"代码变更清单"中。
- 代码中存在但文档未记录的变更：
  - `app/main.py` 中 `__main__` 入口的 reload 配置重构。

## 6. 实现问题

- 问题：`parse_worker.py:150` 中 `_classify_report` 方法仍直接使用 `asyncio.to_thread(self.gateway.llm.classify_report_category, ...)` 而非通过 gateway facade 调用。
- 严重级别：低
- 文件 / 行号：`app/workers/parse_worker.py:150`
- 原因：`classify_report_category` 不是 gateway 的 `execute_with_resilience` 操作之一，直接 `asyncio.to_thread` 调用 LLM 底层方法在功能上正确，但与"worker 通过 gateway 统一调用"的设计意图不完全一致。
- 建议：可后续将 `classify_report_category` 纳入 gateway 操作，或在文档中说明此为已知例外。

## 7. 测试与验证缺口

- 已有验证：
  - `tests/test_api/test_agent_sessions.py`：UUID7 thread_id、turn_id 断言 + 会话生命周期测试。
  - `tests/test_agent/test_context_flow.py`：UUID7 context tool call ID 断言。
  - `tests/test_providers/test_gateway_ocr.py`：`aexecute_with_resilience` 基本调用验证。
  - `tests/test_workers/test_async_gateway_boundary.py`：parse/generate worker 通过 async facade 调用的路径验证。
- 缺失验证：
  - `aexecute_with_resilience` 的重试/错误场景测试（当前仅测试成功路径）。`execute_with_resilience` 的重试逻辑在同步测试中已有覆盖，async facade 透传行为可推断正确，但缺少直接验证。
- 无法确认的验证：
  - Python 3.14 环境下完整测试运行（审查时未重跑，依赖文档记录的 55 passed 结果）。
- 建议补充：
  - 可选：为 `aexecute_with_resilience` 补充一个失败重试场景的 async 测试，确认 `asyncio.to_thread` 不改变重试行为。

## 8. 风险与后续事项

- 交付风险：
  - `app/main.py` 变更超出需求范围且未在文档中记录，合并前应确认是否为有意变更。
  - 生产部署环境的 Python 3.14 安装未在本任务内验证（文档已说明）。
- 后续事项：
  - 部署侧同步 Python 3.14 运行时。
  - 第三方依赖（FastAPI / Starlette / LangChain）在 Python 3.14/3.16 下的警告后续升级清理。
  - 可选：将 `classify_report_category` 纳入 gateway facade 统一管理。
- 是否需要更新需求实施追踪文档：是。应将验收标准 5/6 标记为 `[x]`，更新第 11 节代码变更清单补充 gateway/worker 变更已落地，更新第 14 节最终一致性检查为 6 条标准均已满足，并说明 `app/main.py` 变更的性质。

## 9. 最终建议

- 是否可以交付：是（有条件）
- 交付前必须修复：无阻塞项。建议更新需求实施追踪文档以反映验收标准 5/6 已完成。
- 可后续优化：async facade 重试场景测试覆盖；`classify_report_category` 统一到 gateway；确认 `app/main.py` 变更是否应归入本次需求或单独提交。
