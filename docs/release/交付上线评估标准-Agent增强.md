# 交付上线评估标准-Agent增强

## 评估结论规则

| 结论 | 判定规则 |
|------|----------|
| PASS | prompt、结构化输出、上下文感知、消息裁剪、工具错误恢复、空回复兜底均有测试证据。 |
| CONDITIONAL | 核心链路通过，但部分 prompt 行为只完成静态测试。 |
| FAIL | 上下文丢失、工具错误重复重试、结构化输出不可用，或空回复无兜底。 |

## 业务范围

范围内：场景/工作流 prompt、OpenAI structured output、context guidance、conversation window trimming、tool error recovery、empty response retry。

范围外：真实模型质量大样本评测、医学 RAG、线上 prompt A/B。

## 上线阻断项 P0/P1

| 编号 | 要求 | 验证方式 | 证据路径 | 阻断级别 | 当前状态 |
|------|------|----------|----------|----------|----------|
| AGENT-P0-001 | 结构化解析请求注入 JSON Schema | Python 单元测试 | `backend-agent/tests/test_providers/test_llm_service.py` | P0 | 待验证 |
| AGENT-P0-002 | 上下文部分可用或失败时明确提示限制 | Python 单元测试 | `backend-agent/tests/test_agent/test_context_guidance.py` | P0 | 待验证 |
| AGENT-P0-003 | 工具错误后不重复同参数重试 | Python 单元测试 | `backend-agent/tests/test_agent/test_tool_error_recovery.py` | P0 | 待验证 |
| AGENT-P1-001 | 长对话按 token 预算裁剪 | Python 单元测试 | `backend-agent/tests/test_agent/test_message_trimming.py` | P1 | 待验证 |
| AGENT-P1-002 | 空回复无工具调用时重试一次 | Python 单元测试 | `backend-agent/tests/test_agent/test_tool_error_recovery.py` | P1 | 待验证 |
| AGENT-P1-003 | 文档状态从“进行中”更新或在评估中说明 | 文档审查 | `docs/agent模块增强设计.md` | P1 | 待验证 |

## 验收矩阵

| 编号 | 要求 | 验证方式 | 证据路径 | 阻断级别 | 当前状态 |
|------|------|----------|----------|----------|----------|
| AGENT-A-001 | `report_interpretation` 工作流能注入报告解读 prompt | 单元测试 | `backend-agent/tests/test_prompts/test_templates.py` | P1 | 待验证 |
| AGENT-A-002 | `follow_up_prep` 工作流能注入复诊准备 prompt | 单元测试 | `test_templates.py` | P1 | 待验证 |
| AGENT-A-003 | 前端 metadata 携带 workflow、audience、urgency | 前端单元测试 | `frontend/src/components/agent/agent-utils.test.ts` | P1 | 待验证 |

## 自动化测试

建议命令：

```powershell
uv run pytest tests/test_agent/test_context_guidance.py tests/test_agent/test_message_trimming.py tests/test_agent/test_tool_error_recovery.py tests/test_prompts/test_system_prompt.py tests/test_prompts/test_templates.py tests/test_providers/test_llm_service.py
npm test -- --run src/components/agent/agent-utils.test.ts
```

通过标准：Python Agent 和前端 metadata 测试全部通过。

失败处理：P0 失败标记 `FAIL`；prompt 文档状态不一致可标记 `CONDITIONAL` 并要求补文档。

## 手工验收

操作入口：`/agent`。

测试数据：有疾病档案和报告的患者；解析中、解析失败、无选中报告三类上下文。

期望结果：回答引用上下文限制；工作流切换后 prompt 行为符合报告解读、复诊准备、用药回顾语义；工具错误时给用户友好说明。

截图/日志证据：Agent 对话截图、trace event、测试输出、prompt 文档状态说明。

## Agent 评估指令

Agent 不能只检查 prompt 文案是否存在，还必须检查对应测试是否覆盖。若 `docs/agent模块增强设计.md` 仍标记“进行中”，结论不得为完全 `PASS`，除非有单独发布说明解释状态。

## 已知缺口

| 缺口 | 影响 | 当前状态 |
|------|------|----------|
| Agent 模块设计文档状态仍可能是“进行中” | 文档与交付状态不一致 | 待验证 |
| 真实模型质量没有大样本评估 | 只能证明机制，不能证明回答质量 | 待验证 |
