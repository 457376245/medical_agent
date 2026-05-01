# Python 3.14 升级与有意义新特性重构

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-01
- 最后更新日期：2026-05-01
- 相关请求：将项目从python3.12升级至3.14，并使用有价值有意义的新特性重构当前项目；追加实现 asyncio.to_thread 并发边界增强计划
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：将项目从python3.12升级至3.14，并使用有价值有意义的新特性重构当前项目
- 原始上下文：目标项目为 `backend-agent`，当前工作区位于 `F:\maven_product\medical_agent\backend-agent`。
- 后续补充：用户要求实现 `asyncio.to_thread` 并发边界增强计划，确认项目是否有接口可以引入该写法提高性能后，采用最小方案：新增 provider gateway 异步 facade，并让 parse/generate worker 通过该 facade 调用同步 provider。

## 3. 摘要

本需求已将 Python 运行时约束、依赖锁文件和验证命令升级到 Python 3.14，并选择与当前业务有实际价值的 Python 3.14 新能力重构会话标识生成逻辑。当前实现使用 Python 3.14 的 UUID7 生成新会话、会话轮次和内部工具调用 ID，使新增 ID 保持时间有序，便于排查、索引和按时间观察会话数据。

追加并发边界增强：`/internal/parse` 和 `/internal/generate` 的阻塞 provider 调用已在 worker 层使用 `asyncio.to_thread()`，本次将该线程化边界上移并显式封装为 `ProviderGateway.aexecute_with_resilience()`，让 async 调用方复用统一入口，避免后续直接在异步接口中调用同步阻塞 provider。

## 4. 背景和目标

- 业务背景：`backend-agent` 是医疗智能体后端服务，包含会话、记忆、工具调用和 MQ 任务处理能力。
- 用户 / 问题陈述：项目需要从 Python 3.12 升级到 Python 3.14，并避免只改版本号，需要结合项目场景使用有意义的新特性。
- 目标：
  - 将项目声明的 Python 版本升级到 3.14。
  - 使用 Python 3.14 新特性做小范围、可解释的业务代码重构。
  - 显式固化同步 provider 在异步 worker 中的线程化调用边界。
  - 保持现有 API 行为和测试兼容。
- 成功标准：
  - `pyproject.toml` 和 `uv.lock` 指向 Python 3.14。
  - 本地可使用 Python 3.14 运行测试。
  - 新特性应用位置与业务场景相关，不引入无关抽象。

## 5. 范围边界

### 本次做

- 升级 Python 版本约束和锁文件到 3.14。
- 使用 UUID7 重构会话、轮次和上下文工具调用 ID 生成逻辑。
- 增加或调整聚焦测试，证明新生成 ID 为 UUID7 且现有会话接口仍可用。
- 运行 Python 3.14 下的自动化测试。
- 新增 provider gateway async facade，并让 parse/generate worker 使用该入口。

### 本次不做

- 不升级 Java、前端或其他仓库。
- 不重写业务流程、数据库结构或外部接口协议。
- 不为了使用新语法而大面积格式化、重排或重构无关文件。
- 不升级所有业务依赖版本，除非 Python 3.14 兼容性需要。
- 不把 LangGraph tool、底层 OSS、urllib 或 PDF parser 全量改为 async。
- 不调整 `WORKER_THREAD_POOL_SIZE` / `MAX_CONCURRENT_TASKS` 并发配置。

### 假设

- 运行时目标是 CPython 3.14.x，当前机器已有 `cpython-3.14.0`。
- 对外暴露的 `thread_id` / `turn_id` 仍保持字符串格式，允许从 UUID4 hex 变更为 UUID7 hex。
- 旧数据中已有 UUID4 ID 继续可读；本次只影响新生成 ID。
- `asyncio.to_thread()` 不是 Python 3.14 新增特性，但在 3.14 运行时可用；本次使用它是为了统一阻塞 I/O 的异步调用边界。
- LangGraph / LangChain 对同步 tool/runnable 已有 executor 包装，本次不重复包线程。

### 待确认问题

- 生产部署镜像或服务器是否已经安装 Python 3.14：TBD。

## 6. 验收标准

