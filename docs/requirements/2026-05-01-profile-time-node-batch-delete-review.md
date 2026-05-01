# 需求达标审查报告：疾病时间节点报告批量删除

## 1. 审查输入

- 需求实施追踪文档：`docs/requirements/2026-05-01-profile-time-node-batch-delete.md`
- 工作区：`F:\maven_product\medical_agent`
- 分支 / 提交：`master`，未提交变更
- 审查时间：2026-05-01
- 审查类型：最终审查

## 2. 审查结论

- 结论：有条件通过
- 总体说明：7 条验收标准均有代码和测试支撑，核心批量删除逻辑正确实现了疾病档案范围校验和级联清理。存在两处文档偏差和一处测试缺口，均为非阻塞问题。

## 3. 阻塞问题

无。

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：每个有报告的时间节点右侧出现批量删除按钮 | 前端时间节点列表新增批量删除按钮 | `DiseaseTimelineView.tsx:640-649`，按钮位于 `.date-node-row` 内，`.date-node-btn` 右侧 | CSS `.date-node-delete-btn` 样式已添加（`globals.css:1715-1717`），移动端自适应布局已处理 | 通过 |
| 标准 2：点击批量删除会弹出确认框，展示节点日期和报告数量 | 确认弹窗展示节点日期和报告数量 | `DiseaseTimelineView.tsx:791-803`，`ConfirmDialog` 展示 `deleteTargetNode.displayDate` 和 `deleteTargetNode.categories.length` | 前端 TypeScript 编译通过（`tsc --noEmit`） | 通过 |
| 标准 3：确认后删除该节点下全部报告及关联解析、生成、资产数据 | 后端级联清理 records、assets、parse_jobs、parse_job_assets、structured_results、generated_outputs、data_rights_requests | `DiseaseProfileService.java:222-270`（`deleteProfileRecords` 方法）；`DiseaseProfileService.java:290-315`（`deleteRecordsCascadeInternal` 级联删除）；前端调用 `DELETE /api/disease-profiles/{profileId}/records`（`DiseaseTimelineView.tsx:303-309`） | `DiseaseProfileServiceCancelParsingTest.java:302-337`（`deleteProfileRecordsDeletesScopedRecordsAndAssets`）验证级联删除 records、assets、parse_jobs、parse_job_assets | 通过 |
| 标准 4：目标节点删除成功后从列表消失；若当前选中的是该节点，详情状态被清空 | 刷新疾病档案记录数据，清理选中节点详情状态 | `DiseaseTimelineView.tsx:316-333`：本地 `mutableRecords` 和 `mutableExamNodes` 过滤已删记录，若 `selectedDate` 匹配则调用 `clearSelectedReportState()`；`page.tsx:92-94`：`onRecordsDeleted` 回调调用 `loadProfileData()` 重新加载 | 前端 TypeScript 编译通过；逻辑可审查 | 通过 |
| 标准 5：其他时间节点、其他疾病档案或不在请求列表内的报告不被删除 | 后端统一校验当前租户、患者、疾病档案范围 | `DiseaseProfileService.java:232-237`：查询条件包含 `tenantId`、`patientId`、`diseaseProfileId` 和 `recordIds`；`DiseaseProfileService.java:243-249`：不在范围内的 recordId 进入 `rejectedRecordIds` 并整体拒绝 | `DiseaseProfileServiceCancelParsingTest.java:283-299`（`deleteProfileRecordsRejectsRecordsOutsideProfileWithoutDeleting`）验证跨档案记录被拒绝且不执行删除 | 通过 |
| 标准 6：请求中包含非法、缺失或越权记录 ID 时不执行部分删除，并返回明确错误 | 控制器校验 profileId、recordIds 并映射错误响应 | `DiseaseProfileController.java:296-343`：校验 profileId UUID 格式（400）、recordIds 非空（400）、recordId UUID 格式（400）；`DiseaseProfileController.java:361-374`：越权记录返回 404 并附 `rejectedRecordIds` | `DiseaseProfileControllerTest.java:41-125`：5 个测试覆盖非法 profileId、空 recordIds、非法 recordId、跨档案拒绝、成功删除 | 通过 |
| 标准 7：新增能力有针对性后端和前端验证记录 | 增加后端和前端针对性测试 | 后端：`DiseaseProfileControllerTest.java`（5 个测试）、`DiseaseProfileServiceCancelParsingTest.java`（新增 2 个批量删除测试）；前端：`timelineGrouping.test.ts`（新增 `recordIdsForGroupedDateItem` 测试） | `mvn "-Dtest=DiseaseProfileControllerTest,DiseaseProfileServiceCancelParsingTest" test` 通过（14 个测试）；`vitest run src/components/profiles/timelineGrouping.test.ts` 通过（4 个测试） | 通过 |

## 5. 文档与代码一致性

