# 需求达标审查报告：OpenAI 兼容服务统一迁移

## 1. 审查输入

- 需求实施追踪文档：`docs/2026-04-02-openai-provider-migration.md`
- 工作区：`F:\maven_product\medical_agent\backend-agent`
- 分支 / 提交：`master` / `defa82a`，工作区存在未提交变更
- 审查时间：2026-05-31 22:49:35 +08:00
- 审查类型：最终审查

## 2. 审查结论

- 结论：有条件通过
- 总体说明：核心迁移目标已经达成。当前代码中 Agent 对话、Provider 解析/生成、PDF/图片多模态内容构造均走 OpenAI 兼容 Chat Completions；未发现 Gemini 或 Google Vision 的运行时依赖。全量 pytest 在本次审查中通过。结论未给“通过”的原因是：本次未复跑真实目标 OpenAI 服务与真实 OSS 文档 `/internal/parse` 端到端链路，且追踪文档存在若干过时记录，需要后续修正文档或补充外部联调证据。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无阻塞问题 | - | 核心验收标准均有代码和测试证据支撑；本次 `uv run python -m pytest -q` 通过，结果为 `79 passed`。 | 交付前补充外部联调记录可将结论提升为“通过”。 |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| `backend-agent` 不再从业务代码路径直接依赖 Gemini 或 Google Vision | 文档第 20 行要求移除 Gemini / Google Vision 直接依赖 | `pyproject.toml:6-18` 仅保留 `openai==2.26.0`、`pypdf`、`pymupdf` 等依赖；`uv.lock:425`、`uv.lock:445`、`uv.lock:504` 锁定 `openai==2.26.0`；`app/providers/llm.py:116-150` 基于 OpenAI 配置；`app/agent/runtime.py:11`、`app/agent/runtime.py:55-69` 使用 `AsyncOpenAI` | `rg -n "langchain|langgraph|google|genai|generative|vision|ChatGoogle|google-cloud|vertex|openai" pyproject.toml uv.lock app tests -S` 未发现 Gemini/Google Vision 运行时导入；全量 pytest `79 passed` | 通过 |
| Agent 对话、`/internal/parse`、`/internal/generate` 保持现有接口不变 | 文档第 21 行要求接口不变 | `/api/v1/chat` 路由仍在 `app/api/chat.py:139-148` 接收 `ChatRequest`；`/internal/parse` 与 `/internal/generate` 仍在 `app/main.py:241-262`；`ChatRequest` 仍保留 message、metadata、attachments 字段，见 `app/schemas/chat.py:56-71` | `tests/test_api/test_agent_sessions.py:58-107` 覆盖 SSE 会话/工具事件；`tests/test_api/test_chat_schema.py:9-48` 覆盖请求模型兼容；定向测试 `30 passed`，全量测试 `79 passed` | 通过 |
| 文本与多模态解析均可通过目标 OpenAI 兼容服务完成 | 文档第 22 行要求文本和多模态均走目标 OpenAI 兼容服务 | `app/providers/llm.py:366-424` 通过 `/chat/completions` 发送文本/多模态消息；`app/providers/document.py:58-82` 将 PDF 文本或图片/PDF 页面转为 OpenAI 内容；`app/providers/document.py:150-165` 构造 `image_url` data URL；`app/providers/llm.py:320-329` 对视觉内容选择 vision model；`app/tools/document_parse.py:40-80` 复用 provider gateway | `tests/test_providers/test_document_parser.py:25-57` 覆盖 PDF 无文本和图片多模态内容构造；`tests/test_providers/test_llm_service.py:45-151` 覆盖视觉模型选择、生成结果和 OpenAI provider 元数据；本次未复跑真实目标服务与真实 OSS 文档端到端 | 部分通过 |

## 5. 文档与代码一致性