- [x] 标准 1：项目 Python 版本约束和锁文件均升级到 Python 3.14。
- [x] 标准 2：至少一个与项目现有业务相关的 Python 3.14 新特性被用于真实代码，而不是示例代码。
- [x] 标准 3：会话创建、聊天流持久化和会话删除等现有行为保持通过测试。
- [x] 标准 4：使用 Python 3.14 执行测试并记录结果。
- [x] 标准 5：`ProviderGateway` 提供统一 async facade，worker 通过该 facade 调用同步 provider。
- [x] 标准 6：parse/generate worker 行为保持不变，并有测试覆盖 async facade 调用路径。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`
- 主要模块 / 文件：`pyproject.toml`、`uv.lock`、`app/api/chat.py`、`app/api/sessions.py`、`app/memory/store.py`、`app/agent/nodes.py`、`app/ids.py`、`app/providers/gateway.py`、`app/workers/parse_worker.py`、`app/workers/generate_worker.py`、`tests/test_api/test_agent_sessions.py`、worker/provider 相关测试
- API / 路由：`POST /api/v1/chat`、`POST /api/v1/sessions`、会话详情和删除接口保持兼容
- 数据库 / 表 / 字段：不新增表或字段；`agent_sessions.thread_id`、`agent_session_turns.turn_id` 的新写入值改为 UUID7 hex
- 配置：`requires-python`
- 定时任务 / MQ / 外部依赖：MQ 消费者调用的 parse/generate worker 内部线程化边界调整；外部协议不变

## 8. 实施方案

- 方案概述：先升级版本约束并重新生成锁文件；再集中封装 ID 生成函数，将新 ID 生成点切换到 `uuid.uuid7()`；最后补充测试并在 Python 3.14 下运行验证。
- 追加并发方案：新增 `ProviderGateway.aexecute_with_resilience()`，内部使用 `asyncio.to_thread()` 调用现有同步重试逻辑；parse/generate worker 改为调用 async facade，底层 provider 和外部响应结构不变。
- 关键设计决定：
  - 选用 UUID7：会话和轮次天然具有时间属性，UUID7 的时间有序特征对排查和索引更有意义。
  - 保持 hex 字符串输出：避免影响现有 API 响应结构和数据库字段类型。
  - 不迁移历史 UUID4：历史数据兼容性不需要改动。
  - `asyncio.to_thread()` 边界放在 gateway facade：worker 不再知道同步 provider 如何线程化，后续 async 调用方也能复用同一入口。
- 替代方案与取舍：
  - 大面积移除 `from __future__ import annotations` 可以体现 Python 3.14 默认延迟注解，但对业务价值较弱且会造成噪音，暂不作为主要重构。
  - 使用模板字符串等新特性与当前业务代码契合度不足，暂不引入。
  - 将底层 `LLMService` / `OSSStorageService` / `DocumentParser` 全量 async 化改动面过大，暂不引入。
- 风险：
  - 部署环境必须同步安装 Python 3.14。
  - 被 pin 住的依赖可能存在 Python 3.14 兼容性问题，需要通过锁文件和测试暴露。

## 9. 实施计划

1. 确认当前改动和 Python 3.14 可用性，创建需求跟踪文档。
2. 升级 `pyproject.toml` 和 `uv.lock` 到 Python 3.14。
3. 使用 UUID7 重构 ID 生成点并补充测试。
4. 使用 Python 3.14 运行测试，更新文档和最终一致性检查。
5. 新增 provider gateway async facade，替换 worker 中直接 `asyncio.to_thread()` 调用，并补充测试。

## 10. 进度日志

- 2026-05-01：创建文档并确认初始范围；发现当前工作区已有改动仍指向 Python 3.12，尚未完成 3.14 升级。
- 2026-05-01：首次使用 Python 3.14 创建独立测试环境时，`pydantic==2.9.2` 依赖的 `pydantic-core==2.23.4` 缺少 CPython 3.14 wheel，且本机缺 MSVC linker 无法现场构建；决定只升级 Pydantic 2.x 直接依赖以获取 3.14 wheel。
- 2026-05-01：完成 UUID7 重构、锁文件更新和 Python 3.14 测试验证；最终测试结果为 `55 passed, 72 warnings`。
- 2026-05-01：追加 asyncio.to_thread 并发边界增强计划，准备新增 `ProviderGateway.aexecute_with_resilience()` 并让 parse/generate worker 调用该 async facade。
- 2026-05-01：完成 async facade 与 worker 调用路径改造；Python 3.14 完整测试结果更新为 `58 passed, 72 warnings`。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `pyproject.toml` | 已升级 Python 版本约束到 `>=3.14,<3.15`，并将 Pydantic 升级到支持 3.14 wheel 的 `2.12.4` | 标准 1 |
| `uv.lock` | 已重新锁定 Python 3.14 依赖，顶部约束为 `requires-python = "==3.14.*"` | 标准 1 |
| `app/ids.py` | 已新增 UUID7 ID 生成 helper，统一输出 hex 字符串和带前缀 ID | 标准 2 |
| `app/api/chat.py` | 已将未传入 `thread_id` 时的新聊天会话 ID 改为 UUID7 | 标准 2、3 |
| `app/api/sessions.py` | 已将显式创建会话 ID 改为 UUID7 | 标准 2、3 |
| `app/memory/store.py` | 已将新轮次 ID 改为 UUID7 | 标准 2、3 |
| `app/agent/nodes.py` | 已将上下文工具调用 ID 改为 `context-<uuid7 hex>` | 标准 2 |
| `tests/test_api/test_agent_sessions.py` | 已补充聊天流、轮次和显式创建会话的 UUID7 行为断言 | 标准 2、3 |
| `tests/test_agent/test_context_flow.py` | 已补充上下文工具调用 ID 的 UUID7 行为断言 | 标准 2 |
| `app/providers/gateway.py` | 已新增 `aexecute_with_resilience()`，统一用 `asyncio.to_thread()` 执行同步 provider 重试逻辑 | 标准 5 |
| `app/workers/parse_worker.py` | 已改为调用 gateway async facade | 标准 5、6 |
| `app/workers/generate_worker.py` | 已改为调用 gateway async facade | 标准 5、6 |
| `tests/test_providers/test_gateway_ocr.py` | 已补充 gateway async facade 返回同步执行结果的测试 | 标准 6 |
| `tests/test_workers/test_async_gateway_boundary.py` | 已补充 parse/generate worker 调用 async facade 的测试 | 标准 6 |

## 12. 验证与测试

- 计划检查：
  - `uv lock`
  - `uv run --python 3.14 python --version`
  - `uv run --python 3.14 pytest`
- 已完成检查：
  - `uv python list` 显示本机已有 `cpython-3.14.0`。
  - `uv run --python 3.14 pytest` 首次验证失败，原因是旧版 `pydantic-core` 需要本机构建但缺少 MSVC linker。
  - `UV_CACHE_DIR=.uv-cache uv lock` 使用 CPython 3.14 解析成功，更新 `pydantic 2.9.2 -> 2.12.4`、`pydantic-core 2.23.4 -> 2.41.5`。
  - `UV_CACHE_DIR=.uv-cache UV_PROJECT_ENVIRONMENT=.venv314 uv run --python 3.14 pytest` 通过，结果为 `55 passed, 72 warnings in 1.64s`。
  - `UV_CACHE_DIR=.uv-cache UV_PROJECT_ENVIRONMENT=.venv314 uv run --python 3.14 pytest tests/test_providers/test_gateway_ocr.py tests/test_workers/test_async_gateway_boundary.py` 通过，结果为 `5 passed, 6 warnings in 2.06s`。
  - `UV_CACHE_DIR=.uv-cache UV_PROJECT_ENVIRONMENT=.venv314 uv run --python 3.14 pytest` 通过，结果为 `58 passed, 72 warnings in 3.20s`。
  - `git diff --check` 通过，无空白错误；Git 仅提示工作区文件下一次触碰时 LF 会替换为 CRLF。
- 未运行 / 尚未验证：
  - 未验证生产部署镜像或服务器上的 Python 3.14 安装。
- 未验证原因：
  - 部署环境不在当前仓库和本地验证范围内。

## 13. 风险与后续事项

- 剩余风险：
  - 生产部署环境未在本任务内验证。
  - Python 3.14 测试中仍有第三方依赖警告：`langchain_core` 提示 Pydantic V1 compatibility 在 Python 3.14+ 不兼容；FastAPI / Starlette 使用的 `asyncio.iscoroutinefunction` 在 Python 3.16 计划移除。当前测试通过，但后续依赖升级时应关注。
- 后续事项：部署侧需要同步 Python 3.14 运行时；后续可独立评估 FastAPI / Starlette / LangChain 依赖升级以清理警告。
- 阻塞项：无

## 14. 最终一致性检查

- 已交付的业务行为：新创建的聊天会话、新显式创建会话、持久化轮次和上下文工具调用 ID 均使用 UUID7；原有会话列表、详情、删除、聊天流持久化行为保持通过测试；parse/generate worker 对外响应结构保持不变。
- 已交付的技术实现：项目 Python 约束升级到 3.14；`uv.lock` 重新锁定为 3.14；新增 `app/ids.py` 统一 UUID7 生成；Pydantic 升级到具备 CPython 3.14 wheel 的 2.x 版本；新增 `ProviderGateway.aexecute_with_resilience()` 统一 async 调用同步 provider 的线程化边界。
- 与原始计划的差异：原计划尽量不升级依赖；实际为解决 Python 3.14 安装验证失败，最小化升级了直接依赖 `pydantic`。
- 验收标准满足情况：6 条验收标准均已满足。
- 证据与验证：
  - `pyproject.toml`：`requires-python = ">=3.14,<3.15"`。
  - `uv.lock`：`requires-python = "==3.14.*"`。
  - `uv.lock`：包含 `pydantic-core` 的 `cp314` wheel。
  - `UV_CACHE_DIR=.uv-cache UV_PROJECT_ENVIRONMENT=.venv314 uv run --python 3.14 pytest`：`58 passed, 72 warnings in 3.20s`。
  - `UV_CACHE_DIR=.uv-cache UV_PROJECT_ENVIRONMENT=.venv314 uv run --python 3.14 pytest tests/test_providers/test_gateway_ocr.py tests/test_workers/test_async_gateway_boundary.py`：`5 passed, 6 warnings in 2.06s`。
  - `git diff --check`：通过。
- 未验证事项：生产部署环境的 Python 3.14 安装和镜像构建未验证。
- 后续工作：部署侧同步 Python 3.14；后续单独评估第三方依赖升级以清理 Python 3.14/3.16 相关警告。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-05-01-python-314-upgrade-review.md`
- 审查重点：确认 Python 3.14 升级、UUID7 重构、async provider facade 并发边界增强和测试证据是否满足原始需求与后续补充。
- 已知需要审查的问题：生产部署环境的 Python 3.14 安装不在本仓库内验证；Python 3.14 下仍存在第三方依赖警告但测试通过。
