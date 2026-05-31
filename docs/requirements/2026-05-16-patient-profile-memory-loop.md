# 患者长期画像自动记忆闭环

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-16
- 最后更新日期：2026-05-16
- 相关请求：按 plan 实施“通过每次问询自动完善病人的长期人物画像，并作为基础上下文携带”
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：希望能通过每次问询，不断自动完善病人的人物画像，并作为基础上下文携带。
- 原始上下文：当前项目已有 Java 侧慢病画像、随访任务、症状记录和 Agent 上下文聚合；Python Agent 只消费上下文，未形成自动写回闭环。
- 后续补充：用户要求按适合长期商用上线的方案实施，不以临时妥协为目标。

## 3. 摘要

本需求新增患者画像记忆闭环：对话结束后由 backend-agent 抽取画像更新候选，提交给 backend-java 的患者记忆账本；Java 侧保存候选、支持确认/拒绝，并在确认后将候选按类型合并进现有慢病画像、个人背景/偏好、症状或随访任务。Agent 后续仍通过既有疾病档案上下文接口携带已确认画像，并补充相关待确认记忆提示。

## 4. 背景和目标

- 业务背景：医疗 Agent 需要长期记住患者慢病、用药、过敏、症状、目标和偏好，但医疗事实不能由模型静默覆盖。
- 用户 / 问题陈述：每次问询产生的新信息如果不能进入长期画像，Agent 无法持续个性化；如果无确认链路直接写入，又存在商用医疗安全风险。
- 目标：建立可追溯、可确认、可拒绝、可审计的画像记忆账本，并接入 Agent 自动抽取。
- 成功标准：对话产生候选记忆；用户可查看并处理；确认后的画像进入后续 Agent 基础上下文。

## 5. 范围边界

### 本次做

- 新增患者记忆账本表，保存对话抽取的画像候选、证据、置信度、风险等级和状态。
- 新增 Java API：提交候选、查询待确认记忆、确认/拒绝候选。
- 确认后合并到现有慢病画像、症状记录、随访任务和新增个人背景/偏好字段。
- backend-agent 在每轮对话持久化后调用抽取器并提交候选。
- Java 内部 Agent 接口在配置内部 API key 时必须校验 `X-Internal-Api-Key`。
- 前端 Agent 页提供待确认画像更新列表和确认/拒绝操作。

### 本次不做

- 不引入向量数据库或复杂知识图谱。
- 不让 LLM 直接覆盖诊断、过敏、用药等高风险主档案事实。
- 不改变现有疾病档案、报告解析和趋势分析主流程。

### 假设

- Java 侧仍是患者画像可信事实源。
- 高风险医疗事实默认进入 `PROPOSED`，必须确认后才合并。
- backend-agent 到 backend-java 的内部提交可复用现有 `JAVA_API_BASE_URL` 和内部 API key 配置。

### 待确认问题

- 生产环境是否需要把“确认”限定为患者本人或医生角色：TBD。

## 6. 验收标准

- [x] 标准 1：聊天结束后，backend-agent 能从本轮对话中抽取结构化画像记忆候选并提交 Java 侧。
- [x] 标准 2：Java 侧持久化候选，保留来源、证据、字段路径、置信度、风险等级和状态。
- [x] 标准 3：用户可查询待确认候选，并确认或拒绝。
- [x] 标准 4：确认后的候选能合并到现有慢病画像、个人背景/偏好、症状或随访任务，并在后续 Agent 上下文中体现。
- [x] 标准 5：高风险事实不会未经确认直接进入主画像。

## 7. 受影响的系统和文件

- 项目 / 服务：backend-java、backend-agent、frontend
- 主要模块 / 文件：`PatientMemoryService`、`AgentPatientMemoryController`、`app/services/patient_memory.py`、`CareProfilePanel`
- API / 路由：`/api/patient-care/memories`、`/internal/agent/patient-memories`
- 数据库 / 表 / 字段：新增 `patient_memory_entries`；`patient_care_profiles.personal_context_json`
- 配置：复用 `JAVA_API_BASE_URL`、`JAVA_AGENT_API_KEY`、`JAVA_AGENT_API_KEY_HEADER`；Java 侧新增 `APP_AGENT_INTERNAL_API_KEY`、`APP_AGENT_INTERNAL_API_KEY_HEADER`
- 定时任务 / MQ / 外部依赖：无新增外部依赖

## 8. 实施方案

- 方案概述：Java 侧新增记忆账本与确认合并服务；Python 侧新增对话后抽取和提交；前端新增候选确认入口。
- 关键设计决定：主档案不直接由 LLM 覆盖；所有候选有证据和状态；已确认事实通过现有 Agent 上下文接口携带。
- 替代方案与取舍：未采用纯对话摘要或向量库作为事实源，因为医疗事实需要结构化、可追溯和可撤回。
- 风险：LLM 抽取质量需要持续评估；确认 UI 的权限边界后续需结合真实角色模型加强。

