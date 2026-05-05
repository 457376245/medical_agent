# Agent 趋势页报告分类选择

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-04
- 最后更新日期：2026-05-04
- 相关请求：`/agent/trends?profileId 页面中检验指标变化，下拉框应该选择的是报告分类，而不是具体的报告`
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：`docs/reviews/2026-05-04-agent-trends-category-selector-review.md`

## 2. 原始需求

- 用户原始诉求：`/agent/trends?profileId 页面中检验指标变化，下拉框应该选择的是报告分类，而不是具体的报告`
- 原始上下文：`/agent/trends` 当前趋势下拉框展示具体报告，用户期望选择报告分类。
- 后续补充：采用补后端接口方案；下拉只展示当前疾病档案有记录的报告分类。2026-05-04 追加：趋势页虽能看到曲线变化，但无法知道指标是否正常，需要复用趋势对比组件展示正常/异常状态。

## 3. 摘要

本需求将 Agent 趋势页的趋势选择入口从具体报告改为报告分类，并在趋势图中展示指标正常/异常状态。已新增按疾病档案和报告分类查询趋势的后端接口，前端下拉框展示当前档案下已有成功解析记录的分类，并复用趋势对比组件展示状态趋势、原始数值、参考范围和异常图例。

## 4. 背景和目标

- 业务背景：慢病追踪趋势应围绕同类报告的指标变化进行比较，用户在趋势页更关注报告分类，而不是单份报告标题。
- 用户 / 问题陈述：当前下拉框选择具体报告，交互语义与“检验指标变化”不匹配。
- 目标：让趋势页按报告分类选择趋势范围，展示指标趋势时同时呈现正常/异常状态，并保留现有具体报告趋势接口兼容性。
- 成功标准：下拉框只展示报告分类；选择分类后展示当前疾病档案下同分类报告的指标趋势；趋势图可判断正常、偏高、偏低、阈值异常或无法判定。

## 5. 范围边界

### 本次做

- 新增 `GET /api/agent/trends?profileId=&sourceType=&limit=`。
- 将 `/agent/trends` 页面下拉值从 `recordId` 改为 `sourceType`。
- 下拉范围限定为当前疾病档案下已有成功解析记录的报告分类。
- 复用趋势对比组件展示状态趋势、原始数值、参考范围和异常图例。
- 增加后端单测和前端构建验证。

### 本次不做

- 不改数据库 schema、迁移、表结构或索引。
- 不改认证、租户、患者隔离策略。
- 不改 `/api/records/{recordId}/trend` 现有行为。
- 不改上传、报告分类管理、时间线、Agent 问答和随访任务页面。

### 假设

- “报告分类”对应记录字段 `sourceType`。
- 当前档案已有记录分类来自 `AgentDashboardResponseData.sourceTypes`。
- 自定义报告分类直接显示原名称；已知枚举分类显示中文标签。

### 待确认问题

- 无。

## 6. 验收标准

- [x] 标准 1：`/agent/trends` 的下拉框展示报告分类，不展示具体报告标题和日期。
- [x] 标准 2：选择分类后前端调用分类级趋势接口，不再用用户选择的具体 `recordId` 调用趋势。
- [x] 标准 3：趋势图展示当前疾病档案下同一报告分类的指标时间序列。
- [x] 标准 4：当前档案无可用分类时下拉禁用并展示清晰空状态。
- [x] 标准 5：保留 `/api/records/{recordId}/trend` 现有行为兼容性。
- [x] 标准 6：缺失 `sourceType` 时后端返回明确 400 错误。
- [x] 标准 7：趋势图能展示指标正常/异常状态、参考范围和状态图例。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-java`、`frontend`
- 主要模块 / 文件：`AgentDashboardController`、`AgentDashboardService`、`/agent/trends` 页面
- API / 路由：新增 `GET /api/agent/trends?profileId=&sourceType=&limit=`
- 数据库 / 表 / 字段：无变更
- 配置：无变更
- 定时任务 / MQ / 外部依赖：无变更

## 8. 实施方案

- 方案概述：后端按 `profileId + sourceType` 找到当前档案下该分类最新成功解析记录，复用现有 `RecordService.fetchTrend` 生成趋势；前端下拉按分类请求新接口。
- 关键设计决定：不重写趋势算法；不把报告分类管理表作为下拉来源；保留旧 record trend 接口。
- 替代方案与取舍：仅前端映射分类到最新报告改动更小，但接口语义仍依赖具体报告；本次采用后端分类级接口以保证交互和 API 语义一致。
- 风险：当前工作区已有未提交改动，实施时需避免回滚或格式化无关文件。

## 9. 实施计划

1. 创建需求追踪文档，记录范围、验收标准和接口方案。
2. 新增后端分类级趋势接口和服务方法，并补充单测。
3. 更新 `/agent/trends` 页面下拉框、默认选择和趋势请求。
4. 运行目标测试，回写验证结果和最终一致性检查。

## 10. 进度日志

- 2026-05-04：创建文档并确认初始范围。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java/src/main/java/com/medical/agent/api/AgentDashboardController.java` | 新增 `GET /api/agent/trends`，校验 `sourceType` 并归一化 `limit` | 标准 2、3、6 |
| `backend-java/src/main/java/com/medical/agent/application/AgentDashboardService.java` | 新增按当前档案和报告分类查询趋势的服务方法，复用 `RecordService.fetchTrend` | 标准 2、3、5 |
| `backend-java/src/test/java/com/medical/agent/api/AgentDashboardControllerTest.java` | 覆盖分类趋势接口成功响应、`limit` 钳制和缺失 `sourceType` | 标准 2、6 |
| `backend-java/src/test/java/com/medical/agent/application/AgentDashboardServiceTest.java` | 覆盖按分类选择最新锚点记录和分类无记录空趋势 | 标准 3、4 |
| `frontend/src/app/agent/trends/page.tsx` | 下拉状态改为 `sourceType`，选项来自当前档案分类，并请求 `/agent/trends`；主图复用 `TrendComparisonPanel` | 标准 1、2、4、7 |
| `frontend/src/components/agent/types.ts` | 扩展趋势字段类型，保留 `numericValue`、`comparisonType`、`resultState`、参考上下限和 inclusive 字段 | 标准 3、7 |

