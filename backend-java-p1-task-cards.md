# backend-java P1 任务卡（文件/类/接口粒度）

## 目标
- 在不一次性改坏现有行为的前提下，完成 `PersistenceService` 大类拆分。
- 建立传统分层边界：`controller -> service -> dao(repository)`。
- 清理读接口写副作用（重点是 `GET /records/{recordId}`）。
- 为 P3 安全改造预留清晰边界（tenant/user 上下文可注入）。

## 执行顺序
1. T01 -> T02
2. T03/T04/T05/T06/T07 可并行
3. T08 -> T09 -> T10
4. T11 -> T12 -> T13

## 任务卡
- 进度规则：未完成使用 `[ ]`，完成后改为 `[✓]`。

### [✓] T01 基线与回归兜底
- 变更文件：
`src/test/java/com/medical/agent/api/ApiIntegrationTest.java`
`src/test/resources/application-test.properties`
- 任务：
补充最小回归基线，确保后续拆分类改造可验证；补一个“record 不存在应返回 404”的失败用例（先红灯）。
- DoD：
测试可稳定运行，新增失败用例明确暴露当前 GET 写副作用。

### [✓] T02 建立仓储接口骨架
- 新增文件：
`src/main/java/com/medical/agent/application/repository/RecordRepository.java`
`src/main/java/com/medical/agent/application/repository/ParseJobRepository.java`
`src/main/java/com/medical/agent/application/repository/StructuredResultRepository.java`
`src/main/java/com/medical/agent/application/repository/GeneratedOutputRepository.java`
`src/main/java/com/medical/agent/application/repository/DataRightsRepository.java`
- 任务：
定义 5 个接口，只放当前业务必需方法，不做过度抽象。
- DoD：
接口与当前 `PersistenceService` 方法映射完整，无循环依赖。

### [✓] T03 抽取 RecordRepository JDBC 实现
- 新增文件：
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcRecordRepository.java`
- 迁移来源：
`src/main/java/com/medical/agent/application/PersistenceService.java` 的记录/资产/时间线/分类相关方法。
- 任务：
迁移 SQL 与参数绑定，保留行为一致；保留 `normalizeReportCategoryName` 等私有工具逻辑到实现类。
- DoD：
`JdbcRecordRepository` 单独可编译；原逻辑行为不变。

### [✓] T04 抽取 ParseJobRepository JDBC 实现
- 新增文件：
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcParseJobRepository.java`
- 迁移来源：
`createOrReuseParseJob`、`bindParseJobAssets`、`listFailedParseJobsForRetry`、`getAndAdvanceParseJob`、`applyParseResult` 等。
- 任务：
把 parse 任务相关 SQL 全部集中；保留 `ParseApplyResult`、`ParseRetryCandidate` 结果对象。
- DoD：
parse 任务所有读写不再依赖 `PersistenceService` 大类内部状态。

### [✓] T05 抽取 StructuredResultRepository JDBC 实现
- 新增文件：
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcStructuredResultRepository.java`
- 迁移来源：
`patchStructuredResult`、`insertStructuredResultIfMissing`、`parsePayload`、trend 字段抽取支撑查询。
- 任务：
统一 `payload_json` 的读写序列化处理。
- DoD：
structured result 相关 SQL 在单一类中闭合。

### [✓] T06 抽取 GeneratedOutputRepository JDBC 实现
- 新增文件：
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcGeneratedOutputRepository.java`
- 迁移来源：
`createGeneratedOutput`、`createGeneratedOutputWithMeta`、`fetchLatestGeneratedOutput`。
- 任务：
先保持现状版本策略，后续 P4 再改原子版本号写入。
- DoD：
生成输出写入与查询独立成类，并被服务层通过接口调用。

