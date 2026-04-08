# dev / dev_office 环境入口对齐与工作区清理

## 元数据

- 状态：已完成
- 负责人：Codex
- 开始日期：2026-04-08
- 最后更新日期：2026-04-08
- 相关请求：分析工作区所有未提交文件，清理无关改动并提交 commit，确保 `dev.ps1` 默认启动 192/home 环境，`dev_office.ps1` 默认启动 100/office 环境。

## 摘要

- 当前工作区里混有无关的 `.env`、`.gitignore`、`package-lock` 等未提交改动，且 `dev` / `dev_office` 的默认环境入口并不稳定。`backend-agent/dev.ps1` 依赖已跟踪的 home `.env`，`backend-agent/dev_office.ps1` 依赖本地 office 文件，`backend-java` 两个启动脚本都还会被普通 `.env` 和本机 Java 环境干扰。本次交付先清理无关改动，再将四个启动入口对齐为明确的 home / office 语义。

## 背景和目标

- 业务背景：开发者需要通过 `dev.ps1` 和 `dev_office.ps1` 稳定启动 home / office 两套开发环境，而不依赖当前工作区碰巧残留的 `.env` 内容。
- 用户/问题陈述：工作区存在多份未提交文件，其中一部分与本次需求无关；同时脚本默认环境不稳定，无法保证 `dev.ps1=192`、`dev_office.ps1=100`。
- 成功标准：
  - 无关未提交改动被回滚或排除在最终提交之外
  - `backend-agent/dev.ps1` 默认走已跟踪的 home `.env`
  - `backend-agent/dev_office.ps1` 默认优先加载 `.env_office`，缺失时回退到可提交的 office 示例文件
  - `backend-java/dev.ps1` 默认走 `home` profile
  - `backend-java/dev_office.ps1` 默认走 `office` profile
  - Java 启动脚本不再被普通 `.env` 和本机 `JAVA_HOME=JDK11` 干扰

## 范围

- 范围内：回滚与本次需求无关的未提交改动
- 范围内：修改 `backend-agent/dev_office.ps1`
- 范围内：修改 `backend-java/dev.ps1`
- 范围内：修改 `backend-java/dev_office.ps1`
- 范围内：新增可提交的 `backend-agent` office 示例配置
- 范围内：验证 `dev` / `dev_office` 默认环境语义

## 范围外

- 范围外：修改 `switch-env.ps1`
- 范围外：重构整体配置体系
- 范围外：调整业务代码逻辑

## 受影响的系统和文件

- 项目/服务：`backend-agent`、`backend-java`
- 主要模块/文件：`backend-agent/dev_office.ps1`、`backend-agent/.env_office.example`、`backend-java/dev.ps1`、`backend-java/dev_office.ps1`、`.gitignore`
- 配置/路由/表/API：`backend-agent/.env`、`backend-agent/.env_office`、`backend-java/src/main/resources/application-home.properties`、`backend-java/src/main/resources/application-office.properties`
- 外部依赖项：FastAPI 启动参数、Spring Boot profile 加载顺序

## 实施计划

1. 盘点所有未提交文件，区分无关改动与本次需要保留的改动。
2. 回滚无关工作区变更，恢复 `backend-agent/.env` 和 `.env.example` 的已提交状态。
3. 修改 `dev` / `dev_office` 脚本，使 home / office 默认入口不再依赖普通 `.env` 残留状态。
4. 增加 office 示例配置和忽略规则，既保证启动语义，又避免把本地 secrets 带进提交。
5. 用最小 dry-run / 脚本级验证确认 home / office 入口语义正确。

## 进度日志

- 2026-04-08：创建文档并确认初始目标。
- 2026-04-08：盘点未提交文件，回滚 `.gitignore`、`frontend/package-lock.json`、`backend-agent/.env`、`backend-agent/.env.example` 中与本次需求无关的工作区改动。
- 2026-04-08：为 `backend-agent/dev_office.ps1` 增加 `.env_office` -> `.env_office.example` 回退链路，并新增可提交的 `backend-agent/.env_office.example`。
- 2026-04-08：将 `backend-java/dev.ps1` 默认 profile 调整为 `home`，取消默认读取普通 `.env`，并增加 Java 21 路径兜底。
- 2026-04-08：将 `backend-java/dev_office.ps1` 默认 profile 固定为 `office`，保留显式 `-EnvFile` 覆盖，并增加 Java 21 路径兜底。
- 2026-04-08：补充 `.gitignore`，忽略本地 `.env_office` / `config.local.json` 类文件，同时保留 `.env_*.example` 可提交。
- 2026-04-08：完成 `dev` / `dev_office` 最小验证。

## 验证与测试

- 计划检查：验证 `backend-agent/dev.ps1` 仍走 home `.env`；验证 `backend-agent/dev_office.ps1` 优先 office 文件并具备 office 示例回退；验证 `backend-java/dev.ps1` 默认 `home`；验证 `backend-java/dev_office.ps1` 默认 `office`。
- 已完成检查：
  - 通过脚本文本检查确认 `backend-agent/dev.ps1` 默认 `EnvFile=.env`
  - 通过脚本文本检查确认 `backend-agent/dev_office.ps1` 默认 `EnvFile=.env_office` 且存在 `.env_office.example` 回退链路
  - 执行 `backend-java\dev.ps1 -DryRun`，输出显示 `Profile: home`
  - 执行 `backend-java\dev_office.ps1 -DryRun`，输出显示 `Profile: office`
  - 两个 Java dry-run 输出都显示 `Env file: <none> (using Spring profile defaults)`
  - 两个 Java dry-run 输出都显示 `JAVA_HOME: D:\JDK21`
- 未运行/尚未验证：未实际把 `backend-agent/dev.ps1` 和 `backend-agent/dev_office.ps1` 拉起到完整服务运行态，仅做脚本级验证。

## 风险与待解决问题

- 风险：`backend-agent/.env_office.example` 是可提交的 office 示例模板，但其中 secrets 仍需开发者本地补齐；若直接拿示例文件跑真实业务，请求链路可能因为空 key 失败。
- 风险：`backend-java` 目前依赖 Spring `application-home.properties` / `application-office.properties` 提供主机地址；若后续 office / home 还需要更多变量，可能需要继续扩展 profile 或专用 env 文件。
- 待解决问题：是否后续需要把 `backend-agent` 的 home / office 配置都统一成 `.example + 本地私有文件` 的成对结构。

## 最终一致性检查

- 已交付的业务行为：`dev.ps1` 默认启动 192/home 环境，`dev_office.ps1` 默认启动 100/office 环境；office 启动不再依赖普通 `.env` 当前是否碰巧已经切对。
- 已交付的技术实现：回滚了无关工作区改动；`backend-agent/dev_office.ps1` 改为 `.env_office` 优先、`.env_office.example` 回退；`backend-java/dev.ps1` / `dev_office.ps1` 改为分别固定 `home` / `office` profile，并取消对普通 `.env` 的隐式加载；Java 启动脚本增加 Java 21 路径兜底。
- 与原始计划的差异：无。
- 证据与验证：已完成工作区清理，并通过脚本文本检查与 Java `-DryRun` 验证了 home / office 两个入口的默认环境语义。
- 后续工作：如需进一步统一规范，可继续补 `backend-agent/.env_home.example` 与成对的本地私有 home 文件。
