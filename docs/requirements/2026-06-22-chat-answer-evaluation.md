# Chat 答案质量复核

## 1. 元数据

- 状态：有条件通过
- 负责人：Codex
- 开始日期：2026-06-22
- 最后更新日期：2026-06-22
- 相关请求：当前项目 chat workflow 中只有单 agent 给出结论，希望引入多 agent 方案，评估主 agent 结论是否合理并打分。
- 相关分支 / 提交 / PR：当前工作区未提交
- 需求达标审查报告：`docs/requirements/2026-06-22-chat-answer-evaluation-review.md`

## 2. 原始需求

- 用户原始诉求：讨论并确定在 chat workflow 中引入多 agent 评估主 agent 结论合理性和评分的设计。
- 原始上下文：`backend-agent` 当前 `/api/v1/chat` 由主 agent 流式输出答案，前端展示 token、工具调用和工具结果 trace。
- 后续补充：用户确认按计划实现；复核模式为仅提示风险，评分保存到 trace，评分粒度为总分加问题点。

## 3. 摘要

本需求在现有 chat workflow 后追加只读 evaluator agent。主 agent 仍正常流式回答；回答完成后 evaluator 基于本轮问题、metadata、上下文摘要和主答案输出结构化评分。评分通过 SSE `evaluation` 事件返回，并复用现有 trace 持久化和前端展示能力。

## 4. 背景和目标

- 业务背景：医疗 Agent 回答需要可观察的质量复核，降低证据不足、医疗建议越界和遗漏风险提示的不可见风险。
- 用户 / 问题陈述：当前只有单 agent 直接给结论，缺少对结论合理性的二次判断和评分。
- 目标：在不重写主 agent、不改变主答案输出路径的前提下，为每轮回答增加质量复核结果。
- 成功标准：
  - 主答案 token 流式输出保持兼容。
  - 主答案完成后返回结构化 evaluation。
  - evaluation 保存到会话 trace，历史可回看。
  - evaluator 失败不影响主答案完成。

## 5. 范围边界

### 本次做

- 在 `backend-agent` chat runtime 中增加只读 evaluator agent。
- 新增 SSE `evaluation` 事件。
- 扩展 trace event 类型和前端展示。
- 添加定向自动化测试。

### 本次不做

- 不做多 agent 辩论、投票或自动重写。
- 不新增数据库表或质控统计 API。
- 不改 Java 服务、MQ worker、报告生成链路。
- 不新增模型配置，先复用默认 agent 模型。

### 假设

- evaluator 与主 agent 使用同一模型配置可以满足第一版验证。
- 评分只是辅助质量信号，不改变主答案内容。
- trace JSON 足够承载第一版复核结果。

### 待确认问题

- 真实模型端到端延迟和成本是否可接受：TBD。

## 6. 验收标准

- [x] 标准 1：`/api/v1/chat` 在主答案完成后、`done` 前发送 `evaluation` SSE 事件。
- [x] 标准 2：evaluation 包含 `status`、`overall_score`、`risk_level`、`summary`、`issues`、`suggestions`，并限制为总分加问题点。
- [x] 标准 3：evaluator 异常时主答案仍返回 `done`，trace 中记录 `status=unavailable`。
- [x] 标准 4：evaluation 持久化到现有 turn trace，历史会话能展示。
- [x] 标准 5：前端当前会话和历史会话都能显示评分复核结果。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`、`frontend`
- 主要模块 / 文件：
  - `backend-agent/app/agent/evaluator.py`
  - `backend-agent/app/api/chat.py`
  - `backend-agent/app/memory/models.py`
  - `frontend/src/components/agent/*`
- API / 路由：`POST /api/v1/chat` 新增 SSE `evaluation` 事件；请求体不变。
- 数据库 / 表 / 字段：不新增表字段，复用 trace JSON。
- 配置：不新增配置。
- 定时任务 / MQ / 外部依赖：无变化。

## 8. 实施方案

- 方案概述：主 agent 完成后调用 evaluator，解析结构化 JSON，映射为 `evaluation` trace 和 SSE 事件。
- 关键设计决定：
  - 只读 evaluator 不调用工具。
  - 低分只提示风险，不阻断、不改写。
  - evaluator 失败降级为 unavailable。
- 替代方案与取舍：
  - 未采用低分自动重写，避免第一版增加延迟和行为复杂度。
  - 未新增独立表，避免为未验证的统计需求提前建模。
- 风险：
  - 每轮新增一次模型调用，会增加延迟和成本。
  - 评分本身仍是模型判断，不能替代医学审核。

## 9. 实施计划

1. 扩展后端事件类型、evaluator 调用和 SSE 输出。
2. 扩展 trace 持久化和前端解析展示。
3. 添加后端和前端定向测试。
4. 运行验证并更新本文档。

## 10. 进度日志

- 2026-06-22：创建文档并确认初始范围。
- 2026-06-22：完成后端 evaluator、SSE、trace 持久化、前端展示与定向测试。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-agent/app/agent/evaluator.py` | 新增只读 evaluator 与 JSON 归一化 | 标准 2、3 |
| `backend-agent/app/api/chat.py` | 主答案后发送 `evaluation` SSE 并持久化 trace | 标准 1、4 |
| `backend-agent/app/memory/models.py` | trace event 增加 `evaluation` | 标准 4 |
| `frontend/src/components/agent/*` | 解析并展示评分复核 | 标准 5 |

## 12. 验证与测试

- 计划检查：
  - `cd backend-agent; uv run python -m pytest -q tests/test_agent tests/test_api`
  - `cd frontend; npx vitest run src/components/agent/agent-utils.test.ts`
- 已完成检查：
  - `cd backend-agent; uv run python -m pytest -q tests/test_agent tests/test_api` → 44 passed, 6 warnings
  - `cd frontend; npx vitest run src/components/agent/agent-utils.test.ts` → 7 passed
- 未运行 / 尚未验证：真实 LLM 端到端联调
- 未验证原因：本地测试使用 stub/mock evaluator，未调用真实模型

## 13. 风险与后续事项

- 剩余风险：真实模型端到端延迟、成本和评分稳定性需要联调观察。
- 后续事项：若需要质控统计或低分重写，作为 v2 单独设计。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：主答案完成后返回并展示一次质量复核；复核结果保存到会话 trace；复核失败时主答案仍完成。
- 已交付的技术实现：新增 OpenAI Agents SDK 只读 evaluator；`/api/v1/chat` 追加 `evaluation` SSE；前端解析并展示 evaluation trace。
- 与原始计划的差异：未修改 `runtime.py` 或 `events.py`，实际以独立 evaluator 模块和现有 SSE/trace 分支完成，外部契约不变。
- 验收标准满足情况：标准 1 到标准 5 均已满足。
- 证据与验证：后端定向测试 44 passed；前端 agent-utils 定向测试 7 passed。
- 未验证事项：未使用真实 LLM 对 `/api/v1/chat` 做端到端联调。
- 后续工作：观察真实模型延迟和成本；如需低分重写或质控统计，作为 v2 单独设计。

## 15. Requirement Doc Review 交接

- 审查状态：已审查
- 建议审查报告路径：`docs/requirements/2026-06-22-chat-answer-evaluation-review.md`
- 审查重点：确认主答案兼容性、evaluation 降级行为、trace 持久化和前端历史展示。
- 已知需要审查的问题：真实模型端到端联调可能不在本地自动化验证范围内。
