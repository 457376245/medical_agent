# 检查时间节点报告聚合需求实施追踪

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-04-30
- 最后更新日期：2026-04-30
- 相关请求：相差 2、3 天但属于同一次检查的报告，应归到同一个检查时间节点；允许使用 dual-model-coding-orchestrator 和弱模型/子代理做只读侦察、bounded implementation 和测试。
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：当前项目会将不同日期的报告各自分在不同的时间节点，即使报告只差 2、3 天，但是实际这几天的报告是同一次检查做的，报告应该归在同一个检查的时间节点；进入实现。
- 原始上下文：项目为 medical_agent，疾病档案详情页按报告日期展示时间线，当前会把相邻几天的同次检查报告拆成多个时间节点。
- 后续补充：要求先创建 requirement-doc-tracking，然后给出强模型执行计划、弱模型 handoff、验收标准和 stop conditions；允许使用弱模型/子代理做只读侦察、bounded implementation 和测试。

## 3. 摘要

本需求已在疾病报告时间线中引入“检查时间节点”的展示聚合能力：同一患者、同一疾病档案下，成功解析报告在 3 天日期窗口内会归入同一个检查节点。后端在疾病档案记录查询响应中新增 `examNodes` 并保留原 `records`；前端优先消费 `examNodes` 展示日期范围节点，旧 `records` 仍作为回退。

## 4. 背景和目标

- 业务背景：一次体检或检查可能在 2、3 天内产出多份报告，例如检验、影像、门诊记录。按单个报告日期拆时间线会割裂同一次检查。
- 用户 / 问题陈述：用户需要按真实检查事件浏览报告，而不是按每份报告的日期孤立浏览。
- 目标：在疾病档案时间线中，将同一检查的多份临近日期报告展示在同一检查时间节点下，且不丢失任一报告入口。
- 成功标准：用户进入疾病档案详情页时，相差 2、3 天的同次检查报告显示为一个检查节点；仍可选择并查看节点内每份报告详情。

## 5. 范围边界

### 本次做

- 在疾病档案记录查询响应中新增检查时间节点聚合结果。
- 前端疾病时间线优先按检查节点渲染。
- 保留原记录列表字段，降低接口兼容风险。
- 处理同一检查节点内同分类多份报告的展示入口，避免因 `sourceType` 相同而覆盖。
- 增加聚合规则相关测试。

### 本次不做

- 不新增检查主表或 `exam_event_id` 持久化关系。
- 不改上传、解析、MQ、删除、趋势接口的核心语义。
- 不实现用户手动合并 / 拆分检查节点。
- 不引入复杂的医院、检查号、样本号识别算法。
- 不重构无关页面样式或疾病档案管理逻辑。

### 假设

- 初始聚合只限定在同一租户、同一患者、同一疾病档案内。
- 初始窗口为 3 天，覆盖“相差 2、3 天”的业务描述。
- 解析成功的报告进入检查节点；解析中或失败记录保持现有 parsingCount 行为。
- 聚合为展示与查询响应行为，不在数据库中永久写入同次检查关系。

### 待确认问题

- 是否需要未来支持用户手动拆分误合并的检查节点：TBD。
- 是否有可靠的上传批次、检查号、医院名称字段可用于更精确判定：TBD。

## 6. 验收标准

- [x] 标准 1：同一患者、同一疾病档案下，成功解析记录日期为 2026-04-01、2026-04-03、2026-04-04 时，时间线展示为一个检查节点。
- [x] 标准 2：同一患者、同一疾病档案下，成功解析记录日期间隔超过 3 天时，时间线展示为不同检查节点。
- [x] 标准 3：检查节点内包含多份报告时，每份报告都可选择并查看详情，不因同一 `sourceType` 被覆盖。
- [x] 标准 4：接口保留原 `records` 字段，同时新增检查节点字段，避免破坏已有调用方。
- [x] 标准 5：解析中或未成功解析的记录不进入检查节点，仍计入 `parsingCount`。
- [x] 标准 6：不新增数据库表、字段或迁移；不改变上传、解析、删除、趋势接口语义。
- [x] 标准 7：后端聚合逻辑和前端节点展示有可执行测试或明确的验证记录。

