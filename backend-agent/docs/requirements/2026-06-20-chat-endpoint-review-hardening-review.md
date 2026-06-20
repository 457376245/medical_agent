# 需求达标审查报告：Chat SSE 端点 Review 后续硬化

## 1. 审查输入

- 需求实施追踪文档：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-review-hardening.md`
- 工作区：`F:\maven_product\medical_agent`
- 分支 / 提交：`master`，初始 `git status` 显示 working tree clean
- 审查时间：2026-06-20
- 审查类型：最终审查

## 2. 审查结论

- 结论：通过
- 总体说明：本次硬化完整实现了文档约定的三项低风险单文件内改进（同一 thread_id 单进程串行锁、公共 turn metadata 由 blocklist 改为 allowlist 并仅暴露白名单字段、取消/结束时正确清理 pending `_next_event` task 与 async iterator 的 aclose）。SSE 事件契约（session / token / tool_call / tool_result / done / error）与 payload 结构保持不变（包括 tool 事件的顶层拍平字段未动）。所有 5 条验收标准均有代码和测试证据支撑。`uv run python -m pytest tests/test_api/test_agent_sessions.py tests/test_api/test_tool_events.py` 结果为 12 passed，与文档记录一致。未发现阻塞性问题或范围偏差。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无 | - | - | - |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：同一 `thread_id` 的两个 chat stream 在单进程内串行执行，避免并发写相同 turn index。 | §5 本次做、§6 标准 1 | `chat.py:463`：`async with _thread_stream_lock(request, thread_id):` 包裹 `_prepare_chat_context` + 整个流式消费 + finally persist；`_ThreadStreamLockRegistry` + `acquire`（带 ref_count + 取消回滚）+ `release` + `_thread_stream_lock` 上下文管理器实现 per-thread 串行；`thread_id` 在锁外确定（460 行）。 | `test_thread_stream_lock_serializes_same_thread`（297-329）：使用 gather 并发 acquire 同一 thread，验证执行顺序 a-start/end 然后 b；`test_thread_stream_lock_acquire_cancel_does_not_leak_ref_count` 覆盖取消场景。 | 通过 |
| 标准 2：`_public_turn_metadata` 不再默认保留未知字段，`patient_id`、原始附件 object key、未知敏感字段不会进入公开 turn metadata。 | §5、§6 标准 2、§11 | `chat.py:36-53` 定义 `_PUBLIC_TURN_METADATA_KEYS` allowlist（含 13 个安全字段）；`152-171` `_public_turn_metadata` 只遍历白名单 key；attachments 仅当 list 时才处理，且仅保留 file_type + display_name（无 object_key）；patient_id 等敏感字段因不在白名单而被排除。`_prepare_chat_context:250-252` 仍接受 X-Patient-Id 头并写入 turn_metadata（供 runtime 使用），但 persist 时（414）经过 public 过滤。 | `test_public_turn_metadata_uses_allowlist`（199-230）：构造含 patient_id/id_card/raw_record_text/unknown_field + attachments（含 object_key）的 metadata，验证仅白名单字段出现且 attachments 被裁剪；`test_public_turn_metadata_skips_non_list_attachments`（233-244）：非 list attachments 不会泄露；集成测试 `test_chat_stream_persists...` 验证 detail 返回的 metadata 中无敏感原始 key。 | 通过 |
| 标准 3：流取消/结束时会 cancel 并 await pending task，且会尝试关闭 async iterator。 | §5、§6 标准 3、§11 | `chat.py:437-450` `_cleanup_stream_tasks` 实现：pending_next 未 done 则 cancel + await suppress CancelledError；stream_aiter 有 aclose 则调用；`534-548` finally 块始终调用 cleanup + persist（正常、CancelledError、Exception 路径均覆盖）。 | `test_cleanup_stream_tasks_awaits_pending_and_closes_iterator`（271-294）：创建未完成的 pending + 带 aclose 的 iterator，验证 cancelled/done 且 closed=True；CancelledError 路径通过代码审查 + `event_stream` 结构确认（520-523 捕获并 re-raise）。 | 通过 |
| 标准 4：`session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件名和现有 payload 兼容性保持不变。 | §6 标准 4 | `chat.py:473` yield session；`312`/`342` `_handle_stream_event` 产生 token/tool_call/tool_result（保留 **public_input 拍平）；`512-518` done；异常 533 yield error；`_sse_event` 格式不变。tool_call/tool_result 顶层拍平字段按“本次不做”保留。 | `test_chat_stream_persists_session_index_and_turn_trace`（69-120）：断言包含 "event: session"、"tool_call"、"tool_result"、"token"、"done"；`test_tool_events.py` 3 项测试验证脱敏行为但不改变事件名。 | 通过 |
| 标准 5：相关测试通过或记录明确环境阻塞原因。 | §6 标准 5、§12 | `py_compile` 通过；测试文件包含所有新增行为单元测试。 | `uv run python -m pytest ...test_agent_sessions.py ...test_tool_events.py` → 12 passed（2026-06-20 实测）；`uv run python -m py_compile ...` → 成功；ruff 因环境 program not found（与文档 §12 记录一致，未作为阻塞）。 | 通过 |

