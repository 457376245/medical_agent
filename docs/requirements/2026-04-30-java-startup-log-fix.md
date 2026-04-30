# Java 进程启动日志问题修复

## 1. 元数据

- 状态：进行中
- 负责人：Codex
- 开始日期：2026-04-30
- 最后更新日期：2026-04-30
- 相关请求：java进程启动报错，分析本地日志并解决
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：分析本地 Java 进程启动报错日志并解决。
- 原始上下文：项目路径 `E:\Python_Product\medical_agent`，Java 服务位于 `backend-java`。
- 后续补充：无。

## 3. 摘要

本需求处理 `backend-java` 本地启动日志中的失败和报错：系统 `PATH` 中旧 Tomcat Native DLL 触发版本不兼容 ERROR，远端 RabbitMQ 监听器消费到历史解析结果消息，且这些消息对应的 parse job 不存在时持续输出错误堆栈。目标是让本地启动继续连接远程 MQ，并让脏历史消息不再表现为启动故障。

## 4. 背景和目标

- 业务背景：Java 服务启动依赖 Postgres、RabbitMQ 等外部资源，本地启动需要连接远程 MQ 消费消息。
- 用户 / 问题陈述：启动日志出现连接失败、MQ consumer 启动超时、parse job not found 等错误。
- 目标：定位日志根因，做最小修复，并验证 Java 服务可启动。
- 成功标准：本地启动脚本不再默认等待 RabbitMQ listener；消费者对不存在 job 的历史结果消息不再输出 ERROR 堆栈；相关测试通过或记录未验证原因。

## 5. 范围边界

### 本次做

- 分析 `backend-java/logs/backend-java.log` 和 JVM 错误日志。
- 调整本地 Java 启动脚本进程内 `PATH`，避免加载旧 Tomcat Native DLL。
- 调整解析结果消费者对不存在 job 的历史消息处理。
- 增加最小测试覆盖。

### 本次不做

- 不改数据库结构。
- 不清理远端 RabbitMQ 队列数据。
- 不修改远端环境地址、账号、密码。
- 不处理 2026-04-03 IntelliJ Git AskPass JVM 内存崩溃，因为它不是当前 Java 服务启动日志。

### 假设

- 本地启动主要用于开发和接口调试，默认不需要自动消费 RabbitMQ 历史消息。
- 本地启动必须保留远程 MQ listener，用于消费解析和生成结果消息。
- 历史 parse result 找不到 job 是可丢弃消息，不能反复重试阻塞消费者。
- `D:\apache-tomcat-8.5.50\bin` 是本机旧外部 Tomcat 路径，移出 Java 服务启动进程 `PATH` 不影响当前 Spring Boot 嵌入式 Tomcat。

### 待确认问题

- 是否需要后续清理远端 RabbitMQ 中已经堆积的历史解析结果消息。

## 6. 验收标准

- [ ] 标准 1：`start-backend-java.ps1` 启动时继续使用配置中的远程 RabbitMQ listener。
- [ ] 标准 2：`ParseResultConsumer` 收到不存在 job 的解析结果消息时记录可读警告，不抛出 ERROR 堆栈。
- [ ] 标准 3：新增或现有测试覆盖消费者忽略未知 job 的行为，并通过验证。
- [ ] 标准 4：执行 Java 服务启动验证，确认应用可以进入 started 状态，或记录外部依赖阻塞原因。
- [ ] 标准 5：脚本启动进程不再加载旧 `tcnative-1.dll`，避免 Tomcat Native 版本不兼容 ERROR。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-java`
- 主要模块 / 文件：`start-backend-java.ps1`、`ParseResultConsumer.java`、`ApiIntegrationTest.java`
- API / 路由：无
- 数据库 / 表 / 字段：无
- 配置：启动脚本进程内 `PATH`
- 定时任务 / MQ / 外部依赖：RabbitMQ `agent.parse.result.v1` listener

## 8. 实施方案

- 方案概述：启动脚本从脚本进程 `PATH` 移除旧外部 Tomcat bin；消费者单独捕获 `ResourceNotFoundException` 并记录 warn；远端 RabbitMQ 脏消息通过管理端按队列清理。
- 关键设计决定：保留远程 RabbitMQ listener，满足本地消费消息需求。
- 替代方案与取舍：也可全局禁用 RabbitMQ 自动配置，但这会违背本地消费远程消息的需求，因此不采用。
- 风险：清理远端队列属于破坏性操作，必须先确认队列内无正常待处理消息。

## 9. 实施计划

1. 修改启动脚本旧 Tomcat Native 路径处理。
2. 修改解析结果消费者对未知 job 的处理。
3. 增加测试覆盖并运行 Maven 验证。
4. 启动 Java 服务确认启动状态。

## 10. 进度日志

- 2026-04-30：创建文档并确认初始范围；本地日志显示启动慢和错误主要来自 RabbitMQ listener 与历史 parse result。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| TBD | TBD | TBD |

## 12. 验证与测试

- 计划检查：运行相关 Maven 测试；执行启动脚本或等价启动命令。
- 已完成检查：TBD
- 未运行 / 尚未验证：TBD
- 未验证原因：TBD

## 13. 风险与后续事项

- 剩余风险：远端 Postgres / RabbitMQ 网络波动仍可能影响依赖这些组件的功能。
- 后续事项：如远端队列存在大量历史消息，建议在确认安全后清理。
- 阻塞项：TBD

## 14. 最终一致性检查

- 已交付的业务行为：TBD
- 已交付的技术实现：TBD
- 与原始计划的差异：TBD
- 验收标准满足情况：TBD
- 证据与验证：TBD
- 未验证事项：TBD
- 后续工作：TBD

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/reviews/2026-04-30-java-startup-log-fix-review.md`
- 审查重点：启动脚本默认行为是否符合本地开发预期；未知 job 消息是否应被丢弃。
- 已知需要审查的问题：是否需要清理远端 RabbitMQ 历史消息。
