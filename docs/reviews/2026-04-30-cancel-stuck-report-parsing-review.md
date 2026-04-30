# 需求达标审查报告：取消卡住的报告解析并清理链路文件

## 1. 审查输入

- 需求实施追踪文档：`docs/requirements/2026-04-30-cancel-stuck-report-parsing.md`
- 工作区：`E:\Python_Product\medical_agent`
- 分支 / 提交：`temp-remaining-work-20260430`，当前工作区未提交变更审查
- 审查时间：2026-04-30 16:12 左右
- 审查类型：复审

## 2. 审查结论

- 结论：有条件通过
- 总体说明：上轮阻塞项已基本处理：已补建需求追踪文档，已新增后端服务级测试，后端和前端验证命令通过。当前实现满足“当前档案下取消全部非成功解析报告并删除链路数据”的核心目标；剩余风险主要是 Testcontainers 集成测试因无 Docker 跳过、前端交互未自动化、晚到 MQ 结果缺少直接测试。复审期间运行 `npm run build` 后 `frontend/tsconfig.tsbuildinfo` 再次显示为修改状态，交付前应从提交中移除该构建缓存改动。若接受这些验证缺口，可进入交付；若作为高风险删除功能上线，建议补齐集成/E2E 验证后再合并。

## 3. 阻塞问题

| 问题 | 严重级别 | 证据 | 建议 |
| --- | --- | --- | --- |
| 无阻塞问题 | - | 需求追踪文档已存在；新增 `DiseaseProfileServiceCancelParsingTest`；`mvn clean test`、`npx tsc --noEmit`、`npm test -- --run`、`npm run build` 均通过 | 按第 7 节补充剩余验证可降低上线风险 |
| 构建缓存文件仍显示为工作区修改 | 低 | 复审后 `git status --short` 显示 `M frontend/tsconfig.tsbuildinfo`；该文件由前端构建生成，不属于需求代码 | 交付/提交前移除该文件改动 |

## 4. 验收标准逐项核对

