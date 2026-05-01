# Agent 启动 Python 3.14 虚拟环境修复

## 1. 元数据

- 状态：待审查
- 负责人：Codex
- 开始日期：2026-05-01
- 最后更新日期：2026-05-01
- 相关请求：agent 项目启动报错，日志显示 Python 3.14 reload 子进程在导入 `aio_pika` / `aiormq` 时被 `KeyboardInterrupt` 中断，并出现 Pydantic V1 与 Python 3.14 兼容性警告。
- 相关分支 / 提交 / PR：TBD
- 需求达标审查报告：TBD

## 2. 原始需求

- 用户原始诉求：agent项目启动报错，并提供启动日志。
- 原始上下文：目标项目为 `backend-agent`，当前仓库位于 `F:\maven_product\medical_agent`。
- 后续补充：无

## 3. 摘要

本次排查确认日志中的 `KeyboardInterrupt` 出现在 uvicorn reload 子进程重启过程中，后续日志已经显示 `Application startup complete`，因此该段不是最终启动失败。实际可复现的本地问题是当前 `.venv` / `.venv314` 指向的 uv 管理 Python 3.14 解释器路径不可访问，导致 `uv run` / `uv sync` 在查询解释器时失败。已将坏掉的 `.venv` 改名保留，并使用可访问的 `D:\python3.14\python.exe` 重新创建 `.venv`；同时在启动脚本中使用项目内 `.uv-cache`，绕开本机 uv 全局 cache 目录异常。

## 4. 背景和目标

- 业务背景：`backend-agent` 当前已按前序需求升级到 Python 3.14，启动依赖 FastAPI、uvicorn、aio-pika、LangChain 等第三方库。
- 用户 / 问题陈述：启动日志中出现 reload 后的 Python traceback，用户需要判断是否为真实启动失败并修复当前环境问题。
- 目标：恢复本机 `backend-agent` 的 `uv run` 启动能力，并保留 Python 3.14 升级成果。
- 成功标准：`uv run` 能使用可访问的 Python 3.14 虚拟环境导入 `app.main`；启动脚本不再依赖异常的 uv 全局 cache 目录。

## 5. 范围边界

### 本次做

- 定位启动日志和本地虚拟环境问题。
- 重建 `backend-agent/.venv` 到可访问的 Python 3.14。
- 调整根目录 `start-backend-agent.ps1` 使用项目本地 `.uv-cache`。
- 记录第三方依赖警告作为剩余风险。

### 本次不做

- 不回退 Python 3.14 升级。
- 不修改业务代码、接口、数据库或 MQ 行为。
- 不升级 LangChain / oss2 等第三方依赖。

### 假设

- 当前仍要求保留前序 Python 3.14 升级结果。
- `D:\python3.14\python.exe` 是本机可用的 CPython 3.14 解释器。
- 日志中的 `KeyboardInterrupt` 与 reload/停止旧子进程相关，不代表最终进程启动失败。

### 待确认问题

- 是否要继续保留 Python 3.14，还是回退到项目早期文档中的 Python 3.11/3.12：TBD。

## 6. 验收标准

- [x] 标准 1：确认当前虚拟环境解释器路径是否可访问。
- [x] 标准 2：重建 `.venv` 后，`uv run` 能使用 Python 3.14 导入 `app.main`。
- [x] 标准 3：启动脚本使用项目本地 uv cache，避免本机全局 cache 异常影响启动。
- [x] 标准 4：明确记录未处理的第三方依赖警告和后续风险。

## 7. 受影响的系统和文件

- 项目 / 服务：`backend-agent`
- 主要模块 / 文件：`start-backend-agent.ps1`、`backend-agent/.venv`
- API / 路由：无
- 数据库 / 表 / 字段：无
- 配置：`UV_CACHE_DIR=.uv-cache`
- 定时任务 / MQ / 外部依赖：无业务行为变更；启动导入仍会加载 MQ 相关依赖。

## 8. 实施方案

- 方案概述：先确认 `.venv` 指向的 Python 3.14 不可访问，再保留坏环境并用本机可访问的 Python 3.14 重新创建 `.venv`；最后给启动脚本补充本地 uv cache 设置。
- 关键设计决定：不修改 `pyproject.toml` 的 Python 3.14 约束，因为这是前序已完成需求；本次只修复本机环境和启动脚本。
- 替代方案与取舍：可回退到 Python 3.12/3.11 来消除 LangChain 警告，但会撤销前序 Python 3.14 升级，并影响 UUID7 相关实现和测试。
- 风险：Python 3.14 下仍有第三方依赖兼容性警告，虽然当前导入通过，但后续应单独评估依赖升级或 Python 版本策略。

## 9. 实施计划

