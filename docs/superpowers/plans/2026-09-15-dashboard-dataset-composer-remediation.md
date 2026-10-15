# 仪表盘数据集编排整改实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将洞察仪表盘组件配置从“单组件直接绑定 + 独立脚本输入”补齐为兼容旧配置的数据集编排交互。

**Architecture:** 保留 `ComponentDataSource` 作为旧组件直接绑定契约；新增数据集输入的来源配置、字段映射、输入筛选和脚本绑定字段。前端以卡片编排输入数据集，后端继续复用已创建数据集和只读 JDBC 查询能力，不开放任意 API/文件地址。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vitest、Playwright/CDP、现有 Spring DataAgent API。

**Spec:** `docs/策略解读/原型设计.md`

## Global Constraints

- 保留旧仪表盘 Schema 的 `metrics/dimensions/filters` 兼容读取。
- API/文件输入只引用已创建数据集，不允许页面绕过数据集权限直接访问任意地址。
- JDBC SQL 只允许只读查询，执行仍使用现有 AST 校验和连接池。
- Python 脚本只做草稿和显式预览，不自动执行 AI 生成代码。
- 不纳入身份权限重构、Trino、pip install 或新的运行时依赖。

### Task 1: 扩展统一输入契约和纯函数

**Files:**
- Modify: `mateclaw-dataagent-ui/src/types/index.ts`
- Create: `mateclaw-dataagent-ui/src/utils/dataset-composer.ts`
- Test: `mateclaw-dataagent-ui/src/utils/__tests__/dataset-composer.spec.ts`

- [x] 写失败测试：来源配置、字段映射、筛选条件和 JDBC 只读校验可序列化；旧输入不丢失。
- [x] 运行定向 Vitest，确认新契约测试失败。
- [x] 增加最小类型和纯函数实现。
- [x] 运行定向 Vitest，确认通过。

### Task 2: 数据集独立卡片和来源配置

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue`
- Test: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`

- [x] 写失败测试：每个输入显示独立卡片；JDBC 显示 SQL；Aloudata 可选择指标视图/指标维度；API/文件显示已有数据集说明；字段映射、输入筛选、预览和移除入口存在。
- [x] 运行定向测试确认失败。
- [x] 实现卡片式渐进配置和表单校验，不改变已有数据集选择/预览 API。
- [x] 运行定向测试确认通过。

### Task 3: 筛选器绑定和脚本区域分层

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/utils/script-template.ts`
- Test: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`
- Test: `mateclaw-dataagent-ui/src/utils/__tests__/script-template.spec.ts`

- [x] 写失败测试：筛选器可以选择作用数据集和字段映射；系统生成区与用户处理区分开；多数据集要求用户处理逻辑。
- [x] 运行定向测试确认失败。
- [x] 实现声明式绑定、Base Script 生成和只读系统区展示。
- [x] 运行定向测试确认通过。

### Task 4: 编辑器接线、后端兼容和验证

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/InsightDataBindServiceImpl.java`
- Modify: `docs/superpowers/evidence/dashboard-mvp-acceptance.md`

- [x] 将数据集编排状态保存到 Schema，并兼容旧配置迁移。
- [x] 运行 UI 全量测试、生产构建和 DataAgent 测试。
- [x] 用 Chrome/CDP 按附件路径验证 JDBC、Aloudata、双数据集、筛选绑定和最终预览。
- [x] 记录截图、AX 和剩余差异。