| 验收标准 | 文档说明 | 代码证据 | 测试 / 验证证据 | 结论 |
| --- | --- | --- | --- | --- |
| 标准 1：`parsingCount > 0` 时显示“取消解析”，`parsingCount = 0` 时不显示 | 追踪文档第 6 节标准 1 | `frontend/src/components/profiles/DiseaseTimelineView.tsx:525` 仅在 `parsingCount > 0` 渲染状态区域；`frontend/src/components/profiles/DiseaseTimelineView.tsx:530` 渲染按钮 | `npm run build` 通过；无前端交互测试 | 通过，测试覆盖偏弱 |
| 标准 2：点击按钮弹出确认弹窗，文案提示不可恢复 | 追踪文档第 6 节标准 2 | `frontend/src/components/profiles/DiseaseTimelineView.tsx:682` 使用 `ConfirmDialog`；`frontend/src/components/profiles/DiseaseTimelineView.tsx:685` 文案包含删除报告、解析数据、上传文件、不可恢复 | `npm run build` 通过；无前端交互测试 | 通过，测试覆盖偏弱 |
| 标准 3：确认后调用取消接口，成功后刷新 records、examNodes、parsingCount | 追踪文档第 6 节标准 3 | `frontend/src/components/profiles/DiseaseTimelineView.tsx:232` 调用 `/disease-profiles/${profileId}/parsing-records`；`frontend/src/components/profiles/DiseaseTimelineView.tsx:239` 调用刷新回调；`frontend/src/app/profiles/[profileId]/page.tsx:85` 传入 `onParsingCanceled` | `npx tsc --noEmit`、`npm run build` 通过 | 通过，缺前端行为测试 |
| 标准 4：后端只删除当前档案下非成功解析记录，SUCCESS 保留 | 追踪文档第 6 节标准 4 | `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java:179` 按 tenant/patient/profile 查询；`DiseaseProfileService.java:196` 查询最新状态；`DiseaseProfileService.java:197` 排除 `SUCCESS` | `DiseaseProfileServiceCancelParsingTest.cancelDeletesOnlyNonSuccessRecordsAndPreservesSuccess`、`cancelReturnsZeroCountsWhenAllRecordsAreSuccess` 通过 | 通过 |
| 标准 5：级联删除 data_rights_requests、structured_results、generated_outputs、parse_job_assets、parse_jobs、assets、records | 追踪文档第 6 节标准 5 | `DiseaseProfileService.java:238` 起级联删除；`DiseaseProfileService.java:239` 至 `DiseaseProfileService.java:260` 覆盖相关 mapper delete | 新增服务测试验证 mapper delete 调用；`mvn clean test` 通过 | 通过 |
| 标准 6：删除对象存储文件 | 追踪文档第 6 节标准 6 | `DiseaseProfileService.java:205` 查询候选资产 objectKey；`DiseaseProfileService.java:212` 调用 `ossPresignService.deleteObject` | `cancelCallsOssDeleteForEveryCandidateAsset` 通过 | 通过 |
| 标准 7：返回删除计数 | 追踪文档第 6 节标准 7 | `DiseaseProfileParsingCancelResponseData` 包含 `deletedRecordCount`、`deletedAssetCount`、`deletedParseJobCount`；`DiseaseProfileController.java:279` 组装响应 | 服务测试断言多个返回计数；`mvn clean test` 通过 | 通过 |
| 标准 8：支持 `unknown` profileId | 追踪文档第 6 节标准 8 | `DiseaseProfileController.java:253` 对 `unknown` 跳过 UUID 校验；`DiseaseProfileService.java:183` 使用 `isNull(DiseaseProfileId)` | `cancelWithUnknownProfileIdQueriesForNullDiseaseProfileId` 通过 | 通过 |
| 标准 9：非法 UUID 返回 400，不存在档案返回 404，无可取消记录返回 200 且计数为 0 | 追踪文档第 6 节标准 9 | `DiseaseProfileController.java:257` 返回 400；`DiseaseProfileController.java:264` 返回 404；`DiseaseProfileService.java:190`、`DiseaseProfileService.java:201` 返回 0 计数 | 服务测试覆盖空记录和全 SUCCESS；controller 分支未单测；Testcontainers 集成测试跳过 | 部分通过 |
| 标准 10：不新增数据库迁移或 parse job 状态枚举 | 追踪文档第 6 节标准 10 | 当前 diff 无迁移文件、无 `ParseJobStatus` 修改 | `git diff --stat` 检查 | 通过 |
| 标准 11：不改 backend-agent | 追踪文档第 6 节标准 11 | 当前 diff 无 `backend-agent` 修改 | `git diff --stat` 检查 | 通过 |
| 标准 12：取消后晚到 MQ 解析结果不会恢复已删除数据 | 追踪文档第 6 节标准 12 | 设计依赖 parse job 删除后 `ParseJobService.applyParseResult` 查不到 job 并抛出 `ResourceNotFoundException`，由 `ParseResultConsumer.consume` 的 catch 记录错误而不写回 | 未新增直接测试；Testcontainers 集成测试跳过 | 部分通过，需补直接验证 |

## 5. 文档与代码一致性

- 文档准确的地方：
  - 追踪文档正确记录了取消范围、删除语义、不新增 `CANCELED` 状态、不改 Python agent、接口路径、主要变更文件和剩余风险。
  - 代码变更清单与当前 diff 基本一致，包括 DTO、Service、Controller、前端页面和服务测试。
  - 验证章节准确记录了 Docker 缺失导致 Testcontainers 集成测试跳过。
- 文档过时或不准确的地方：
  - 追踪文档第 14 节写“标准 1-12 代码实现已完成，测试待补充”，但第 6 节标准 12 仍标记未完成直接验证；建议改为“标准 1-11 已有直接验证或代码证据，标准 12 代码路径存在但缺直接测试”。
  - 追踪文档第 12 节“计划检查”仍写“晚到结果处理”为计划项，但实际未补测试，应保持为未验证项。
- 文档遗漏：
  - 未记录 controller 分支缺少单元测试或接口测试。
  - 未记录前端取消弹窗交互未自动化。
- 代码中存在但文档未记录的变更：
  - 未发现明显未记录的业务代码变更。

## 6. 实现问题

- 问题：controller 错误分支和响应 DTO 缺少直接测试。
- 严重级别：中。
- 文件 / 行号：`backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java:248` 至 `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java:283`。
- 原因：代码实现了非法 UUID、档案不存在、成功响应三类分支，但当前新增测试集中在 Service mock 层，未覆盖 Controller 分支。
- 建议：补充 `DiseaseProfileController` 单元测试，覆盖 400、404、200 响应和 DTO 字段。

