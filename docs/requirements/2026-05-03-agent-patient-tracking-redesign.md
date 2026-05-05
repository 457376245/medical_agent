# Agent 患者慢病追踪与引导式咨询重设计

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-03
- 最后更新日期：2026-05-03
- 相关请求：重新设计 `/agent` 页面，可拆分多个页面，面向患者按多个维度跟踪和咨询医疗情况
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：希望完全重新设计 `/agent` 页面，可以将当前单一页面拆分成多个；系统面向患者，agent 页面用于让患者按多个维度跟踪和咨询自己的医疗情况；先业务采访确认需求后再设计 UI。
- 原始上下文：当前 `/agent` 是单页三栏工作台，包含会话、AI 问答、病例上下文、风险、照护档案、随访任务、症状记录。
- 后续补充：核心用户为慢病患者本人和家属/照护者；第一屏展示当前疾病总体状态；允许拆成 `/agent` 总览、`/agent/chat` 问答、`/agent/trends` 趋势、`/agent/tasks` 随访；患者一次关注一个疾病档案；追踪维度为检验指标趋势、症状记录、用药情况、风险预警；AI 采用引导式咨询；医疗安全边界轻提示；主要桌面端使用；允许前后端一起改并补齐数据能力。

## 3. 摘要

本需求将 `/agent` 从单页聊天工作台重构为疾病优先的患者慢病追踪入口，新增总览、问答、趋势和随访子页，并通过后端聚合接口提供当前疾病的风险、报告、趋势、症状、用药和待办数据。

## 4. 背景和目标

- 业务背景：患者和家属需要长期理解病情发展，而不是只在单个报告上提问。
- 用户 / 问题陈述：当前页面信息聚合在一个工作台中，难以让患者第一眼判断当前疾病状态、近期风险和下一步行动。
- 目标：以疾病档案为中心，提供低压力、可信、可追踪、可咨询的患者端体验。
- 成功标准：患者进入 `/agent` 后能选择疾病并看到总体状态；可进入引导式问答、指标趋势和随访任务页面；核心数据来自真实接口；旧会话和 SSE 聊天能力保留。

## 5. 范围边界

### 本次做

- 新增 `/agent`、`/agent/chat`、`/agent/trends`、`/agent/tasks` 的页面结构。
- 新增面向 `/agent` 的疾病总览聚合接口。
- 按疾病优先展示风险、任务、症状、报告和趋势；用药作为患者长期画像展示。
- 保留会话管理、疾病选择、AI 思考过程/工具调用过程、快捷问题。
- 使用温和陪伴、低压力的患者端视觉风格。

### 本次不做

- 不做跨疾病总览。
- 不做独立用药管理页面。
- 不新增诊断、处方或替代医生决策能力。
- 不重写 Agent SSE 协议和后端 Python Agent 图。

### 假设

- 当前患者上下文由既有认证和 PatientProvider 提供。
- 用药数据暂不按疾病拆分，展示为当前患者长期用药。
- 桌面端优先，移动端保证基础可用。

### 待确认问题

- 无。

## 6. 验收标准

- [x] 标准 1：`/agent` 为疾病健康总览，第一屏展示疾病、风险、最新报告、趋势亮点、近期症状、用药和待办摘要。
- [x] 标准 2：`/agent/chat` 提供引导式咨询，保留会话管理、快捷问题、AI trace 展示和消息发送。
- [x] 标准 3：`/agent/trends` 展示当前疾病下基于报告记录的指标趋势或清晰空状态。
- [x] 标准 4：`/agent/tasks` 展示并支持当前疾病的随访任务和症状记录，保留用药/目标摘要。
- [x] 标准 5：后端提供聚合接口并支持按 `profileId` 过滤症状和随访任务，旧接口未传 `profileId` 时保持兼容。
- [x] 标准 6：通过前后端相关测试或构建验证，未验证项明确记录。

## 7. 受影响的系统和文件

- 项目 / 服务：`frontend`、`backend-java`
- 主要模块 / 文件：`frontend/src/app/agent/**`、`frontend/src/components/agent/**`、`backend-java/src/main/java/com/medical/agent/api/**`、`backend-java/src/main/java/com/medical/agent/application/**`
- API / 路由：新增 `GET /api/agent/dashboard?profileId=...`；扩展 `/api/patient-care/follow-up-tasks`、`/api/patient-care/symptoms`
- 数据库 / 表 / 字段：复用现有疾病档案、记录、随访任务、症状、慢病画像表；不新增表字段
- 配置：无
- 定时任务 / MQ / 外部依赖：无

## 8. 实施方案

- 方案概述：后端新增聚合 DTO、服务和控制器，前端新增 agent shell 与三个子页，聊天页复用现有 workbench 逻辑并调整入口。
- 关键设计决定：疾病优先；聚合接口减少前端多接口拼装；旧接口保持兼容。
- 替代方案与取舍：只复用现有接口会导致总览页面加载状态分散，因此采用新增聚合接口。
- 风险：现有症状和用药数据模型偏患者全局，疾病关联不完整时需要通过空状态和标注降低误解。

## 9. 实施计划

1. 创建需求文档并梳理接口契约。
2. 实现后端聚合接口和 `profileId` 过滤。
3. 重构前端 `/agent` 路由和共享 shell。
4. 补充测试并运行验证。
5. 更新文档为实际交付状态。

