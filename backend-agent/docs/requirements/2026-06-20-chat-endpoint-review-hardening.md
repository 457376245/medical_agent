# Chat SSE 端点 Review 后续硬化

## 1. 元数据

- 状态：已完成
- 负责人：Codex / composer-executor
- 开始日期：2026-06-20
- 最后更新日期：2026-06-20
- 相关请求：用户提供当前 `chat.py` 实现 review，要求分析是否值得采纳，若值得采纳则使用子代理执行。
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-review-hardening-review.md`

## 2. 原始需求

- 用户原始诉求：分析 review 是否值得采纳；如果值得采纳，使用子代理执行。
- 原始上下文：review 指出当前 Agent SSE 网关设计方向正确，但存在并发一致性、权限边界、schema 稳定性、取消清理、metadata 暴露、tracing/guardrails 等生产化差距。
- 后续补充：本轮只采纳可在当前代码边界内低风险完成的硬化项。

## 3. 摘要

- 本次硬化采纳 review 中最适合当前单文件落地的三类建议：同一 `thread_id` 串行化、公共 turn metadata 改 allowlist、取消时等待 pending task 并关闭 async iterator。暂不修改 `tool_call` / `tool_result` SSE payload 顶层拍平字段，避免破坏前端兼容；暂不实现 `X-Patient-Id` 授权、完整 tracing、guardrails、人审等跨模块能力。

## 4. 背景和目标

- 业务背景：`/api/v1/chat` 是 Agent 对话核心 SSE 入口，影响会话状态、工具 trace、患者上下文和前端流式体验。
- 用户 / 问题陈述：review 认为当前实现可内测但生产化不足，需要判断哪些建议值得采纳。
- 目标：先补齐高收益、低耦合、可验证的安全与一致性短板。
- 成功标准：不破坏现有 SSE 契约和测试，降低同线程并发竞态、metadata 默认暴露和取消泄露风险。

## 5. 范围边界

### 本次做

- 为同一 `thread_id` 的 chat stream 增加单进程串行化，避免同一会话并发 run 互相覆盖 turn index 和 runtime state。
- 将 `_public_turn_metadata` 从 blocklist 改为 allowlist，并保留现有安全可展示字段。
- 在取消/收尾时等待 pending `_next_event` task 完成取消，并调用 async iterator 的 `aclose()`（如存在）。
- 补充/调整相关测试。

### 本次不做

- 不移除 `tool_call` / `tool_result` payload 中已有顶层拍平字段，避免前端兼容风险。
- 不实现认证授权体系；`X-Patient-Id` 信任边界只记录为后续架构问题。
- 不新增完整 tracing、OpenTelemetry、guardrails、人审或 tool permission gate。
- 不改数据库 schema、Agent runtime、前端。

### 假设

- 当前服务为单进程内存锁即可覆盖内测/本地部署的一致性风险；多进程部署仍需外部锁或数据库事务。
- 前端可能依赖 tool event 顶层拍平字段，因此本轮不改变。
- 公共 metadata 只应暴露明确列入 allowlist 的字段。

### 待确认问题

- 生产部署是否多 worker / 多实例；若是，单进程锁不能作为最终一致性方案。
- `X-Patient-Id` 的可信来源应由网关还是后端认证中间件提供。

## 6. 验收标准

- [x] 标准 1：同一 `thread_id` 的两个 chat stream 在单进程内串行执行，避免并发写相同 turn index。
- [x] 标准 2：`_public_turn_metadata` 不再默认保留未知字段，`patient_id`、原始附件 object key、未知敏感字段不会进入公开 turn metadata。
- [x] 标准 3：流取消/结束时会 cancel 并 await pending task，且会尝试关闭 async iterator。
- [x] 标准 4：`session`、`token`、`tool_call`、`tool_result`、`done`、`error` SSE 事件名和现有 payload 兼容性保持不变。
- [x] 标准 5：相关测试通过或记录明确环境阻塞原因。

## 7. 受影响的系统和文件

- 项目 / 服务：backend-agent
- 主要模块 / 文件：`backend-agent/app/api/chat.py`，可能新增/调整 `backend-agent/tests/test_api/test_agent_sessions.py`
- API / 路由：`POST /api/v1/chat`
- 数据库 / 表 / 字段：不变
- 配置：不变
- 定时任务 / MQ / 外部依赖：不变

## 8. 实施方案

- 方案概述：保持文件内硬化，新增 per-thread lock helper、metadata allowlist、异步关闭 helper，并用测试覆盖关键行为。
- 关键设计决定：保留 SSE payload 兼容，避免把 schema 稳定性建议变成破坏性协议变更。
- 替代方案与取舍：数据库事务/分布式锁更适合多实例生产，但当前改动先覆盖单进程风险。
- 风险：长连接串行化会让同一会话第二个请求等待前一个请求结束；这是 Agent 会话一致性需要的取舍。

## 9. 实施计划

1. 使用 `composer-executor` 修改 `chat.py` 并补测试。
2. 运行相关测试。
3. 更新本文档的变更清单、验证结果和最终一致性检查。

## 10. 进度日志

- 2026-06-20：创建文档，确认采纳范围和暂缓项。
- 2026-06-20：composer-executor 完成 chat.py 硬化与测试，10 passed。
- 2026-06-20：修正 lock acquire 取消时 ref_count 泄漏；attachments 非 list 不公开；12 passed。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-agent/app/api/chat.py` | per-thread 锁（acquire 取消回滚 ref_count）、metadata allowlist（attachments 仅 list）、`_cleanup_stream_tasks`、`_prepare_chat_context` 接收 thread_id | 标准 1-4 |
| `backend-agent/tests/test_api/test_agent_sessions.py` | allowlist、非 list attachments、cleanup、lock 串行化、acquire 取消无泄漏 | 标准 1-5 |

