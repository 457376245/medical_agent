# 取消卡住的报告解析并清理链路文件 — 需求实施追踪

## 1. 元数据

- 状态：实施完成，待审查
- 负责人：Claude Code
- 开始日期：2026-04-30
- 最后更新日期：2026-04-30
- 相关请求：疾病档案详情页报告解析卡住后，用户无法清除"正在解析中"状态；需要提供取消解析能力，删除非成功解析记录及其关联数据和文件。
- 相关分支 / 提交 / PR：`temp-remaining-work-20260430`，未提交
- 需求达标审查报告：`docs/requirements/2026-04-30-cancel-stuck-report-parsing-review.md`

## 2. 原始需求

- 用户原始诉求：疾病档案详情页"正在解析中：N 份报告..."长期不消失，用户需要取消卡住的解析并清理关联数据和文件。
- 原始上下文：项目为 medical_agent，用户上传报告后触发解析，但部分报告因各种原因卡在 QUEUED/RETRYING/FAILED/DEAD_LETTER 状态，前端永久显示"正在解析中"。
- 后续补充：取消语义为"删除链路数据"而非标记取消；不新增 CANCELED 状态；不改 Python agent；已投递或正在运行的 Python 任务无法主动中断，晚到结果因 parse_job 已删除而被忽略。

## 3. 摘要

本需求在疾病档案详情页新增"取消解析"按钮，允许用户一键删除当前档案下所有非成功解析的报告记录及其关联资产、解析任务、解析结果、生成内容和对象存储文件。后端新增 `DELETE /api/disease-profiles/{profileId}/parsing-records` 接口，前端在 `parsingCount > 0` 时展示取消按钮和确认弹窗。

## 4. 背景和目标

- 业务背景：用户上传报告后系统自动触发解析，但解析可能因网络、服务异常等原因卡住，导致前端永久显示"正在解析中"。
- 用户 / 问题陈述：用户无法清除卡住的解析状态，无法重新上传同一报告。
- 目标：提供一键取消能力，删除所有非成功解析记录及关联数据，恢复干净状态。
- 成功标准：用户点击"取消解析"后，`parsingCount` 归零，卡住记录及其关联数据和文件被清除，成功解析的报告不受影响。

## 5. 范围边界

### 本次做

- 后端新增 `DELETE /api/disease-profiles/{profileId}/parsing-records` 接口。
- 接口按 tenant + patient + profile 维度筛选候选记录，排除 SUCCESS 记录。
- 级联删除候选记录的 data_rights_requests、structured_results、generated_outputs、parse_job_assets、parse_jobs、assets、records。
- 删除候选记录关联的对象存储文件。
- 前端在"正在解析中：N 份报告..."旁新增"取消解析"按钮。
- 点击按钮弹出确认弹窗，确认后调用取消接口，成功后刷新页面数据。
- 支持 `unknown` profileId（未分类疾病档案）。

### 本次不做

- 不新增 CANCELED 状态或数据库迁移。
- 不改 Python agent 或正常上传、正常解析流程。
- 不改成功解析报告的展示、趋势、AI 分析或 Agent 对话语义。
- 不重构上传流程、时间线聚合逻辑、认证/租户上下文。
- 不主动中断已投递或正在运行的 Python 解析任务。

### 假设

- 用户确认取消范围为"当前疾病档案全部非成功解析报告"。
- 用户确认取消语义为"删除链路数据"，而不是保留记录并标记取消。
- 对象存储删除沿用现有 `OssPresignService.deleteObject` 的 best-effort/no-op 语义。
- 解析中任务无法从 RabbitMQ/Python worker 主动撤回；本需求接受"系统侧删除并忽略晚到结果"的取消语义。

### 待确认问题

- 无。

## 6. 验收标准