## 7. 受影响的系统和文件

- 项目 / 服务：backend-java、frontend
- 主要模块 / 文件：
  - `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileQueryService.java`
  - `backend-java/src/main/java/com/medical/agent/domain/dto/response/DiseaseProfileDetailResponseData.java`
  - `backend-java/src/main/java/com/medical/agent/domain/vo/DiseaseProfileExamNode.java`
  - `backend-java/src/test/java/com/medical/agent/application/DiseaseProfileQueryServiceTest.java`
  - `frontend/src/app/profiles/[profileId]/page.tsx`
  - `frontend/src/components/profiles/DiseaseTimelineView.tsx`
  - `frontend/src/components/profiles/timelineGrouping.ts`
  - `frontend/src/components/profiles/timelineGrouping.test.ts`
- API / 路由：`GET /api/disease-profiles/{profileId}/records`
- 数据库 / 表 / 字段：只读使用 `records.record_date`、`records.source_type`、`records.disease_profile_id` 等现有字段；本次不做数据库迁移。
- 配置：无计划变更。
- 定时任务 / MQ / 外部依赖：无计划变更。

## 8. 实施方案

- 方案概述：后端在疾病档案记录查询中，对成功解析记录按 3 天日期范围生成检查节点；前端优先用检查节点渲染时间线，旧 `records` 作为兼容回退。
- 关键设计决定：
  - 使用后端聚合而非纯前端聚合，保证接口响应语义稳定，并为 Agent 或其他入口后续复用留出路径。
  - 使用 3 天窗口作为第一版确定性规则，避免引入未经验证的复杂匹配算法。
  - 不持久化检查节点，降低误合并的长期数据风险。
  - 节点内记录选择使用 `record.id`，不能只用 `sourceType`。
- 替代方案与取舍：
  - 纯前端按日期合并：改动更小，但其他调用方仍拿不到检查节点语义，且容易与后端行为分裂。
  - 新增检查主表：长期模型更完整，但需要迁移、回填、编辑能力和更大测试范围，本次先不做。
- 风险：
  - 仅按日期窗口可能误合并临近但不同次检查的报告。
  - 当前前端分类选择以分类为主，若同节点同分类多报告处理不当会丢入口。

## 9. 实施计划

1. 只读侦察现有 DTO、服务、前端 props 和测试模式，确认最小修改面。
2. 后端新增检查节点响应结构与 3 天窗口聚合逻辑，保持 `records` 兼容。
3. 前端 `DiseaseTimelineView` 优先消费检查节点，并保留旧记录分组回退。
4. 增加或更新后端/前端测试，覆盖聚合、拆分、同分类多报告、解析中排除。
5. 运行针对性测试，修复发现的问题。
6. 更新本文档的变更清单、验证结果、风险和最终一致性检查。

## 10. 进度日志