- 文档准确的地方：
  - 新增接口路径 `DELETE /api/disease-profiles/{profileId}/records` 与代码一致（`DiseaseProfileController.java:290`）。
  - 请求/响应 DTO 字段与代码一致。
  - 级联删除范围（records、assets、parse_jobs、parse_job_assets、structured_results、generated_outputs、data_rights_requests）与 `deleteRecordsCascadeInternal` 实现一致。
  - 前端 `recordIdsForGroupedDateItem` helper 函数已新增并被批量删除流程使用。
  - CSS 布局调整支持选择区域和批量删除按钮并列展示。

- 文档过时或不准确的地方：
  - 代码变更清单（第 11 节）列出 `DiseaseProfileQueryService.java` 有变更，但实际 diff 仅为空白/CRLF 格式化，不涉及本次需求功能。清单应移除该条目或注明为格式调整。
  - 代码变更清单列出 `DiseaseProfileQueryServiceTest.java` 有变更，同上仅为格式调整。

- 文档遗漏：
  - 无。

- 代码中存在但文档未记录的变更：
  - `backend-agent/app/mq/consumer.py` 有 198 行变更，未在文档中说明。该文件不属于本次需求范围（文档仅涉及 backend-java 和 frontend），但实际已修改，需确认是否为同期无关变更。

## 6. 实现问题

- 问题：`deleteProfileRecords` 未使用 `@Transactional` 注解
- 严重级别：低
- 文件 / 行号：`DiseaseProfileService.java:222`
- 原因：该方法调用 `deleteRecordsCascadeInternal` 进行多表删除操作。虽然 `deleteRecordsCascadeInternal` 是私有方法且在同一类中调用，Spring 的 `@Transactional` 注解在同一类内部调用时不会生效（代理拦截问题）。但该方法本身标注了 `@Transactional`（第 221 行），实际上由于是同类内部调用 `deleteRecordsCascadeInternal`，事务注解在 `deleteProfileRecords` 入口处生效，因此级联删除在同一事务中。经核实，`deleteProfileRecords` 方法确实有 `@Transactional` 注解（第 221 行），此条不构成问题。
- 建议：无需修改。

## 7. 测试与验证缺口

- 已有验证：
  - 后端控制器单元测试 5 个（`DiseaseProfileControllerTest`）：覆盖非法 profileId、空 recordIds、非法 recordId、跨档案拒绝、成功删除。
  - 后端服务层单元测试 2 个新增（`DiseaseProfileServiceCancelParsingTest`）：覆盖跨档案拒绝删除、成功级联删除及资产清理。
  - 前端纯函数测试 1 个新增（`timelineGrouping.test.ts`）：验证 `recordIdsForGroupedDateItem` 提取同分类多报告不丢失。
  - TypeScript 编译通过（`tsc --noEmit`）。
  - Git whitespace check 通过。

- 缺失验证：
  - 未启动真实前后端服务做浏览器手工回归（文档已说明原因：Node.js 版本 17.9.1 低于 Next.js 14 要求的 18.17.0）。
  - 未运行完整 `mvn test` 或完整前端构建。
  - 前端 `DiseaseTimelineView` 组件无单元测试覆盖批量删除交互流程（确认弹窗、API 调用、本地状态更新、错误处理）。

- 无法确认的验证：
  - OSS 文件删除在真实环境中是否正常工作（测试中通过 mock 验证）。
  - 浏览器中批量删除按钮布局在不同屏幕尺寸下的实际表现。

- 建议补充：
  - 在 Node.js 版本升级后补充浏览器手工回归验证。
  - 可选：补充 `DiseaseTimelineView` 组件的批量删除交互测试。

## 8. 风险与后续事项

- 交付风险：
  - OSS 删除与数据库事务无法天然保持分布式原子性，沿用项目现有删除逻辑处理方式，风险与已有删除功能一致。
  - 未做浏览器手工验证，UI 交互和布局问题可能在真实环境中才暴露。

- 后续事项：
  - 建议在联调环境启动服务后执行一次浏览器手工回归。
  - `backend-agent/app/mq/consumer.py` 的变更需确认是否为同期无关改动，避免混淆提交范围。
  - `DiseaseProfileQueryService.java` 和 `DiseaseProfileQueryServiceTest.java` 的空白变更可考虑在提交前还原，避免无关 diff。

- 是否需要更新需求实施追踪文档：
  - 建议更新代码变更清单，移除 `DiseaseProfileQueryService.java` 和 `DiseaseProfileQueryServiceTest.java` 的条目（或注明为格式调整）。
  - 建议在进度日志中补充测试验证结果。

## 9. 最终建议

- 是否可以交付：是，核心功能完整，验收标准全部通过。
- 交付前必须修复：无。
- 可后续优化：
  - 补充浏览器手工回归验证。
  - 清理代码变更清单中的无关文件条目。
  - 考虑补充前端组件级批量删除交互测试。