## 10. 进度日志

- 2026-05-03：创建文档并确认初始范围，开始实施。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java/src/main/java/com/medical/agent/api/AgentDashboardController.java` | 新增 `GET /api/agent/dashboard` 聚合入口 | 标准 1、5 |
| `backend-java/src/main/java/com/medical/agent/application/AgentDashboardService.java` | 聚合疾病档案、记录、风险、任务、症状、用药、趋势亮点 | 标准 1、3、5 |
| `backend-java/src/main/java/com/medical/agent/domain/dto/response/AgentDashboardResponseData.java` | 定义总览页响应结构 | 标准 1、5 |
| `backend-java/src/main/java/com/medical/agent/application/PatientCareService.java` | 随访任务、症状和风险计算支持 `profileId` 过滤 | 标准 4、5 |
| `frontend/src/app/agent/page.tsx` | `/agent` 改为疾病健康总览页 | 标准 1 |
| `frontend/src/app/agent/chat/page.tsx` | 新增引导式咨询子页，复用现有 AgentWorkbench | 标准 2 |
| `frontend/src/app/agent/trends/page.tsx`、`frontend/src/components/agent/AgentTrendChart.tsx` | 新增趋势页和 ECharts 趋势图 | 标准 3 |
| `frontend/src/app/agent/tasks/page.tsx` | 新增随访、症状、用药与目标页 | 标准 4 |
| `frontend/src/components/agent/AgentPageFrame.tsx`、`frontend/src/app/globals.css` | 新增患者端 shell、疾病选择、子页导航和低压力视觉样式 | 标准 1、2、3、4 |
| `frontend/src/components/agent/useAgentDashboard.ts`、`frontend/src/components/agent/useCareSupport.ts` | 前端接入聚合接口和疾病范围过滤 | 标准 1、4、5 |
| `frontend/src/components/agent/AgentWorkflowBar.tsx`、`agent-utils.ts`、`types.ts` | 补齐报告解读、复诊准备、用药检查、异常原因四类引导式咨询场景 | 标准 2 |
| `backend-java/src/test/java/com/medical/agent/api/AgentDashboardControllerTest.java`、`PatientCareControllerTest.java`、`frontend/tests/agent-smoke.mjs` | 补充/更新接口和页面冒烟测试入口 | 标准 6 |

## 12. 验证与测试

- 计划检查：后端 Maven 测试；前端 Vitest；前端 build；必要时浏览器检查路由。
- 已完成检查：
  - `cd backend-java; mvn -q -Dtest=AgentDashboardControllerTest,PatientCareControllerTest test`：通过。
  - `cd backend-java; mvn -q test`：退出码 0；日志提示 Testcontainers 未找到 Docker 环境。
  - `cd backend-java; mvn -q -DskipTests compile`：通过。
  - `cd frontend; npm run build`：通过，生成 `/agent`、`/agent/chat`、`/agent/trends`、`/agent/tasks`。
  - `cd frontend; npx vitest run`：3 个测试文件、15 个测试通过。
  - 启动前端开发服务后访问 `http://127.0.0.1:3000/agent`：HTTP 200。
- 未运行 / 尚未验证：
  - `npm run smoke:agent` 未通过。
  - 未做登录态下的浏览器视觉截图验收。
- 未验证原因：
  - Playwright Chromium 可执行文件未安装，`npm run smoke:agent` 提示需要 `npx playwright install`。
  - 当前验证仅确认路由 HTTP 200，未注入真实登录 token 和后端业务数据进行端到端视觉检查。

## 13. 风险与后续事项

- 剩余风险：用药仍是患者全局画像，不按疾病拆分；历史任务/症状如果未绑定 `diseaseProfileId`，疾病优先页面不会展示它们。
- 后续事项：如需完整视觉验收，安装 Playwright 浏览器并准备登录态测试数据后运行更新后的 `smoke:agent`。
- 阻塞项：无

## 14. 最终一致性检查

- 已交付的业务行为：患者以单个疾病档案为中心进入 `/agent`，可查看健康总览、进入报告解读/复诊准备/用药检查/异常原因四类 AI 咨询、查看趋势、管理随访和症状记录。
- 已交付的技术实现：新增后端聚合接口和前端四个 agent 路由；复用现有 Agent 对话、会话、trace、随访任务、症状和慢病画像能力。
- 与原始计划的差异：聊天页复用现有 `AgentWorkbench`，没有在本轮彻底拆解对话内部三栏结构；这是为了保留 SSE、会话和 trace 稳定性。
- 验收标准满足情况：标准 1-5 已通过代码实现；标准 6 已完成构建、单元/控制器测试和基础 HTTP 验证，但 Playwright 冒烟因本地浏览器缺失未完成。
- 证据与验证：见“验证与测试”。
- 未验证事项：登录态真实数据下的桌面/移动端截图和交互验收。
- 后续工作：安装 Playwright 浏览器并补充端到端登录态测试；如产品需要，可进一步将用药改为疾病关联模型。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/reviews/2026-05-03-agent-patient-tracking-redesign-review.md`
- 审查重点：业务范围是否完整落地；聚合接口是否与前端页面一致；医疗安全边界是否符合轻提示要求。
- 已知需要审查的问题：Playwright 冒烟未完成；聊天页内部仍沿用既有三栏工作台。