- 2026-04-30：创建文档并确认初始范围、验收标准、实施方案和弱模型协作边界。
- 2026-04-30：完成弱模型只读侦察，确认当前后端响应由 `DiseaseProfileQueryService` 生成，前端旧逻辑用 `sourceType` 选择会导致同分类多报告只能命中第一条。
- 2026-04-30：完成后端 `examNodes` 响应与 3 天窗口聚合，保留 `records` 字段。
- 2026-04-30：完成前端 `examNodes` 消费、旧 `records` 回退、同分类多报告按 `record.id` 选择。
- 2026-04-30：完成针对性后端、前端和 TypeScript 验证。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `backend-java/src/main/java/com/medical/agent/domain/vo/DiseaseProfileExamNode.java` | 新增检查时间节点 VO，包含节点 ID、日期范围、展示日期和节点内记录列表 | 标准 1、2、3、4 |
| `backend-java/src/main/java/com/medical/agent/domain/dto/response/DiseaseProfileDetailResponseData.java` | 响应 DTO 新增 `examNodes`，保留 `records` | 标准 4 |
| `backend-java/src/main/java/com/medical/agent/application/DiseaseProfileQueryService.java` | 对成功解析记录按 3 天日期范围生成检查节点；非法 profileId 返回空节点；解析中记录仍只计入 `parsingCount` | 标准 1、2、5、6 |
| `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java` | 返回疾病档案记录时透出 `result.examNodes()` | 标准 4 |
| `backend-java/src/test/java/com/medical/agent/application/DiseaseProfileQueryServiceTest.java` | 新增服务层聚合测试：3 天内合并、超过 3 天拆分、同分类多报告保留、解析中排除 | 标准 1、2、3、5、7 |
| `frontend/src/app/profiles/[profileId]/page.tsx` | 读取 `payload.data.examNodes` 并传入时间线组件；请求失败时同步清空节点状态 | 标准 4 |
| `frontend/src/components/profiles/timelineGrouping.ts` | 抽出时间线分组 helper，优先使用后端 `examNodes`，旧 records 按日期回退；选择 key 使用 `record.id` | 标准 1、3、4 |
| `frontend/src/components/profiles/DiseaseTimelineView.tsx` | 时间节点展示日期范围；分类/报告选择改用 `record.id`，同分类多报告显示日期后缀；更新分类时同步节点内记录 | 标准 1、3 |
| `frontend/src/components/profiles/timelineGrouping.test.ts` | 新增前端分组测试，覆盖后端节点消费、旧数据回退、同分类多报告可区分 | 标准 3、4、7 |

## 12. 验证与测试

- 计划检查：
  - 后端聚合逻辑单测或服务层测试。
  - 前端时间线节点渲染测试或可替代的组件级验证。
  - 针对性构建 / 测试命令，按项目可用脚本执行。
- 已完成检查：
  - `mvn -q -Dtest=DiseaseProfileQueryServiceTest test`：通过。
  - `npx vitest run src/components/profiles/timelineGrouping.test.ts`：通过。
  - `npx tsc --noEmit`：通过。
- 未运行 / 尚未验证：
  - 未在真实测试环境执行端到端 E2E 验证。
  - 未启动浏览器做真实页面手工回归。
- 未验证原因：
  - 本次改动核心规则已由服务层和前端纯函数测试覆盖；如需验证真实 HTTP、数据库、鉴权、页面交互链路，后续启用真实测试环境执行 E2E，不使用本地容器化集成测试作为验证路径。

## 13. 风险与后续事项

- 剩余风险：3 天窗口是启发式规则，可能误合并临近但不同次检查的报告；本次未实现手动拆分能力。
- 后续事项：可在后续版本加入上传批次、检查号、医院名称、用户手动拆分 / 合并能力。
- 阻塞项：暂无。

## 14. 最终一致性检查

- 已交付的业务行为：疾病档案时间线可展示后端返回的检查节点，2、3 天内的同次检查报告可归入同一节点；节点内同分类多报告可按独立报告选择。
- 已交付的技术实现：`GET /api/disease-profiles/{profileId}/records` 响应新增 `examNodes`；前端优先消费 `examNodes`，旧 `records` 回退；无数据库迁移。
- 与原始计划的差异：无实质差异；前端采用扁平 `GroupedCategory` 加 `record.id` 作为选择 key，而不是引入分类内 records 嵌套结构，满足最小改动目标。
- 验收标准满足情况：标准 1-7 已通过代码和测试验证。
- 证据与验证：见“验证与测试”章节。
- 未验证事项：真实测试环境 E2E 和真实浏览器页面手工验证未运行。
- 后续工作：如误合并反馈出现，后续可引入检查号、医院、上传批次或手动拆分 / 合并能力。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-04-30-exam-node-report-grouping-review.md`
- 审查重点：检查时间节点聚合是否满足验收标准；是否未改变数据库和无关接口语义；同节点同分类多报告是否不丢入口；文档与实际实现是否一致。
- 已知需要审查的问题：3 天窗口误合并风险是否可接受。
