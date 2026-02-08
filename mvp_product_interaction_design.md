# 医疗 Agent Web 应用 MVP：产品与交互设计

## 1) 信息架构（Information Architecture）

### 1.1 顶层对象模型
- 用户（User）
- 病历记录（Record，按一次就诊/检查批次聚合）
- 原始文件（Asset，图片/PDF）
- 解析任务（Parse Job）
- 结构化结果（Structured Data）
- AI 输出（Summary / Medication Plan Draft）
- 操作审计（Audit Log）

### 1.2 导航结构（MVP）
- 首页
  - 最近记录
  - 待处理任务
  - 快速上传入口
- 上传
  - 文件选择/拖拽
  - 上传与解析状态
- 记录
  - 时间线列表
  - 记录详情
- 用药计划
  - 当前计划
  - 历史版本
- 我的
  - 账号与隐私
  - 免责声明与授权
  - 导出/删除申请

### 1.3 核心数据关系
- 一个 Record 可关联多个 Asset。
- 一个或多个 Asset 触发一个 Parse Job。
- 一个 Parse Job 产出一个 Structured Data（可修订）。
- 一个 Structured Data 可生成多个 AI 输出版本。

## 2) 页面与流程设计

### 2.1 核心用户流程（Happy Path）
1. 用户进入首页，点击上传。
2. 选择图片或 PDF，完成上传。
3. 系统进入解析流程并显示状态（上传中 -> 识别中 -> 结构化中 -> 生成中）。
4. 用户查看结构化结果，必要时手动修正低置信字段。
5. 用户选择生成检查结果摘要或用药计划草案。
6. 用户确认并保存，记录写入时间线，可回看追溯。

### 2.2 关键页面清单与职责
- 首页
  - 聚合任务状态、最近记录、快捷上传。
- 上传页
  - 文件选择、格式校验、进度展示、失败重试。
- 解析结果页
  - 原文与结构化结果对照、低置信高亮、手动编辑。
- AI 结果页
  - 显示摘要或用药计划草案，支持重新生成与版本保存。
- 记录详情页
  - 查看原始文件、结构化字段、AI 输出版本、编辑历史。

### 2.3 异常与边界流程（必须覆盖）
- 上传失败：格式错误、超大小、网络中断，给出明确原因与重试入口。
- 解析失败：OCR/LLM 超时或错误，支持稍后重试、重新提交。
- 低置信字段：标黄并引导用户确认，提供原文定位。
- 空结果或内容不可读：提示“无法识别”，建议重新拍摄/上传更清晰文件。
- 重复提交：通过文件指纹或任务去重提示，避免重复解析消耗。

## 3) 关键交互设计

### 3.1 上传交互
- 支持点击上传和拖拽上传。
- 前置校验：文件类型、大小、页数（如适用）。
- 提供实时上传进度、预计耗时、取消与重试能力。

### 3.2 解析反馈交互
- 明确分阶段状态，不只显示“处理中”。
- 允许用户离开页面，返回后可在首页继续查看进度。
- 状态超时时给出解释与下一步操作（重试/反馈）。

### 3.3 结构化确认交互
- 字段分组展示：检查项、指标数值、结论、药品信息。
- 低置信字段高亮，显示来源页码与原文片段。
- 用户编辑后可保存“修订版”，保留修改痕迹。

### 3.4 生成与保存交互
- 分开按钮：`生成检查摘要` 与 `生成用药计划草案`。
- 生成结果支持“重新生成”并形成新版本。
- 保存前显示风险提示：AI 仅供参考，不替代医生诊疗意见。

### 3.5 安全与合规交互
- 首次使用需确认隐私政策和免责声明。
- 涉及剂量/频次字段时增加二次确认。
- 提供删除数据与导出申请入口（满足基本数据权利）。

## 4) 原型与可用性验证（最小成本验证路径）

### 4.1 验证目标
- 验证用户是否能顺畅完成“上传 -> 理解 -> 修正 -> 保存”。
- 验证用户是否理解并接受 AI 输出的边界与免责声明。
- 验证异常场景下是否仍能完成核心任务。

### 4.2 两轮最小成本验证方案

#### 第一轮：低保真原型（线框）
- 工具：Figma 低保真线框 + 假数据。
- 样本：5-8 名目标用户（有体检/慢病管理需求）。
- 任务：上传文件、生成摘要、修正一个字段并保存。
- 采集：任务完成率、卡点步骤、主观清晰度评分（1-5）。

#### 第二轮：中保真原型（关键文案+状态）
- 增加异常流：上传失败、解析失败、低置信确认。
- 采集：完成时长、重试成功率、免责声明理解正确率。
- 结论：给出 go/no-go，并沉淀必须优化项。

### 4.3 可用性阈值（建议）
- 核心任务完成率 >= 80%。
- 首次完成任务中位时长 <= 3 分钟。
- 免责声明理解正确率 >= 90%。
- 低置信字段被正确修正比例 >= 70%。

## 5) 产出物定义

### 5.1 原型（Prototype）
- 低保真原型：覆盖主流程与 3 个异常流程。
- 中保真原型：补齐状态、文案、风险提示、版本管理。

建议交付文件：
- `prototype_low_fidelity.fig`（或等价链接）
- `prototype_mid_fidelity.fig`（或等价链接）

### 5.2 交互稿（Interaction Spec）
- 页面级：页面目标、入口、状态、跳转条件。
- 组件级：上传器、状态条、字段编辑器、版本切换。
- 异常级：错误码、提示文案、恢复动作、埋点触发。

建议交付文件：
- `interaction_spec_mvp.md`

### 5.3 埋点方案草案（Analytics Draft）

#### 漏斗事件（主链路）
- `view_upload`
- `upload_success`
- `parse_success`
- `generate_success`
- `record_save_success`

#### 详细事件（MVP）
- `view_home`
- `click_upload`
- `upload_start` / `upload_fail`
- `parse_start` / `parse_fail`
- `view_structured_result`
- `edit_structured_field`
- `save_structured_edit`
- `click_generate_summary` / `generate_summary_success` / `generate_summary_fail`
- `click_generate_med_plan` / `generate_med_plan_success` / `generate_med_plan_fail`
- `save_record_success`
- `view_history_record`

#### 关键属性（Properties）
- `file_type`（jpg/png/pdf）
- `file_size_mb`
- `page_count`
- `parse_duration_ms`
- `model_name`
- `confidence_bucket`（high/medium/low）
- `has_manual_edit`（bool）
- `edited_field_count`
- `error_code`
- `user_type`（self/family）

建议交付文件：
- `analytics_tracking_plan_draft.md`

## 6) 与需求文档对齐关系（用于后续 AI 开发）
- 本文档对应 `mvp_requirements_and_goals.md` 的实现视角，聚焦“怎么做页面与交互”。
- 若发生冲突，以 `mvp_requirements_and_goals.md` 的范围边界与成功指标为准。
- 建议后续补充一份追踪矩阵文档：将“需求目标 -> 页面流程 -> 埋点指标 -> 验收用例”做一一映射。