## 12. 验证与测试

- 计划检查：`uv run python -m pytest tests/test_api/test_agent_sessions.py tests/test_api/test_tool_events.py`
- 已完成检查：
  - `uv run python -m pytest tests/test_api/test_agent_sessions.py tests/test_api/test_tool_events.py` → 12 passed
  - `uv run python -m py_compile app/api/chat.py tests/test_api/test_agent_sessions.py` → 通过
- 未运行 / 尚未验证：`uv run ruff check app/api/chat.py tests/test_api/test_agent_sessions.py`
- 未验证原因：当前环境缺少 `ruff` 可执行文件，命令返回 `program not found`

## 13. 风险与后续事项

- 剩余风险：多进程/多实例环境仍需分布式锁、DB 原子 turn 分配或 runtime 层 single-flight。
- 后续事项：认证授权后的 patient scope、tool event schema 版本化、完整 tracing、guardrails/tool permission gate。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：同 thread 单进程串行；会话 turn metadata 仅暴露 allowlist 字段；流结束/取消时清理 pending task 与 async iterator。
- 已交付的技术实现：`_ThreadStreamLockRegistry` + `_thread_stream_lock`；`_PUBLIC_TURN_METADATA_KEYS` allowlist；`async _cleanup_stream_tasks`；`chat()` 先定 thread_id 再锁内准备与流式消费。
- 与原始计划的差异：无；未改动 tool_call/tool_result 顶层拍平字段。
- 验收标准满足情况：标准 1-5 均已满足（标准 5 有测试证据）。
- 证据与验证：`uv run python -m pytest tests/test_api/test_agent_sessions.py tests/test_api/test_tool_events.py` 12 passed；`uv run python -m py_compile app/api/chat.py tests/test_api/test_agent_sessions.py` 通过。
- 未验证事项：多 worker 部署下的分布式锁；真实客户端断开时的端到端 cancel 行为。
- 后续工作：X-Patient-Id 授权、tracing、guardrails、tool permission gate（见 §13）。


## 15. Requirement Doc Review 交接

- 审查状态：已审查
- 审查报告路径：`backend-agent/docs/requirements/2026-06-20-chat-endpoint-review-hardening-review.md`
- 审查结论：通过
- 审查重点：确认本轮采纳项未破坏 SSE 契约，并确实降低并发、metadata 暴露和取消清理风险。
- 审查发现：所有验收标准 1-5 满足；12 passed 测试证据完整；代码实现与文档 §5 范围、§11 变更清单、§14 一致性检查完全对齐；未触碰 tool payload 拍平及跨模块能力；ruff 检查与真实多 worker 端到端 cancel 为已记录的非阻塞环境/后续项。
- 已知需要审查的问题：单进程锁不是多实例最终方案（文档 §5.3、§13 已说明）。
