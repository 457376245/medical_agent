# 疾病时间节点报告批量删除需求实施追踪

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-01
- 最后更新日期：2026-05-01
- 相关请求：`/profiles` 给时间节点后面添加按钮，用于批量删除该时间节点下的报告；用户确认采用后端批量接口方案。
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：在 `/profiles` 页面时间节点后面添加按钮，用于批量删除该时间节点下的报告。
- 原始上下文：疾病档案详情页左侧按检查时间节点展示报告数量，当前只能在选中单份报告后删除该报告。
- 后续补充：删除语义采用后端批量接口，避免前端循环单删导致部分成功。

## 3. 摘要

本需求为疾病档案报告时间线增加按时间节点批量删除能力。用户可在时间节点列表中直接删除该节点下所有报告；后端提供按疾病档案和记录 ID 列表删除的接口，统一校验当前租户、患者和疾病档案范围，并级联清理报告关联数据。

## 4. 背景和目标

- 业务背景：一次检查时间节点下可能包含多份报告，逐份进入详情删除效率低。
- 用户 / 问题陈述：用户需要在时间节点层级一次性删除节点内所有报告。
- 目标：在 `/profiles/{profileId}` 时间节点列表中提供明确的批量删除入口，删除后页面状态与后端保持一致。
- 成功标准：用户确认后可删除目标时间节点全部报告；不会删除其他时间节点或其他疾病档案报告；删除失败时可感知。

## 5. 范围边界

### 本次做

- 新增疾病档案下按记录 ID 列表批量删除报告的后端接口。
- 前端时间节点列表新增批量删除按钮和确认弹窗。
- 删除成功后刷新疾病档案记录数据，并清理当前选中节点的详情状态。
- 增加后端和前端针对性测试。

### 本次不做

- 不新增数据库表、字段或软删除机制。
- 不新增恢复、回收站、撤销删除或批量选择部分报告能力。
- 不改变单报告删除接口语义。
- 不改变检查时间节点聚合规则。

### 假设

- “该时间节点下的报告”以当前前端 `GroupedDateItem.categories` 内的全部 `record.id` 为准。
- 删除是不可恢复操作，必须二次确认。
- 后端批量删除要求全部记录通过校验才执行删除。

### 待确认问题

- 无。

## 6. 验收标准

- [ ] 标准 1：每个有报告的时间节点右侧出现批量删除按钮。
- [ ] 标准 2：点击批量删除会弹出确认框，展示节点日期和报告数量。
- [ ] 标准 3：确认后删除该节点下全部报告及关联解析、生成、资产数据。
- [ ] 标准 4：目标节点删除成功后从列表消失；若当前选中的是该节点，详情状态被清空。
- [ ] 标准 5：其他时间节点、其他疾病档案或不在请求列表内的报告不被删除。
- [ ] 标准 6：请求中包含非法、缺失或越权记录 ID 时不执行部分删除，并返回明确错误。
- [ ] 标准 7：新增能力有针对性后端和前端验证记录。

## 7. 受影响的系统和文件

- 项目 / 服务：backend-java、frontend
- 主要模块 / 文件：
  - `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java`
  - `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java`
  - `frontend/src/components/profiles/DiseaseTimelineView.tsx`
  - `frontend/src/app/profiles/[profileId]/page.tsx`
- API / 路由：新增 `DELETE /api/disease-profiles/{profileId}/records`
- 数据库 / 表 / 字段：使用现有 `records`、`assets`、`parse_jobs`、`parse_job_assets`、`structured_results`、`generated_outputs`、`data_rights_requests`；不新增迁移。
- 配置：无。
- 定时任务 / MQ / 外部依赖：删除已上传文件时沿用现有 OSS 删除服务。

## 8. 实施方案

- 方案概述：后端新增疾病档案范围内的批量删除接口，前端时间节点按钮收集节点内 record IDs 调用该接口。
- 关键设计决定：
  - 采用后端批量接口而非前端循环单删。
  - 所有 record IDs 必须属于当前租户、当前患者、当前疾病档案，否则整体失败。
  - 复用现有级联删除范围，不引入软删除。
- 替代方案与取舍：前端循环调用单删改动更少，但会产生部分成功和状态不一致风险，已放弃。
- 风险：OSS 删除与数据库事务无法天然保持分布式原子性，本次沿用项目现有删除逻辑处理方式。

## 9. 实施计划

1. 创建需求追踪文档。
2. 后端新增请求/响应 DTO、服务方法和控制器接口。
3. 增加后端单元或集成测试覆盖成功、非法输入和跨档案防误删。
4. 前端时间节点列表新增删除按钮、确认弹窗、调用接口和刷新状态。
5. 运行针对性测试并更新文档。

## 10. 进度日志