1. 检查启动脚本、Python 版本约束和虚拟环境指向。
2. 重建 `.venv` 到可访问的 Python 3.14。
3. 验证 `uv run` 可导入 `app.main`。
4. 更新启动脚本和需求文档。

## 10. 进度日志

- 2026-05-01：确认 `pyproject.toml` 锁定 `>=3.14,<3.15`，`uv.lock` 锁定 `==3.14.*`。
- 2026-05-01：确认 `.venv` 和 `.venv314` 均指向 `C:\Users\h4573\AppData\Roaming\uv\python\cpython-3.14.0-windows-x86_64-none`，该路径下 `python.exe` 当前不可访问。
- 2026-05-01：将 `backend-agent/.venv` 改名为 `.venv.bad-python314` 保留现场，并用 `D:\python3.14\python.exe` 重新创建 `.venv`。
- 2026-05-01：验证 `uv run python -c "import app.main"` 成功。
- 2026-05-01：更新 `start-backend-agent.ps1`，设置 `UV_CACHE_DIR=.uv-cache`。

## 11. 代码变更清单

| 文件 / 模块 | 变更说明 | 对应验收标准 |
| --- | --- | --- |
| `start-backend-agent.ps1` | 启动前设置项目本地 `UV_CACHE_DIR=.uv-cache` | 标准 3 |
| `backend-agent/.venv` | 重新创建到 `D:\python3.14`，原坏环境改名保留为 `.venv.bad-python314` | 标准 1、2 |

## 12. 验证与测试

- 计划检查：
  - `Get-Content .venv\pyvenv.cfg`
  - `uv run python -c "import sys, platform; ...; import app.main"`
- 已完成检查：
  - `.venv\pyvenv.cfg` 当前显示 `home = D:\python3.14`、`version_info = 3.14.0`。
  - `UV_CACHE_DIR=.uv-cache uv run python -c "import sys, platform; print(sys.executable); print(platform.python_version()); print(platform.system()); import app.main; print('import ok')"` 成功，输出 Python 3.14.0、Windows、`import ok`。
  - 使用 `MQ_CONSUMER_ENABLED=false`、`UVICORN_RELOAD=false` 尝试启动 uvicorn，应用启动阶段完成并进入 `Application startup complete`，随后因 `0.0.0.0:8090` 已被 PID 7884 占用而退出。
  - `netstat -ano | findstr :8090` 显示 PID 7884 正在监听 8090。
  - `Invoke-RestMethod http://127.0.0.1:8090/health` 返回 `{"status":"ok"}`，说明当前监听中的 agent 服务健康检查可用。
- 未运行 / 尚未验证：
  - 未运行完整 pytest。
- 未验证原因：
  - 8090 已有运行中的进程监听；为避免中断用户当前服务，未停止 PID 7884 后重新启动。

## 13. 风险与后续事项

- 剩余风险：
  - `langchain_core` 仍提示 Pydantic V1 compatibility 不兼容 Python 3.14+；当前是警告，不是导入失败。
  - `oss2` 在 Python 3.14 下出现无效转义 `SyntaxWarning`；当前是第三方库警告。
  - `.venv314` 仍指向不可访问的 uv 管理解释器，如继续使用该环境变量 `UV_PROJECT_ENVIRONMENT=.venv314` 会再次失败。
- 后续事项：
  - 如果决定保留 Python 3.14，建议单独升级或替换仍有兼容警告的第三方依赖。
  - 如果决定以稳定启动为优先，可另起需求回退到 Python 3.12/3.11，但需要同步替换 UUID7 方案。
- 阻塞项：无

## 14. 最终一致性检查

- 已交付的业务行为：无业务行为变更。
- 已交付的技术实现：`backend-agent/.venv` 已重建到可访问 Python 3.14；启动脚本使用项目本地 uv cache。
- 与原始计划的差异：未修改业务代码；修复点集中在本机环境和启动脚本。
- 验收标准满足情况：4 条验收标准均已满足。
- 证据与验证：`uv run` 导入 `app.main` 成功，`.venv\pyvenv.cfg` 指向 `D:\python3.14`。
- 未验证事项：完整测试未运行。
- 后续工作：清理或重建 `.venv314`；评估 Python 3.14 第三方依赖告警。

## 15. Requirement Doc Review 交接

- 审查状态：待审查
- 建议审查报告路径：`docs/requirements/2026-05-01-agent-startup-python314-venv-fix-review.md`
- 审查重点：确认本次是否只修复启动环境和脚本，没有误改业务代码；确认是否需要继续保留 Python 3.14。
- 已知需要审查的问题：`.venv314` 尚未重建，第三方依赖 Python 3.14 警告尚未清理。