## 9. 实施计划

1. 新增 Java 数据表、实体、Mapper、DTO、服务和 API。
2. 扩展 Java 上下文聚合，返回相关待确认记忆。
3. 新增 backend-agent 抽取与提交逻辑，接入聊天结束后持久化流程。
4. 新增前端待确认记忆列表和操作。
5. 补充聚焦测试并更新最终交付记录。

## 10. 进度日志

- 2026-05-16：创建文档并确认初始范围。
- 2026-05-16：完成 Java 记忆账本、Python 抽取提交、前端确认入口和聚焦测试。
- 2026-05-16：审计后补齐 `personalContext` 个人背景/偏好字段，并将 Python 抽取器改为兼容项目当前流式 LLM 响应。
- 2026-05-16：审计后补齐 Java 内部 Agent API key 校验，避免患者记忆写入接口在生产配置下裸露。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java` 患者记忆模块 | 新增 `patient_memory_entries`、候选提交、查询、确认、拒绝和确认合并逻辑 | 标准 2、3、4、5 |
| `backend-java` 慢病画像 | 新增 `personal_context_json`，保存生活方式、照护情况、表达偏好等人物画像信息 | 标准 4 |
| `backend-java` 内部接口安全 | 新增 `InternalAgentApiGuard`，配置内部 API key 时保护 Agent 上下文和记忆提交接口 | 标准 2、5 |
| `backend-agent` 患者记忆抽取 | 对话持久化后调用流式兼容的 LLM 抽取候选，并提交 Java 内部接口 | 标准 1、2 |
| Agent 上下文聚合 | 上下文响应增加 `personalContext` 与 `pendingMemories`，Python 提示中明确待确认内容不能当作事实 | 标准 4、5 |
| 前端 Agent 工作台 | 在慢病画像面板展示个人背景/偏好与待确认画像更新，并支持确认 / 忽略 | 标准 3、4 |

## 12. 验证与测试

- 计划检查：Java 患者记忆 API 测试；Python 抽取提交测试；前端类型/单测或构建检查。
- 已完成检查：
  - `mvn -q -DskipTests compile`
  - `mvn -q "-Dtest=PatientMemoryServiceTest,PatientCareControllerTest,AgentContextControllerTest,AgentPatientMemoryControllerTest,InternalAgentApiGuardTest" test`
  - `mvn -q "-Dtest=PatientMemoryServiceTest,PatientCareControllerTest,AgentContextControllerTest" test`
  - `mvn -q test`（本机无 Docker，Testcontainers 输出 Docker 不可用日志，Maven 进程返回成功）
  - `uv run pytest tests/test_services/test_patient_memory.py tests/test_agent/test_context_guidance.py tests/test_agent/test_context_flow.py`（19 passed）
  - `npx tsc --noEmit`
  - `npm run build`
- 未运行 / 尚未验证：`npm run lint`
- 未验证原因：当前项目未配置 ESLint，`next lint` 进入交互式初始化提示，不能作为非交互检查执行。

## 13. 风险与后续事项

- 剩余风险：LLM 抽取质量仍需在真实问询样本上灰度评估；确认权限后续需按患者 / 医生角色细化。
- 后续事项：后续可增加记忆冲突检测、批量确认、审计报表和向量检索补充层。
- 阻塞项：无

## 14. 最终一致性检查

- 已交付的业务行为：每轮对话后可生成患者画像候选记忆；候选可在前端查看、确认或忽略；确认后合并进慢病画像、个人背景/偏好、症状或随访任务。
- 已交付的技术实现：Java 侧新增记忆账本与 API；Python Agent 新增抽取提交服务；前端新增待确认画像更新面板；Agent 上下文增加待确认记忆提示。
- 与原始计划的差异：未引入向量检索补充层；已补齐个人背景/偏好字段作为结构化画像的一部分。
- 验收标准满足情况：标准 1-5 已通过代码实现和聚焦测试覆盖。
- 证据与验证：见“验证与测试”。
- 未验证事项：独立 `next lint` 因 ESLint 未配置无法非交互运行；真实 LLM 抽取质量未用生产样本评估。
- 后续工作：增加冲突合并策略、医生角色审批、批量审核和真实样本评测集。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-05-16-patient-profile-memory-loop-review.md`
- 审查重点：医疗事实写入是否必须经过确认；候选证据和状态是否完整；Agent 上下文是否只使用已确认主画像作为事实。
- 已知需要审查的问题：生产角色权限边界和真实 LLM 抽取质量需后续结合样本与账户体系复核。