- [x] 标准 1：前端 `parsingCount > 0` 时，在"正在解析中：N 份报告..."旁显示"取消解析"按钮；`parsingCount = 0` 时不显示。
- [x] 标准 2：点击按钮弹出确认弹窗，文案明确"将删除这 N 份未完成解析的报告、关联解析数据和已上传文件，删除后不可恢复"。
- [x] 标准 3：确认后调用 `DELETE /api/disease-profiles/{profileId}/parsing-records`，成功后刷新 records、examNodes、parsingCount。
- [x] 标准 4：后端接口只删除当前档案下非成功解析记录（NOT_PARSED、QUEUED、RETRYING、FAILED、DEAD_LETTER），SUCCESS 记录保留。**测试验证：`cancelDeletesOnlyNonSuccessRecordsAndPreservesSuccess`、`cancelReturnsZeroCountsWhenAllRecordsAreSuccess`。**
- [x] 标准 5：级联删除 data_rights_requests、structured_results、generated_outputs、parse_job_assets、parse_jobs、assets、records。**测试验证：通过 mock verify 确认所有 mapper.delete 被调用。**
- [x] 标准 6：调用 `OssPresignService.deleteObject` 删除候选记录关联的对象存储文件。**测试验证：`cancelCallsOssDeleteForEveryCandidateAsset`。**
- [x] 标准 7：返回 `deletedRecordCount`、`deletedAssetCount`、`deletedParseJobCount`。**测试验证：所有测试断言返回计数。**
- [x] 标准 8：`unknown` profileId 支持未分类疾病档案的取消操作。**测试验证：`cancelWithUnknownProfileIdQueriesForNullDiseaseProfileId`。**
- [x] 标准 9：UUID 格式错误返回 400，不存在档案返回 404，无可取消记录返回 200 且计数为 0。**代码实现已覆盖；集成测试需 Docker 环境。**
- [x] 标准 10：不新增数据库迁移或 parse job 状态枚举。**代码验证：diff 无迁移文件或枚举修改。**
- [x] 标准 11：不改 backend-agent。**代码验证：diff 无 backend-agent 修改。**
- [ ] 标准 12：取消后晚到的 MQ 解析结果不会恢复已删除数据。**代码实现依赖 `applyParseResult` 的 `ResourceNotFoundException`；需集成测试验证。**

## 7. 受影响的系统和文件

- 项目 / 服务：backend-java、frontend
- 主要模块 / 文件：
  - `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java` — 新增取消接口
  - `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java` — 新增取消逻辑和级联删除
  - `backend-java/src/main/java/com/medical/agent/domain/dto/response/DiseaseProfileParsingCancelResponseData.java` — 新增响应 DTO
  - `backend-java/src/test/java/com/medical/agent/application/DiseaseProfileServiceCancelParsingTest.java` — 新增服务测试
  - `frontend/src/components/profiles/DiseaseTimelineView.tsx` — 新增取消按钮和确认弹窗
  - `frontend/src/app/profiles/[profileId]/page.tsx` — 传入刷新回调
- API / 路由：`DELETE /api/disease-profiles/{profileId}/parsing-records`
- 数据库 / 表 / 字段：只读使用 `records`、`parse_jobs`、`assets`、`parse_job_assets`、`structured_results`、`generated_outputs`、`data_rights_requests`；不新增表或字段。
- 配置：无变更。
- 定时任务 / MQ / 外部依赖：不改 ParseRetryScheduler、ParseResultConsumer；晚到结果因 parse_job 已删除而在 `applyParseResult` 中抛出 `ResourceNotFoundException`，被 `ParseResultConsumer.consume` 的 catch 块捕获并记录日志。

## 8. 实施方案

- 方案概述：后端在 DiseaseProfileService 新增 `cancelParsingRecords` 方法，按 tenant + patient + profile 筛选候选记录，排除 SUCCESS，执行级联删除和 OSS 清理；前端在 DiseaseTimelineView 新增取消按钮和确认弹窗。
- 关键设计决定：
  - 不新增 CANCELED 状态，直接删除记录，与现有 `deleteProfile` 级联删除模式一致。
  - 复用 `queryLatestParseStatus` 逻辑判断候选记录，与 `parsingCount` 统计口径一致。
  - OSS 删除在数据库删除前执行，沿用 `deleteProfile` 的现有模式（best-effort）。
  - 晚到 MQ 结果通过 `applyParseResult` 的 `ResourceNotFoundException` 被 catch 忽略。
- 替代方案与取舍：
  - 标记取消而非删除：保留记录历史但需新增状态、修改查询逻辑、处理状态流转，复杂度高且用户诉求是"清理"。
  - 前端逐条调用删除接口：多次请求、无原子性、用户体验差。
- 风险：
  - OSS 删除在数据库事务前执行，若 DB 删除失败则文件已删但记录回滚（沿用现有 `deleteProfile` 模式，best-effort）。
  - 已投递到 Python agent 的任务无法撤回，晚到结果依赖 parse_job 记录已被删除来忽略。

## 9. 实施计划

1. 创建响应 DTO `DiseaseProfileParsingCancelResponseData`。
2. 在 `DiseaseProfileService` 新增 `cancelParsingRecords` 方法和 `deleteRecordsCascadeInternal` 辅助方法。
3. 在 `DiseaseProfileController` 新增 `DELETE /{profileId}/parsing-records` 端点。
4. 前端 `DiseaseTimelineView` 新增 `onParsingCanceled` prop、取消按钮、确认弹窗。
5. 前端 `page.tsx` 提取 `loadProfileData` 函数，传入刷新回调。
6. 补充后端服务测试。
7. 运行编译和测试验证。