- 问题：晚到 MQ 结果缺少直接回归测试。
- 严重级别：中。
- 文件 / 行号：`docs/requirements/2026-04-30-cancel-stuck-report-parsing.md:70`、`backend-java/src/main/java/com/medical/agent/application/service/ParseJobService.java` 的 `applyParseResult` 路径、`ParseResultConsumer.consume` catch 路径。
- 原因：这是需求明确语义之一，但当前只靠代码推断和文档说明，没有测试证明取消后晚到结果不会恢复记录或写入结构化结果。
- 建议：在有 Docker 的环境补集成测试，或用 mock 层测试 `ParseResultConsumer.consume` 对缺失 job 的处理不会调用写入逻辑。

- 问题：前端取消交互没有自动化测试。
- 严重级别：低到中。
- 文件 / 行号：`frontend/src/components/profiles/DiseaseTimelineView.tsx:525` 至 `frontend/src/components/profiles/DiseaseTimelineView.tsx:690`。
- 原因：按钮显示、弹窗确认、取消不请求、失败提示、成功刷新均为用户可见行为，目前仅通过类型检查和构建验证。
- 建议：后续补 React/Vitest 组件测试或 Playwright 手工/自动化验证。

- 问题：构建缓存文件仍在工作区显示为修改。
- 严重级别：低。
- 文件 / 行号：`frontend/tsconfig.tsbuildinfo`。
- 原因：复审运行 `npm run build` 后 TypeScript 构建信息文件再次变化；该文件不是需求产物。
- 建议：交付/提交前移除该文件改动。

- 问题：OSS 文件删除和数据库事务存在已知 best-effort 风险。
- 严重级别：中。
- 文件 / 行号：`backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java:212` 至 `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileService.java:216`。
- 原因：对象存储删除发生在数据库删除前；如果后续数据库删除失败，可能出现文件已删除但数据库事务回滚。追踪文档已记录该风险，且沿用现有 `deleteProfile` 模式。
- 建议：本次可接受为已知风险；后续可改为事务后补偿删除或异步清理队列。

## 7. 测试与验证缺口

- 已有验证：
  - `cd backend-java; mvn clean test`：通过。共 93 个测试，0 失败，15 skipped；新增 `DiseaseProfileServiceCancelParsingTest` 7 个测试全部通过。
  - `cd frontend; npx tsc --noEmit`：通过。
  - `cd frontend; npm test -- --run`：通过。3 个测试文件，13 个测试通过。
  - `cd frontend; npm run build`：通过。Next.js 编译、类型检查、静态页面生成成功。
- 工作区清洁度：
  - `frontend/tsconfig.tsbuildinfo` 在复审后仍显示为修改，应在提交前移除。
- 缺失验证：
  - Testcontainers 集成测试因本机无 Docker 跳过，未验证真实 PostgreSQL + HTTP 接口链路。
  - Controller 400/404/200 分支缺少单元测试。
  - 晚到 MQ 结果不会恢复数据缺少直接测试。
  - 前端取消解析交互缺少自动化测试。
- 无法确认的验证：
  - 真实 OSS 删除行为未在集成环境验证。
  - 真实并发场景下取消与解析结果同时到达的最终一致性未验证。
- 建议补充：
  - 有 Docker 环境时运行完整 `ApiIntegrationTest`，增加取消接口集成用例。
  - 增加 Controller 单测覆盖错误分支和成功响应。
  - 增加晚到 parse result 的回归测试。
  - 后续补前端组件或 E2E 测试。

## 8. 风险与后续事项

- 交付风险：
  - 真实数据库级联删除和接口层错误响应未被集成测试覆盖。
  - 晚到 MQ 结果语义依赖现有异常路径，缺直接测试。
  - OSS 删除 best-effort 语义可能造成对象存储和数据库短暂或永久不一致。
- 后续事项：
  - 在有 Docker 的环境补跑并扩展 Testcontainers 集成测试。
  - 补 Controller 单测和晚到 MQ 结果测试。
  - 视上线风险补前端交互测试。
- 是否需要更新需求实施追踪文档：需要小幅更新。建议把第 14 节关于“标准 1-12 代码实现已完成”的表述改为与第 6、12 节一致，明确标准 12 缺直接验证。

## 9. 最终建议

- 是否可以交付：有条件可以交付。核心功能和主要服务层删除逻辑已有代码与测试证据，构建和现有测试通过。
- 交付前必须修复：移除 `frontend/tsconfig.tsbuildinfo` 构建缓存改动；若团队要求高风险删除功能必须有真实数据库集成测试，则需先补 Docker 环境集成测试。
- 可后续优化：
  - 补 Controller 测试、晚到 MQ 测试、前端交互测试。
  - 将 OSS 删除改为事务后补偿流程。
  - 更新追踪文档第 14 节，使最终一致性描述与未验证项一致。