## 5. 文档与代码一致性

- 文档准确的地方：
  - §11 代码变更清单完全匹配实际 diff：`_ThreadStreamLockRegistry`/`_thread_stream_lock`、`_PUBLIC_TURN_METADATA_KEYS` + attachments 仅 list 处理、`_cleanup_stream_tasks`、`_prepare_chat_context` 显式接收 thread_id、测试中 5 个新增测试函数。
  - §3 摘要、§5 范围（只做三项硬化、不改 tool payload、不碰跨模块）、§6 验收标准描述与 §14 最终一致性检查一一对应代码。
  - §7 受影响文件、§8 实施方案、§9 实施计划描述与交付一致。
  - 未验证事项（多 worker、端到端 cancel）和假设（单进程内存锁覆盖内测）在 §5.3、§13、§14 均有记录。
- 文档过时或不准确的地方：无。
- 文档遗漏：无。
- 代码中存在但文档未记录的变更：无（所有修改均在 `chat.py` + `test_agent_sessions.py` 内，且已列入清单）。

## 6. 实现问题

- 问题：无阻塞或高风险实现问题。
- 严重级别：-
- 文件 / 行号：-
- 原因：-
- 建议：-

（细节点：acquire 取消路径的 ref_count 回滚实现正确；finally 保证 cleanup 总执行；锁粒度覆盖 prepare+persist，避免 turn_index 竞态，符合设计。）

## 7. 测试与验证缺口

- 已有验证：
  - 12 passed（包含 lock 串行化、acquire 取消无泄漏、cleanup 关闭、allowlist 过滤、非 list attachments 跳过、集成 persist + metadata 清理）。
  - py_compile 成功。
  - SSE 事件名、payload 结构、持久化行为通过集成测试 + 代码审查确认。
- 缺失验证：
  - `uv run ruff check`（当前环境 ruff 不可执行，与文档一致）。
  - 真实 HTTP 客户端主动断开连接时的完整 cancel + cleanup + persist 端到端行为（单元测试覆盖 helper，但未在 FastAPI TestClient stream 异常路径完整模拟）。
  - 多进程/多 worker 部署下的串行化效果（超出单文件内存锁范围）。
- 无法确认的验证：多实例一致性、真实 LLM 运行时 + 患者记忆抽取器下的长连接取消场景。
- 建议补充：
  - 支持 ruff 的环境补充 `uv run ruff check app/api/chat.py tests/test_api/test_agent_sessions.py`。
  - 在集成/UAT 环境使用真实客户端断开 + 并发相同 thread_id 请求验证行为。
  - 生产多 worker 部署时评估分布式锁或 DB 事务方案（文档已前置说明）。

## 8. 风险与后续事项

- 交付风险：低。核心目标（降低同线程并发竞态、metadata 泄露、取消泄露风险）在当前单进程边界内达成；SSE 契约未破；测试有针对性覆盖；所有“不做”项（tool payload 拍平、认证、tracing、guardrails）均未触碰。
- 后续事项：
  - 文档 §13 已明确：多进程需分布式锁/DB 原子 turn；X-Patient-Id 信任边界、完整 tracing、guardrails、tool permission gate 为后续架构工作。
  - 建议在 review 报告中记录本次审查的“通过”结论，便于后续追溯。
- 是否需要更新需求实施追踪文档：是（本次审查后将更新 §1 审查报告路径和 §15 审查状态/结论/路径）。

## 9. 最终建议

- 是否可以交付：可以。
- 交付前必须修复：无。
- 可后续优化：
  - 补齐 ruff 静态检查（非阻塞）。
  - 增加端到端 cancel 集成测试或压力场景用例（推荐但非本次硬化必须）。
  - 多实例部署时按文档规划引入外部一致性机制。
