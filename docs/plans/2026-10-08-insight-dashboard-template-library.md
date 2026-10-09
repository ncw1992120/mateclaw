# 洞察「策略解读」→ 团队共享样例模板库 实现计划

> 状态：设计定稿，待评审后实施
> 分支：在 `feature/dev_fu` 上开发，浏览器验证后中文提交并 push
> 关联：已配置好的「策略解读」仪表盘（含数据集 table_ab、筛选器、Python 脚本、主题）

## 0. 已确认的设计决策

| 维度 | 决策 |
|------|------|
| 可见范围 | **团队/组织共享样例库**（非个人模板） |
| 数据集处理 | **带示例数据集直接可用**：复制即带真实绑定，立刻可预览/参考，不做参数化 |
| 入口形态 | 现有**仪表盘列表页加「模板」页签**（与「我的仪表盘」并列），非独立路由页 |
| 工程原则 | 复用后端已有 `/copy` 与 `/preview`，**不重造克隆引擎** |

## 1. 目标与范围

- 把已配好的「策略解读」做成可被全团队**发现、参考、一键派生**的样例。
- 「参考」价值：用户无需克隆即可在只读详情/预览中学习方法范式（卡片布局、筛选绑定、Python 脚本组织）。
- 「快速」价值：一键「基于此创建」→ 副本带示例数据立即可跑，顶部非阻塞提示替换数据源。
- 本期**不做**：参数化引擎、模板版本双向同步、官方模板审核流（仅用 `isOfficial` 标记区分）。

## 2. 数据模型（后端 `mateclaw-dataagent`）

核查现状：`InsightDashboardVO`（`dto/InsightDashboardVO.java`）仅有 `id/name/description/schemaJson/reportContent/status/agentId/workspaceId/ownerId/ownerName/modifier/createTime/updateTime`，**无 visibility / templateMeta**。控制器 `DataAgentInsightDashboardController.java` 已有 `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`DELETE /{id}`、`POST /{id}/copy`、`POST /{id}/preview`。

### 2.1 字段扩展
- `InsightDashboard` 实体 + `InsightDashboardVO` + `InsightDashboardCreateRequest` / `InsightDashboardUpdateRequest` 增加：
  - `visibility` 枚举：`private`（默认）/ `workspace` / `template` / `official`
  - `templateMeta`（JSON/String）：`{ tags?: string[]; category?: string; cover?: string; isOfficial?: boolean; usageCount?: int; sourceDashboardId?: long }`
- DB：dashboard 表加 `visibility` 列（默认 `private`）+ `template_meta` 列（JSON/text）；`InsightDashboardMapper.xml` 同步列映射。

### 2.2 接口扩展
- `listDashboards` 支持按可见性过滤：新增参数 `visibility`（可传 `template,official` 或 `mine`）。现有 `list()` 按 workspace 隔离，模板在 **workspace 内共享**，跨 workspace 不共享（沿用现有隔离）。
- 新增 `POST /v1/insight/dashboards/{id}/save-as-template`：复制当前看板为 `visibility='template'` 的新看板，写入 `templateMeta`（来源 id、作者、默认 tags/category 由请求体带入）。官方样例由后端种子或管理操作置 `official`。
- **核对并修正 `copyDashboard`**：副本必须 `visibility='private'`、`ownerId=当前用户`、清空 `templateMeta`（fork 语义：作者改模板不影响已派生副本）。需先读 `InsightDashboardServiceImpl.copyDashboard` 确认现状，否则补改。

## 3. 前端（`mateclaw-dataagent-ui`）

### 3.1 类型与 API
- `src/types/index.ts`：`InsightDashboard` 加 `visibility?`、`templateMeta?`；`InsightDashboardCreateInput` / `InsightDashboardUpdateInput` 同步。
- `src/api/insight-dashboard.ts`：`list(params?: { visibility?: string })`；新增 `saveAsTemplate(id, meta)`。

### 3.2 仪表盘列表 `src/views/insight/DashboardListView.vue`
- 新增**顶层主 Tab**：「我的仪表盘」|「模板」（放在现有 `filter-tabs` 状态子页签上方，作为一级切换）。
- 「模板」态：`store.fetchDashboards({ visibility: 'template,official' })`；卡片**隐藏** 编辑/发布/删除/取消发布，操作改为 **预览（只读）** + **基于此创建（copy）** + 官方标记。
- 卡片展示 `templateMeta.cover` / `tags` / `category` / `usageCount`。
- 「存为样例模板」入口：在我的仪表盘卡片操作栏加「存为模板」按钮，或在列表页头加入口。

### 3.3 编辑器 `src/views/insight/InsightDashboardEditorView.vue`
- 顶部「存为样例模板」按钮 → 调 `saveAsTemplate`，弹窗填 名称 / 描述 / 标签 / 分类 / 封面（首屏截图方案待定：前端 DOM 截图或后端无头渲染）。
- 打开官方模板（`visibility='official'`）：**只读模式**，仅允许「基于此创建」。
- 用户从模板 copy 进来的副本：顶部一条**非阻塞提示**「本模板示例数据来自 xxx，点此替换为你的数据」→ 跳到数据集绑定编辑。

### 3.4 状态 `src/stores/useInsightDashboardStore.ts`
- `fetchDashboards(params?)` 透传 `visibility` 过滤参数给 `insightDashboardApi.list`。

## 4. 实施阶段（建议提交顺序，每阶段可独立验证）

- **P1 后端模型 + list 过滤 + copy fork 语义**（含单测）
- **P2 `save-as-template` 端点 + 官方样例种子/管理**
- **P3 前端类型 / API / store 过滤参数**
- **P4 列表页「模板」Tab + 模板卡片 + 「存为模板」入口**
- **P5 编辑器「存为模板」+ 官方只读 + 副本「替换示例数据」提示**
- **P6 Codex 内部浏览器验证 → 中文提交 → push `feature/dev_fu`**

## 5. 验证清单

- **后端单测**：`list` 按 visibility 过滤正确；`copy` 产生 `private` 副本且 `templateMeta` 清空；`save-as-template` 生成 `template` 看板且记录来源。
- **前端单测**：`DashboardListView` 模板态渲染与卡片操作；`store` 过滤参数透传；编辑器替换提示显隐逻辑。
- **浏览器（Codex 内部浏览器）**：
  1. 列表出现「模板」Tab；「策略解读」样例出现并可预览（只读，无编辑/删除）。
  2. 「基于此创建」→ 进入可编辑副本，示例数据（table_ab 等）立即可跑、可参考。
  3. 在我的仪表盘「存为模板」后，该看板出现在「模板」Tab。
  4. 官方模板编辑态为只读，仅可派生。

## 6. 风险 / 待核对项

- `copyDashboard` 当前是否重置 `ownerId` / `visibility`？—— 必须读 `InsightDashboardServiceImpl.copyDashboard` 确认，否则补改（fork 语义前提）。
- 现有 `list` 是否严格按 workspace 隔离？模板需在 workspace 内共享，跨 workspace 不可见。
- 封面截图方案（**已定，2026-10-18**）：先占位图 / 分类色块，**暂不接入截图**；截图能力后续迭代。P2/P5 不再依赖截图能力，存为模板时由用户填分类标签驱动占位图样式。
- 官方样例落地方式：后端种子 SQL 还是管理后台操作？先人工/种子跑通 MVP。
- 权限：模板中心的「存为模板」「基于此创建」需对齐现有 `PERMISSION.INSIGHT_CREATE` 与 `canModifyResource` 逻辑。
