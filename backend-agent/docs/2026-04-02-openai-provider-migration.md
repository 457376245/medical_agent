# OpenAI 兼容服务统一迁移

## 元数据

- 状态：有条件通过
- 需求达标审查报告：docs/2026-04-02-openai-provider-migration-review.md
- 负责人：Codex
- 开始日期：2026-04-02
- 最后更新日期：2026-04-02
- 相关请求：将 `backend-agent` 中 Gemini / Google Vision 相关 AI 调用统一迁移到指定 OpenAI 兼容服务

## 摘要

- 将 `backend-agent` 中 Agent 对话、文档解析、结构化提取、文本生成等 AI 能力统一切换到 `http://35.208.147.180:8317/` 提供的 OpenAI 兼容接口，并按场景固定默认模型。

## 背景和目标

- 业务背景：项目当前同时依赖 Gemini 和 Google Vision OCR，接入分散，维护和联调成本较高。
- 用户/问题陈述：希望项目后续统一使用已经验证可用的 OpenAI 兼容接口服务，减少外部 AI 服务分裂。
- 成功标准：
- `backend-agent` 不再从业务代码路径直接依赖 Gemini 或 Google Vision。
- Agent 对话、`/internal/parse`、`/internal/generate` 保持现有接口不变。
- 文本与多模态解析均可通过目标 OpenAI 兼容服务完成。

## 范围

- 范围内：
- 更新 `backend-agent` 的依赖、配置模板、Provider 层、Agent 节点与相关测试。
- 新增/更新文档，明确模型映射、配置方式和验证结果。
- 统一迁移文本和图片/PDF 相关 AI 调用链路。

- 范围外：
- 不改 `frontend` 与 `backend-java` 的业务接口。
- 不改会话、内存、Java 上下文聚合等非 AI 逻辑。
- 不修改生产环境真实密钥文件。

## 受影响的系统和文件

- 项目/服务：`backend-agent`
- 主要模块/文件：
- `backend-agent/app/providers/llm.py`
- `backend-agent/app/providers/document.py`
- `backend-agent/app/agent/nodes.py`
- `backend-agent/app/config.py`
- `backend-agent/app/main.py`
- `backend-agent/app/tools/document_parse.py`
- `backend-agent/pyproject.toml`
- `backend-agent/.env.example`
- `backend-agent/tests/...`
- 配置/路由/表/API：
- `OPENAI_BASE_URL`、`OPENAI_API_KEY` 等新增环境变量
- `/api/v1/chat`
- `/internal/parse`
- `/internal/generate`
- 外部依赖项：OpenAI 兼容 chat completions 接口

## 实施计划

1. 更新需求文档、配置模板和 Python 依赖，定义统一的 OpenAI 环境变量与模型映射。
2. 重构 Provider 层与 Agent 对话接入，移除 Gemini / Google Vision 运行时依赖。
3. 更新测试与项目文档，完成定向验证和真实联调。

## 进度日志

- 2026-04-02：创建迁移文档并确认实现范围，定位核心改动集中在 `backend-agent` 的 provider、agent 和测试层。
- 2026-04-02：完成 `config.py`、`llm.py`、`document.py`、`document_parse.py`、`nodes.py`、`main.py` 的迁移，将运行链路从 Gemini / Google Vision 切到 OpenAI 兼容服务。
- 2026-04-02：补充和改写 provider / agent / integration 测试，覆盖模型映射、结构化解析、多模态消息构造和 Agent OpenAI 装配。
- 2026-04-02：完成真实文本与图片输入联调验证；尝试更新 `uv.lock`，但当前环境访问 PyPI 超时，锁文件未能同步。
- 2026-04-02：修正依赖版本对齐问题，将 `langchain-openai` 从错误的 `0.3.17` 调整为与 `langchain==1.2.10` 同代可配套的 `1.1.11`。
- 2026-04-02：根据 `uv lock` 的解析结果，将 `openai` 从不兼容的 `1.79.0` 调整为 `2.26.0`，与 `langchain-openai==1.1.11` 的依赖约束对齐。

## 验证与测试

- 计划检查：
- 运行 `backend-agent` 相关 provider / agent / api 定向测试。
- 使用冒烟脚本验证目标 OpenAI 兼容服务文本调用。
- 补充至少一条多模态解析验证。
- 已完成检查：
- 运行 `backend-agent/.venv/Scripts/python.exe -m pytest -q backend-agent/tests/test_providers/test_document_parser.py backend-agent/tests/test_providers/test_gateway_ocr.py backend-agent/tests/test_providers/test_llm_service.py backend-agent/tests/test_agent/test_openai_node.py backend-agent/tests/test_agent/test_context_flow.py backend-agent/tests/test_api/test_agent_sessions.py tests/integration/test_provider_gateway_retry_classification.py`，共 `22 passed`。
- 运行 `backend-agent/scripts/openai_service_smoke_test.py` 对目标服务完成文本 chat 联调，成功返回 `pong`。
- 通过真实 `chat/completions` 请求向 `gpt-5.4` 发送内存生成的 PNG 图片，服务成功返回“收到”，验证多模态图片输入可用。
- 未运行/尚未验证：
- 未在真实 OSS 文档资产上跑完整 `/internal/parse` 端到端链路。
- 未完成 `uv.lock` 更新，因为当前环境访问 PyPI 超时。

## 风险与待解决问题

- 风险：`pyproject.toml` 已更新到新的 OpenAI 依赖，但 `uv.lock` 尚未同步，后续在可访问 PyPI 的网络环境中仍需补一次 `uv lock`。
- 风险：当前仅修正了 `pyproject.toml` 中的版本冲突；若本地已有旧缓存或旧锁文件，首次重新安装时仍需要重新解析依赖。
- 风险：依赖版本已经按约束对齐，但仍需以一次成功的 `uv lock` / `uv sync` 作为最终安装层验证。
- 风险：虽然已验证图片输入可用，但真实医疗 PDF/图片在生产数据规模下的效果仍需结合业务样本复核。
- 待解决问题：在可联网环境中补更新锁文件，并在真实 OSS 文档样本上执行一次 `/internal/parse` 端到端回归。

## 最终一致性检查

- 已交付的业务行为：`backend-agent` 中 Agent 对话、文本生成、结构化解析和图片/PDF 多模态解析均已切换为 OpenAI 兼容 chat completions 调用；对外 HTTP 接口保持不变。
- 已交付的技术实现：新增 OpenAI 环境变量与模型映射；`ChatGoogleGenerativeAI` 改为 `ChatOpenAI`；`LLMService` 改为基于 OpenAI 兼容 HTTP 接口；`DocumentParser` 改为构造 OpenAI 多模态消息；`parse_document` 工具改为复用 provider gateway；删除了 `ocr_google.py` 运行时代码。
- 与原始计划的差异：未能同步更新 `uv.lock`，原因是当前环境访问 PyPI 超时；其余核心迁移项已按计划完成。
- 证据与验证：已完成 22 个定向测试、文本 chat 冒烟验证，以及一次真实图片输入联调验证。
- 后续工作：在可联网环境中执行 `uv lock`，并使用真实 OSS 医疗文档样本补一轮 `/internal/parse` 端到端验证。