- 2026-05-01：创建文档并确认后端批量接口方案。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java/src/main/java/com/medical/agent/domain/dto/request/DeleteDiseaseProfileRecordsRequest.java` | 新增批量删除请求体 DTO，接收 `recordIds` | 标准 3、6 |
| `backend-java/src/main/java/com/medical/agent/domain/dto/response/DiseaseProfileRecordsDeleteResponseData.java` | 新增批量删除响应 DTO，返回删除统计和被拒绝 ID | 标准 3、6 |
| `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java` | 新增 `DELETE /api/disease-profiles/{profileId}/records`，校验 profileId、recordIds 并映射错误响应 | 标准 3、5、6 |
| `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java` | 新增疾病档案范围内的批量删除服务方法，统一校验当前租户、患者、疾病档案并复用级联清理 | 标准 3、5、6 |
| `backend-java/src/test/java/com/medical/agent/api/DiseaseProfileControllerTest.java` | 新增控制器测试，覆盖非法输入、跨档案拒绝和成功响应 | 标准 6、7 |
| `backend-java/src/test/java/com/medical/agent/application/DiseaseProfileServiceCancelParsingTest.java` | 增加服务层批量删除测试，覆盖跨档案不删除、成功删除和资产清理 | 标准 3、5、7 |
| `frontend/src/components/profiles/timelineGrouping.ts` | 新增节点内记录 ID 提取 helper，供批量删除复用 | 标准 3 |
| `frontend/src/components/profiles/timelineGrouping.test.ts` | 增加节点批量删除 ID 提取测试，确保同分类多报告不丢失 | 标准 3、7 |
| `frontend/src/components/profiles/DiseaseTimelineView.tsx` | 时间节点列表新增批量删除按钮、确认弹窗、接口调用、本地状态清理和刷新回调 | 标准 1、2、3、4 |
| `frontend/src/app/profiles/[profileId]/page.tsx` | 向时间线组件传入删除后重新加载疾病档案数据的回调 | 标准 4 |
| `frontend/src/app/globals.css` | 调整时间节点行布局，支持选择区域和批量删除按钮并列展示 | 标准 1 |

## 12. 验证与测试

- 计划检查：
  - 后端批量删除接口单元测试或集成测试。
  - 前端分组/按钮相关测试或构建验证。
  - 针对性 Maven、npm 测试。
- 已完成检查：
  - `mvn "-Dtest=DiseaseProfileControllerTest,DiseaseProfileServiceCancelParsingTest" test`：通过，14 个测试通过。
  - `& 'D:\nodejs\node.exe' '.\node_modules\vitest\vitest.mjs' run src/components/profiles/timelineGrouping.test.ts`：通过，4 个测试通过；沙箱内 Vite/esbuild spawn 报 `EPERM`，已在批准后沙箱外复跑通过。
  - `& 'D:\nodejs\node.exe' '.\node_modules\typescript\bin\tsc' --noEmit`：通过，无输出。
  - `git diff --check`：通过，无空白错误；仅输出现有 CRLF 提示。
- 未运行 / 尚未验证：
  - 未启动真实前后端服务做浏览器手工回归。
  - 未运行完整 `mvn test` 或完整前端构建。
- 未验证原因：
  - 本次改动已有针对性控制器、服务层、前端纯函数和 TypeScript 验证；真实页面联调需要启动完整服务和可用测试数据。
  - 本机 `D:\nodejs\node.exe` 版本为 17.9.1，Next.js 14 要求 Node.js >= 18.17.0，前端 dev server 未能启动。

## 13. 风险与后续事项

- 剩余风险：OSS 删除与数据库事务仍沿用项目既有删除方式，不提供分布式原子性；若 OSS 删除成功后数据库删除失败，需要按现有运维手段处理。
- 后续事项：可在后续需求中补充端到端浏览器回归或接口契约文件；当前仓库未找到历史 `specs/001-medical-agent-mvp/contracts/openapi.yaml`。
- 阻塞项：暂无。

## 14. 最终一致性检查

- 已交付的业务行为：疾病档案时间节点列表每个节点显示批量删除按钮；确认后删除该节点下全部报告，并在删除当前选中节点时清空详情状态。
- 已交付的技术实现：新增 `DELETE /api/disease-profiles/{profileId}/records`，请求体为 `recordIds`；后端统一校验疾病档案和记录归属，级联清理关联数据；前端调用接口并刷新疾病档案记录。
- 与原始计划的差异：无实质差异；未新增 OpenAPI 静态契约文件，因为仓库当前不存在对应 specs 目录。
- 验收标准满足情况：标准 1-7 已通过实现和针对性测试覆盖；真实浏览器手工验证未运行。
- 证据与验证：见“验证与测试”章节。
- 未验证事项：真实页面点击链路、真实 OSS 删除链路和完整构建未验证；前端 dev server 因本机 Node.js 版本过低未启动。
- 后续工作：建议在联调环境启动服务后执行一次浏览器手工回归。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-05-01-profile-time-node-batch-delete-review.md`
- 审查重点：批量删除是否只作用于目标时间节点；接口是否避免部分删除；文档和实际实现是否一致。
- 已知需要审查的问题：真实浏览器交互和完整联调尚未执行。