- 文档准确的地方：核心目标、接口范围、OpenAI 环境变量、Provider/Gateway 迁移方向与当前代码一致；`app/providers/llm.py`、`app/providers/document.py`、`app/main.py`、`app/tools/document_parse.py` 均真实存在并承担对应职责。
- 文档过时或不准确的地方：追踪文档第 83、87、97、99 行仍写 `uv.lock` 未同步，但当前 `uv.lock` 已包含 `openai==2.26.0`；追踪文档第 42、65、78 行仍引用 `app/agent/nodes.py` 和 `tests/test_agent/test_openai_node.py`，当前工作区中这些文件已删除，Agent 对话改由 `app/agent/runtime.py` 和 `tests/test_agent/test_openai_runtime.py` 承担；追踪文档第 68-69 行的 `langchain-openai` 版本记录已被后续 runtime 迁移淘汰，当前 `pyproject.toml` 不再包含 LangChain/LangGraph 依赖。
- 文档遗漏：当前工作区还有 2026-05-31 的 Agent runtime 迁移改动，包含删除 LangChain/LangGraph 图节点和新增项目内 `AgentRuntime`，该变化不属于 2026-04-02 文档的原始实现记录。
- 代码中存在但文档未记录的变更：`app/agent/runtime.py`、`app/agent/messages.py`、`app/agent/tool_runner.py` 已替代旧节点/图实现；这属于后续需求文档 `docs/requirements/2026-05-31-agent-runtime-langchain-langgraph-migration.md` 的范围。

## 6. 实现问题

- 问题：聊天流错误提示仍建议 `LLM_PROXY_MODE=bypass_google`，但当前代理配置说明只支持 `off`、`sanitize`、`bypass_hosts`。
- 严重级别：低
- 文件 / 行号：`app/api/chat.py:62-69`、`app/utils.py:67-75`
- 原因：这是 Google 迁移后的遗留文案/配置名，不构成 Gemini 或 Google Vision 运行时依赖，但会在网络故障时给出不准确排障建议。
- 建议：后续小修中将提示改为当前真实支持的 `off` 或 `bypass_hosts`，并按需传入需要绕过的 OpenAI 兼容服务主机。

## 7. 测试与验证缺口

- 已有验证：本次运行 `uv run python -m pytest -q`，结果 `79 passed, 71 warnings`；运行 OpenAI 迁移相关定向测试，结果 `30 passed, 71 warnings`；运行 `uv run python -m pytest -q ..\tests\integration\test_provider_gateway_retry_classification.py`，结果 `3 passed, 5 warnings`。
- 缺失验证：本次未调用真实 `http://35.208.147.180:8317/` OpenAI 兼容服务；未使用真实 OSS 医疗文档样本复跑完整 `/internal/parse` 端到端链路。
- 无法确认的验证：目标 OpenAI 兼容服务当前实时可用性、多模态模型当前对真实医疗 PDF/图片的效果、生产规模样本下的解析稳定性。
- 建议补充：用当前代码复跑 `scripts/openai_service_smoke_test.py` 的文本 chat 冒烟；准备一份真实 OSS 文档资产，调用 `/internal/parse` 完成端到端回归并记录请求、模型、响应状态和错误码。

## 8. 风险与后续事项

- 交付风险：无阻塞交付风险；主要风险来自外部服务实时可用性和真实文档样本效果未在本次审查复核。
- 后续事项：修正追踪文档中过时的 `uv.lock`、`nodes.py`、`test_openai_node.py`、`langchain-openai` 描述；修正 `bypass_google` 遗留提示；补充真实 OpenAI 服务和真实 OSS 文档端到端验证记录。
- 是否需要更新需求实施追踪文档：需要。本次已回写审查结论和审查报告路径；其他非状态内容建议单独更新，避免在审查回写中重写历史实施记录。

## 9. 最终建议

- 是否可以交付：可以有条件交付。
- 交付前必须修复：无。
- 可后续优化：补充外部联调证据；修正文档过时项；清理 `bypass_google` 遗留提示。
