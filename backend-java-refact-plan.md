# backend-java 重构计划（确认版）

## 1. 已确认决策
1. 鉴权方案：`1A` 网关下发 JWT。
2. 多租户/多用户：`2A` 本次真实落地。
3. API 兼容策略：`3B` 允许小幅破坏式调整，采用版本化发布。
4. 数据库变更窗口：`4B` 一次短停机窗口完成结构性变更。
5. MQ 语义：`5A` At-least-once + 消费幂等。
6. 密钥状态：`6B` 未轮换，必须立即执行红线整改。
7. `mock-upload` 策略：`7A` 仅 `local/test` 环境开放。
8. 工期优先级：`8B` 先拆大类，再补安全。

## 2. 执行原则
- 主线按 `8B` 执行：先做代码结构拆分与边界清理。
- 红线并行执行：由于 `6B`，密钥轮换与凭据下线不延期，作为并行 P0 任务立即开始。
- API 升级采用双栈：`/api/v1` 兼容保留，新增破坏式调整放 `/api/v2`，设置退场时间。
- 目标架构采用传统分层：`controller -> service -> dao(repository)`，禁止 controller 直接访问 DAO。

## 3. 分阶段计划

## Phase P0-Redline（并行，0.5-1 天）
- 下线 `application.properties` 中真实凭据默认值。
- 轮换 OSS/DB/RabbitMQ 凭据并更新环境变量。
- `mock-upload` 限制为 `local/test` profile。

交付物：
- 凭据轮换记录。
- 配置分层文件与部署变量清单。

## Phase P1（第 1 周）：大类拆分与持久层分治
- 拆分 `PersistenceService` 为：
  - `RecordRepository`
  - `ParseJobRepository`
  - `StructuredResultRepository`
  - `GeneratedOutputRepository`
  - `DataRightsRepository`
- 建立 service 层门面：
  - `RecordService`
  - `ParseJobService`
  - `GeneratedOutputService`（保留并收敛职责）
  - `DataRightsService`
- 去除读接口写副作用（禁止 GET 触发 `ensureRecord`）。
- 梳理跨表写入流程并补 `@Transactional` 边界。

交付物：
- 新包结构与依赖关系图。
- 回归测试通过（保留现有行为）。

## Phase P2（第 2 周）：API 边界重构与 v2 发布
- 将 `@RequestBody Map<String, Object>` 迁移为 DTO + `jakarta.validation`。
- 统一异常模型与错误码。
- 控制器改为仅依赖 service 层，不直接依赖 DAO/repository。
- 对破坏式改动发布 `/api/v2`，并提供迁移对照表。

交付物：
- DTO 模型与校验规则清单。
- `v1 -> v2` 映射文档。

## Phase P3（第 3 周）：安全与授权闭环
- 接入 JWT 资源服务器。
- 从 `SecurityContext` 注入 `tenantId/userId`，移除默认常量数据路径。
- 修复 IDOR（`recordId/requestId` 强绑定校验）。
- 开启审计最小集（谁在何时访问了什么资源）。

交付物：
- 鉴权配置与权限矩阵。
- 越权用例集（全部按预期拒绝）。

## Phase P4（第 4 周）：MQ 可靠性与幂等
- RabbitMQ 增加重试、DLQ、失败告警。
- 消费失败不吞异常，进入重试或死信。
- 增加幂等存储：`tenant + user + endpoint + idempotency_key` 唯一约束。
- 生成版本号写入改为数据库原子策略，消除并发竞态。

交付物：
- 可靠投递与消费时序图。
- 重复消息/失败重试测试报告。

## Phase P5（停机窗口，0.5-1 天）：数据库结构升级
- 在停机窗口内执行 Flyway 结构性变更：
  - 补关键 FK/唯一约束/索引。
  - 修复历史脏数据并收敛级联策略。
- 窗口结束后执行数据校验与业务回归。

交付物：
- 迁移脚本包（含回滚脚本）。
- 停机执行手册与核验清单。

## Phase P6（持续）：测试与发布门禁
- 建立无 Docker 单测 + 有 Docker 集成测试双轨。
- CI 禁止“全部 skipped”通过发布。
- 新增并发、授权、幂等、死信回归用例。

交付物：
- 测试矩阵与门禁规则。
- 每次发布的质量报告模板。

## 4. 优先级任务序列
1. 立即执行 P0-Redline（凭据与配置）。
2. 拆分 DAO（repository）并建立 service 层编排边界。
3. 完成 DTO 化与 `/api/v2` 破坏式升级准备。
4. 落地 JWT 与租户用户隔离。
5. 上线 MQ 重试、DLQ 与幂等。
6. 在停机窗口完成 DB 结构迁移。
7. 收紧 CI 门禁并固化测试体系。

## 5. 验收标准
- 仓库中无真实密钥默认值。
- 鉴权默认开启，未授权和越权访问被拒绝。
- 不再存在 GET 触发写入行为。
- controller 不直接调用 DAO/repository，全部通过 service 层。
- 并发写入无版本冲突。
- MQ 失败可重试并最终可追踪到 DLQ。
- CI 中关键测试不允许全 skipped。

## 6. 风险与应对
- 风险：`8B` 导致安全项延后。
- 应对：P0-Redline 并行强制执行，不占主线但必须先完成。

- 风险：`3B` 的接口版本切换影响调用方。
- 应对：`v1/v2` 双栈运行，提供迁移期和下线公告。

- 风险：`4B` 停机窗口超时。
- 应对：预演迁移、冻结变更、准备一键回滚脚本。

## 7. 里程碑
- M1（第 1 周）：P0-Redline + P1 完成。
- M2（第 2 周）：P2 完成并发布 v2 文档。
- M3（第 3 周）：P3 完成并开启权限门禁。
- M4（第 4 周）：P4 完成并通过异步可靠性压测。
- M5（第 5 周）：P5 完成并恢复业务。
- M6（持续）：P6 长期执行。

## 8. P1 实际进展（2026-02-10）
- 已完成：
  - 新增 `RecordControllerTest` 单元回归（7 个用例）。
  - 建立 5 个 repository 接口与 5 个 JDBC 实现。
  - `PersistenceService` 从 1000+ 行重构为编排门面（约 260 行）。
  - `GET /api/v1/records/{recordId}` 移除自动建档副作用，不存在返回 404。
  - 关键跨表路径增加 service 层事务边界。
  - 新增 `RecordService`、`ParseJobService`、`DataRightsService` 并迁移对应 controller 依赖。
- 测试状态：
  - `RecordControllerTest`：7/7 通过。
  - `ApiIntegrationTest`：7 个用例全部 skipped（本机无 Docker）。
- 遗留输入（进入后续阶段）：
  - 在可用 Docker 的 CI 环境补跑并固定集成测试门禁。
  - 继续推进 DTO 化、统一异常模型与 `/api/v2` 迁移文档。