## 10. 进度日志

- 2026-04-30：完成需求分析和代码侦察，确认级联删除模式、parsingCount 统计口径、ParseResultConsumer 晚到结果处理。
- 2026-04-30：完成后端 DTO、Service、Controller 实现。
- 2026-04-30：完成前端取消按钮、确认弹窗、刷新回调。
- 2026-04-30：完成 `mvn compile` 和 `npx tsc --noEmit`、`npm test` 验证。
- 2026-04-30：根据审查报告补建本文档、补充 7 个后端服务测试、清理未使用 import 和构建缓存。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java/.../domain/dto/response/DiseaseProfileParsingCancelResponseData.java` | 新增取消解析响应 DTO | 标准 7 |
| `backend-java/.../application/DiseaseProfileService.java` | 新增 `cancelParsingRecords`、`queryLatestParseStatus`、`deleteRecordsCascadeInternal` 方法 | 标准 4、5、6 |
| `backend-java/.../api/DiseaseProfileController.java` | 新增 `DELETE /{profileId}/parsing-records` 端点，含 UUID 校验和档案存在性检查 | 标准 8、9 |
| `backend-java/.../application/DiseaseProfileServiceCancelParsingTest.java` | 新增 7 个服务级测试：SUCCESS 保留、非 SUCCESS 删除、FAILED/DEAD_LETTER/NOT_PARSED 覆盖、跨档案隔离、OSS 删除验证、unknown profileId、空结果 | 标准 4、5、6、7、8 |
| `frontend/src/components/profiles/DiseaseTimelineView.tsx` | 新增 `onParsingCanceled` prop、取消按钮、确认弹窗、错误状态 | 标准 1、2、3 |
| `frontend/src/app/profiles/[profileId]/page.tsx` | 提取 `loadProfileData`，传入 `onParsingCanceled` 刷新回调 | 标准 3 |

## 12. 验证与测试

- 计划检查：
  - 后端服务级测试：候选记录筛选、SUCCESS 保留、跨档案隔离、OSS 删除调用、晚到结果处理。
  - 前端构建验证和类型检查。
- 已完成检查：
  - `cd backend-java; JAVA_HOME="D:/JDK21" mvn test`：通过（93 测试，0 失败，15 skipped 因无 Docker）。
  - `cd frontend; npx tsc --noEmit`：通过。
  - `cd frontend; npm test -- --run`：通过（3 文件，13 测试）。
  - 后端服务测试 `DiseaseProfileServiceCancelParsingTest`：7 测试全部通过，覆盖 SUCCESS 保留、QUEUED/FAILED/DEAD_LETTER/NOT_PARSED 删除、跨档案隔离、OSS 删除调用、unknown profileId、空结果返回。
- 未运行 / 尚未验证：
  - `mvn test` 集成测试因本机无 Docker 跳过 Testcontainers 用例。
  - 前端交互测试（按钮显示、确认弹窗、取消/确认/失败行为）未自动化。
- 未验证原因：
  - Testcontainers 需要 Docker 环境，本机未安装。
  - 前端交互测试需要浏览器 E2E 环境。

## 13. 风险与后续事项

- 剩余风险：
  - OSS 删除在 DB 事务前执行，失败时可能留下 DB 有记录但文件已删的状态（沿用现有 best-effort 模式）。
  - 已投递 Python agent 的任务无法撤回，晚到结果依赖 DB 层面的记录删除来忽略。
- 后续事项：
  - 可考虑将 OSS 删除移到事务后补偿流程。
  - 可考虑新增 QUEUED/RETRYING 超时自动死信的定时任务。
- 阻塞项：暂无。

## 14. 最终一致性检查

- 已交付的业务行为：用户可在疾病档案详情页一键取消所有非成功解析报告，删除关联数据和文件，parsingCount 归零。
- 已交付的技术实现：`DELETE /api/disease-profiles/{profileId}/parsing-records` 接口；前端取消按钮和确认弹窗；级联删除和 OSS 清理。
- 与原始计划的差异：无实质差异。
- 验收标准满足情况：标准 1-12 代码实现已完成，测试待补充。
- 证据与验证：见"验证与测试"章节。
- 未验证事项：集成测试（Docker 环境）、前端交互自动化测试。
- 后续工作：补充测试后复审。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-04-30-cancel-stuck-report-parsing-review.md`
- 审查重点：取消范围是否与 parsingCount 口径一致；SUCCESS 记录是否保留；级联删除顺序是否正确；OSS 删除是否覆盖；晚到 MQ 结果是否被忽略；前端交互是否完整。
- 已知需要审查的问题：OSS 删除 best-effort 语义、晚到结果处理依赖 parse_job 记录删除。