### [✓] T07 抽取 DataRightsRepository JDBC 实现
- 新增文件：
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcDataRightsRepository.java`
- 迁移来源：
`createDataRightsRequest`、`getDataRightsRequest`。
- 任务：
将数据权益请求查询与状态推进逻辑收口，避免 controller/service 直接拼装状态机。
- DoD：
数据权益流程仅通过仓储接口访问表 `data_rights_requests`。

### [✓] T08 改造 PersistenceService 为编排门面
- 变更文件：
`src/main/java/com/medical/agent/application/PersistenceService.java`
- 任务：
将原 1000+ 行类改为薄门面，内部委派到 5 个 repository；把常量与工具方法下沉到对应实现类。
- DoD：
`PersistenceService` 控制在约 200 行以内，仅保留编排与兼容方法。

### [✓] T09 清理 GET 写副作用
- 变更文件：
`src/main/java/com/medical/agent/application/PersistenceService.java`
`src/main/java/com/medical/agent/infrastructure/persistence/jdbc/JdbcRecordRepository.java`
`src/main/java/com/medical/agent/api/RecordController.java`
- 任务：
移除 `fetchRecord()` 内 `ensureRecord()`；不存在记录返回 NOT_FOUND（404），不自动建记录。
- DoD：
`GET /api/v1/records/{recordId}` 对不存在记录不落库且返回 404。

### [✓] T10 补事务边界
- 变更文件：
`src/main/java/com/medical/agent/application/DiseaseProfileService.java`
`src/main/java/com/medical/agent/application/PersistenceService.java`
`src/main/java/com/medical/agent/infrastructure/scheduler/ParseRetryScheduler.java`
- 任务：
对跨表写流程统一加服务层事务边界；避免仓储层混入事务策略。
- DoD：
删除记录、级联删除、parse 结果落库等关键路径具备原子性。

### [✓] T11 引入应用服务层
- 新增文件：
`src/main/java/com/medical/agent/application/service/RecordService.java`
`src/main/java/com/medical/agent/application/service/ParseJobService.java`
`src/main/java/com/medical/agent/application/service/DataRightsService.java`
- 任务：
将控制器当前直接调用的编排逻辑迁移到应用服务层；服务层组合多个 DAO/repository。
- DoD：
控制器仅处理协议与参数绑定，业务编排全部下沉到 service 层。

### [✓] T12 控制器依赖收敛与回归修复
- 变更文件：
`src/main/java/com/medical/agent/api/*.java`（依赖 `PersistenceService` 的控制器）
- 任务：
控制器不感知底层仓储拆分；仅调用应用服务；补全 NOT_FOUND/BAD_REQUEST 语义一致性。
- DoD：
控制器不直接依赖 DAO/repository，且可编译并通过回归。

### [✓] T13 验收与发布准备
- 变更文件：
`src/test/java/com/medical/agent/api/ApiIntegrationTest.java`
`backend-java-refact-plan.md`
- 任务：
回填 P1 实际完成项、风险与遗留项；形成 P2 输入清单。
- DoD：
P1 验收项全部勾选；遗留项明确归属到 P2/P3/P4。

## 方法映射建议（旧 -> 新）
- `PersistenceService.ensureRecord/createAsset/fetchRecord/updateRecordSourceType/fetchRecordTrend/deleteRecord/...` -> `RecordRepository`
- `createOrReuseParseJob/bindParseJobAssets/listFailedParseJobsForRetry/applyParseResult/...` -> `ParseJobRepository`
- `patchStructuredResult/insertStructuredResultIfMissing/parsePayload相关` -> `StructuredResultRepository`
- `createGeneratedOutput/createGeneratedOutputWithMeta/fetchLatestGeneratedOutput` -> `GeneratedOutputRepository`
- `createDataRightsRequest/getDataRightsRequest` -> `DataRightsRepository`

## P1 完成判定
- `PersistenceService` 从超大类降为门面类。
- 核心 SQL 已分散到 5 个 JDBC 实现类。
- 应用层形成清晰 service 编排边界。
- controller 不直接访问 DAO/repository。
- `GET /records/{id}` 不再创建记录。
- 关键跨表流程具备事务边界。
- 回归测试通过，且新增用例覆盖本次改动。