## 12. 验证与测试

- 计划检查：`cd backend-java; mvn -Dtest=AgentDashboardControllerTest test`；`cd backend-java; mvn -Dtest=RecordControllerTest test`；`cd frontend; npm run build`
- 已完成检查：
  - `cd backend-java; mvn "-Dtest=AgentDashboardControllerTest,AgentDashboardServiceTest" test`：通过，5 个测试通过。
  - `cd backend-java; mvn "-Dtest=RecordControllerTest" test`：通过，7 个测试通过。
  - `cd frontend; npx tsc --noEmit --pretty false`：通过，无 TypeScript 输出错误。
  - 静态检索确认 `frontend/src/app/agent/trends/page.tsx` 已请求 `/agent/trends`，下拉文案为“选择报告分类”。
  - 2026-05-04 追加：`cd frontend; npx tsc --noEmit --pretty false`：通过，趋势页复用 `TrendComparisonPanel` 后类型检查通过。
  - 2026-05-04 追加：静态检索确认 `frontend/src/app/agent/trends/page.tsx` 不再引用 `AgentTrendChart`，已引用 `TrendComparisonPanel`。
- 未运行 / 尚未验证：
  - `cd frontend; npm run build` 未完成。
  - 未做浏览器手动验收。
- 未验证原因：
  - `npm run build` 在 Next.js 写入 `frontend/.next/trace` 时失败：`EPERM: operation not permitted, open 'F:\maven_product\medical_agent\frontend\.next\trace'`。尝试清理 `.next` 时同一文件仍被系统拒绝访问，判断为本地构建产物占用或权限问题。2026-05-04 追加验证时仍为同一错误。

## 13. 风险与后续事项

- 剩余风险：前端完整 Next.js production build 受本地 `.next/trace` 权限问题阻塞，尚未完成。
- 后续事项：释放或清理 `frontend/.next/trace` 占用后重跑 `cd frontend; npm run build`；如需要交付验收，建议运行 Requirement Doc Review。
- 阻塞项：无。

## 14. 最终一致性检查

- 已交付的业务行为：`/agent/trends` 趋势下拉按报告分类展示，选择分类后刷新该分类趋势，不再让用户选择具体报告；趋势图复用趋势对比组件展示正常/偏高/偏低/阈值异常/无法判定状态。
- 已交付的技术实现：新增 `GET /api/agent/trends`；前端按 `data.selectedProfile.profileId + sourceType` 请求分类趋势；旧 `/api/records/{recordId}/trend` 保持不变；前端保留趋势字段状态信息并传入 `TrendComparisonPanel`。
- 与原始计划的差异：无功能差异；前端 `npm run build` 因本地 `.next/trace` 权限问题未完成，改用 `tsc --noEmit` 做源码类型验证。
- 验收标准满足情况：标准 1-7 已通过代码实现和目标测试覆盖；完整前端 production build 尚未完成。
- 证据与验证：`AgentDashboardControllerTest`、`AgentDashboardServiceTest`、`RecordControllerTest` 通过；`npx tsc --noEmit --pretty false` 通过；静态检索确认趋势页复用 `TrendComparisonPanel`。
- 未验证事项：浏览器手动交互和 `npm run build`。
- 后续工作：解决 `.next/trace` 权限占用后补跑 `npm run build` 和浏览器验收。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/reviews/2026-05-04-agent-trends-category-selector-review.md`
- 审查重点：趋势页下拉是否按分类选择；分类级趋势接口是否复用现有趋势行为且保持兼容；趋势图是否展示正常/异常状态和参考范围；验证结果是否充分。
- 已知需要审查的问题：前端 `npm run build` 因本地 `.next/trace` 权限问题未完成，需要在释放构建产物占用后补充验证。
