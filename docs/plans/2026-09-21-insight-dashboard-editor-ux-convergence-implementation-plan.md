# 洞察仪表盘编辑器 UI/UX 收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有数据集、SQL、Aloudata、Python 与结果集业务契约的前提下，统一洞察仪表盘编辑器的属性配置状态、信息层级、弹窗/表单/按钮样式、画布交互和反馈方式，并用前后端单元测试、测试工程师用例、真实 Google Chrome CDP 9222 视觉证据完成验收。

**Architecture:** 保留 `InsightDashboardEditorView` 作为唯一正式编辑器外壳，页面树、组件库和画布继续复用现有实现；将数据组件与控制/容器组件的属性配置收敛到统一侧栏契约和单一草稿状态，业务数据仍写入组件级 `config.datasetPipeline`。视觉规则由洞察编辑器局部设计令牌和复用组件承载；后端不新增 UI 偏好模型，只补强 Dashboard Schema、数据集草稿、预览和执行接口的契约回归。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Pinia、Vitest、Vue Test Utils、Playwright、Google Chrome CDP `9222`；Spring Boot、Java 21、JUnit 5、Mockito、Jackson；现有 DataAgent、Python Runner 与本地模拟环境。

**Spec:** `docs/策略解读/原型设计.md`、`docs/plans/2026-09-15-dashboard-component-dataset-configuration-plan.md`、`docs/plans/2026-09-21-insight-python-script-complete-implementation-plan.md`、项目根 `AGENTS.md` 与 `MEMORY.md`。

## 实施进度

- [x] Task 1：统一属性配置基础视觉组件（Vitest 单测 41 files / 235 tests 通过；commit 待创建）
- [ ] Task 2：统一属性草稿状态与保存边界
- [ ] Task 3：收敛卡片属性配置布局与信息层级
- [ ] Task 4：收敛数据集来源选择与配置弹窗
- [ ] Task 5：统一 Python、筛选器、结果集交互
- [ ] Task 6：统一画布、组件库与页面树交互
- [ ] Task 7：补强后端 Schema、预览、保存与执行契约回归
- [ ] Task 8：正式路由隔离与原型页边界
- [ ] Task 9：单元测试、集成测试与测试数据补全
- [ ] Task 10：Chrome CDP 9222 视觉验收与可访问性检查
- [ ] Task 11：最终回归、证据归档与发布检查

> 更新规则：每完成一个 Task，必须在本节勾选并记录测试命令、结果和对应中文 commit；未执行或被环境阻塞的项目不得标记为完成。

## Global Constraints

- 正式入口只使用 `InsightDashboardEditorView`；原型路由和假数据不得进入生产导航、正式保存链路或验收结论。
- 页面树、组件库、画布继续采用原正式版本；本计划只做体验收敛，不重写布局引擎。
- 卡片属性顺序固定为：组件标题 → KPI 专属模式 → 数据集配置 → 筛选器绑定 → Python 预处理 → 结果集 → KPI 指标配置。
- 数据集入口只保留“+ 添加数据集”；搜索只存在于“添加数据集”打开的数据源选择树；来源分类固定为 Aloudata、JDBC、接口、文件。
- JDBC、Aloudata、HTTP/API、文件、字段映射、筛选下推、Python Base Script、结果集和历史 Schema 兼容能力不得因 UI 收敛退化。
- 帮助说明默认不常驻占用面板空间；操作含义使用 Info 图标、Tooltip、Popover 或聚焦帮助，错误、警告、运行状态和结果过期状态必须就地可见。
- 每个作用域最多一个主按钮；保存、执行、上传等异步操作必须有 loading、成功或错误反馈，普通字段修改不得连续弹 Toast。
- 中文表单标签不得使用大写转换或无意义字距；控件、圆角、间距、颜色、图标和弹窗宽度必须复用统一令牌。
- 图标统一使用 Element Plus 图标或项目 SVG；正式页面不使用 Emoji 充当图标。
- 不引入新的前端 UI 框架，不引入 DuckDB、Trino、`pip install` 或新的身份权限体系。
- 后端接口不得保存纯 UI 状态；本轮后端修改只允许用于契约校验、Schema 兼容和稳定的错误返回。
- 所有修改必须保留工作区中与本计划无关的未提交内容；每次提交只暂存任务列出的路径。
- 真实视觉验收必须连接 `http://127.0.0.1:9222` 上由用户启动的 Google Chrome；Playwright 自行启动的 Chromium/Chrome 不能替代该项证据。

## Review Focus

- 在 KPI、图表、表格、筛选器、时间筛选器和组合卡片之间连续切换时，侧栏不得残留上一个组件的草稿、结果集或弹窗状态；Task 2 的切换测试覆盖。
- 已有旧 Dashboard Schema、组件级 `datasetPipeline` 和旧 `dataSource/tabs[].dataSource` 均能读取、编辑、保存、刷新；Task 3 和 Task 7 的前后端 round-trip 测试覆盖。
- 长数据源名、长字段名、长错误信息、20 个以上字段及 8 个 Tab 不得撑破右侧栏或弹窗；Task 4、Task 5 和 QA-18～QA-21 覆盖。
- 预览、保存、执行中途发生 400、403、503、超时、取消或空结果时，当前配置不得丢失，错误必须显示在对应区域；Task 4、Task 7 和 QA-22～QA-27 覆盖。
- 375、768、1024、1280、1440 宽度以及 light/warm/eye-care/dark 四主题下，不得出现横向页面溢出、遮挡、不可见焦点或低对比度正文；Task 6、Task 9 和 CDP 视觉门禁覆盖。

---

## 0. 当前基线、范围与非目标

当前编辑器在同一正式页面内并存两套属性配置体系：数据组件由 `CardAttributeSidebar.vue` 驱动，筛选、时间筛选、AI 分析和组合卡片由 `PropertyPanel.vue` 驱动；前者使用 `useInsight()` 的面板状态和 300ms 回写，后者维护另一套局部状态。数据集配置还分散在 `DatasetInputPanel.vue`、`DatasetSourceDialog.vue` 和 `card-attribute/dataset/*Dialog.vue` 中，弹窗宽度、按钮层级、错误呈现方式和帮助文案密度不一致。

本计划解决：

1. 属性配置状态与保存契约统一。
2. 常驻帮助文案、Toast 和低频动作收敛。
3. 数据集来源配置流程及弹窗层级收敛。
4. 表单、按钮、图标、空状态和主题令牌统一。
5. 画布鼠标、键盘、触屏替代操作和响应式改进。
6. 前后端契约回归、QA 手工用例和 Chrome 9222 视觉验收。

本计划不做：

- 不重新设计数据集后端模型、Python Runner 协议或 Aloudata Adapter。
- 不改变原型文档已确认的字段和操作顺序。
- 不把原型页升级为第二套正式入口。
- 不以像素级复刻替代可用性、业务结果和无障碍验收。
- 不在本轮处理全站所有页面，只处理洞察仪表盘编辑器及其直接弹窗。

## 1. 目标交互与视觉规则

### 1.1 正式编辑器结构

```text
顶部工具栏
├── 返回 / 仪表盘名称与描述
└── AI 助手 / 保存 / 预览

编辑区
├── 页面树（可折叠）
├── 组件库（可折叠）
├── 画布
└── 属性配置（统一侧栏，可折叠）
    ├── 组件标题
    ├── KPI 专属模式
    ├── 数据集配置
    ├── 筛选器绑定
    ├── Python 预处理
    ├── 结果集
    └── KPI 指标配置
```

### 1.2 提示信息分级

| 信息类型 | 呈现方式 | 示例 |
| --- | --- | --- |
| 名词解释、限制说明 | 标签后的 Info 图标 + Tooltip/Popover | SQL 只读限制、Python 系统区域说明 |
| 当前步骤帮助 | 控件获得焦点或区域展开后显示 | 输入别名规则、筛选值格式 |
| 当前状态 | 就地状态标签 | 字段探测中、结果集已过期、执行中 |
| 可恢复警告 | 就地 Alert，可关闭 | 历史字段引用无法解析 |
| 字段错误 | 控件下方错误文字 + `role="alert"` | SQL 为空、别名重复 |
| 页面级失败 | 区域 Alert；必要时再补一次 Toast | 数据源不可达、保存失败 |
| 成功反馈 | 保存/上传/执行成功可短暂 Toast | 仪表盘保存成功 |
| 普通编辑 | 不弹 Toast | 标题修改、切换 Tab、新增筛选行 |

### 1.3 尺寸和层级基线

| 项目 | 固定规则 |
| --- | --- |
| 普通控件高度 | 32px |
| 图标按钮可点击热区 | 不小于 32×32px；画布缩放热区不小于 20px |
| 属性侧栏宽度 | 桌面 360px；最小 320px；最大 420px |
| 小弹窗 | 420px，适用于确认和简单表单 |
| 来源配置弹窗 | 640px |
| 字段/指标配置弹窗 | 760px |
| 数据预览弹窗 | `min(960px, calc(100vw - 48px))` |
| 移动端弹窗 | `calc(100vw - 24px)` |
| 圆角 | 控件 6px、卡片 8px、弹窗 10px |
| 区域间距 | 16px；区域内部 8px；紧密动作 4px |
| 动画 | 150～200ms，并尊重 `prefers-reduced-motion` |
| 主按钮 | 每个区域最多 1 个；右侧或底部最右 |

## 2. 文件职责与接口边界

### 前端新增文件

| 文件 | 职责 |
| --- | --- |
| `mateclaw-dataagent-ui/src/views/insight/components/property/useComponentPropertyDraft.ts` | 统一选中组件的草稿装载、修改、校验、重置和回写，隔离 `useInsight()` 单例状态。 |
| `mateclaw-dataagent-ui/src/views/insight/components/property/PropertySection.vue` | 属性面板通用分区，统一标题、Info、状态、折叠和间距。 |
| `mateclaw-dataagent-ui/src/views/insight/components/property/InlineHelp.vue` | Info 图标、Tooltip/Popover、键盘触发和移动端点击帮助。 |
| `mateclaw-dataagent-ui/src/views/insight/components/property/EditorEmptyState.vue` | 统一画布与属性区空状态，可选一个主操作。 |
| `mateclaw-dataagent-ui/src/views/insight/components/property/__tests__/useComponentPropertyDraft.spec.ts` | 草稿隔离、切换、回写和兼容测试。 |
| `mateclaw-dataagent-ui/src/views/insight/components/property/__tests__/PropertySection.spec.ts` | 标题、帮助、折叠和无障碍测试。 |
| `mateclaw-dataagent-ui/e2e/dashboard-editor-ux.spec.ts` | 编辑器信息层级、对话框、键盘、响应式与主题 E2E。 |
| `mateclaw-dataagent-ui/e2e/cdp-dashboard-editor-ux-check.mjs` | 只连接真实 Google Chrome 9222，采集截图、AX、控制台和网络证据。 |
| `docs/superpowers/evidence/dashboard-editor-ux-acceptance.md` | 记录候选 SHA、环境、测试输出、截图和结论。 |

### 前端修改文件

| 文件 | 本轮职责 |
| --- | --- |
| `src/views/insight/InsightDashboardEditorView.vue` | 保持唯一正式编辑器，接入统一属性草稿；移除同一组件由两套侧栏分流的行为。 |
| `src/views/insight/components/card-attribute/CardAttributeSidebar.vue` | 降为统一侧栏容器或删除其单例桥接职责；不得继续拥有独立事实源。 |
| `src/views/insight/components/card-attribute/AttributePanel.vue` | 按固定顺序组织属性区，使用通用分区、帮助和状态组件。 |
| `src/views/insight/components/card-attribute/useInsight.ts` | 只保留可复用业务函数；组件草稿不得继续由全局单例承载。 |
| `src/views/insight/components/card-attribute/useCardAttributeBridge.ts` | 收敛为 Schema ↔ draft 的纯转换函数，并保证字段无损。 |
| `src/views/insight/components/PropertyPanel.vue` | 迁移筛选器、时间筛选器、AI 分析和组合卡片配置到统一侧栏样式和草稿契约。 |
| `src/views/insight/components/DatasetSourcePicker.vue` | 清晰区分已有数据集与新建来源；保留四类来源，不混淆数据集和 JDBC 连接。 |
| `src/views/insight/components/DatasetSourceDialog.vue` | 合并配置与预览区域，移除叠加弹窗；统一校验和 footer 动作。 |
| `src/views/insight/components/card-attribute/dataset/*Dialog.vue` | 复用统一弹窗壳、表单行、错误和按钮层级。 |
| `src/views/insight/components/DashboardCanvas.vue` | 统一空状态、图标、选中操作和缩放可发现性；补键盘替代。 |
| `src/views/insight/components/ComponentPalette.vue` | 统一 Tooltip、焦点和添加方式；保持现有 SVG 图标。 |
| `src/assets/dashboard-design.css` | 增加洞察编辑器令牌，映射 Element Plus 主题变量。 |
| `src/router/index.ts` | 正式编辑器按路由动态加载；原型路由仅开发环境注册。 |
| `src/views/insight/components/__tests__/*.spec.ts` | 扩展属性面板、来源选择、弹窗、画布和可访问性回归。 |
| `package.json` | 增加真实 9222 CDP 验收命令。 |

### 后端修改和测试文件

| 文件 | 本轮职责 |
| --- | --- |
| `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTOTest.java` | 锁定统一属性侧栏回写后 `config.datasetPipeline`、组合卡片和旧字段的无损 round-trip。 |
| `mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/DatasetComposerControllerTest.java` | 锁定草稿预览、确认、参数错误、403/503 和执行摘要契约。 |
| `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DashboardExecutionServiceTest.java` | 锁定组件级执行状态、日志、结果、取消和旧 Schema 兼容。 |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DatasetComposerController.java` | 仅在测试发现响应字段或状态码不稳定时做最小修正。 |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java` | 仅在 round-trip 测试发现字段丢失时补兼容字段，不承载 UI 状态。 |

### 固定前端草稿接口

```ts
export interface ComponentPropertyDraft {
  componentId: string
  title: string
  type: InsightComponent['type']
  component: InsightComponent
  datasetPipeline: ComponentDatasetPipeline
  dirty: boolean
  validationErrors: Record<string, string>
}

export interface ComponentPropertyDraftController {
  draft: Readonly<Ref<ComponentPropertyDraft | null>>
  load(component: InsightComponent | null): void
  patch(patch: Partial<InsightComponent>): void
  patchPipeline(patch: Partial<ComponentDatasetPipeline>): void
  validate(): boolean
  commit(): InsightComponent | null
  reset(): void
}
```

约束：`load()` 必须创建深拷贝；`commit()` 是向编辑器回写组件的唯一出口；关闭弹窗、取消编辑或切换组件不得隐式提交未确认的弹窗草稿。

## 2.1 开发与测试前置条件

| 条件 | 检查命令 | 通过标准 |
| --- | --- | --- |
| Docker Desktop | `docker info` | Server 可访问，磁盘空间足以启动模拟栈 |
| 本地模拟环境 | `dev-support/local-simulation/scripts/start.sh` | MySQL、PostgreSQL、MinIO、WireMock、Python Runner 健康 |
| 模拟环境检查 | `dev-support/local-simulation/scripts/check.sh` | JDBC `orders`、对象文件、HTTP 模拟和 Runner 检查通过 |
| Node 依赖 | `npm --prefix mateclaw-dataagent-ui ls --depth=0` | 无缺失依赖 |
| UI | `curl --fail http://127.0.0.1:15174/` | 返回 HTML；端口按实际 E2E 配置记录 |
| DataAgent | `curl --fail http://127.0.0.1:18089/dataagent/api/actuator/health` | `status=UP`；若模拟栈映射其他端口，记录实际地址 |
| Chrome CDP | `curl --fail --silent http://127.0.0.1:9222/json/version` | 返回 Google Chrome 与 WebSocket endpoint |
| E2E state | `test -s /tmp/mateclaw-dashboard-e2e-state.json` | 包含动态 JWT、workspaceId 和各测试 Dashboard ID；凭据不写入 Git |

启动模拟环境前从示例环境文件加载变量：

```bash
set -a
source dev-support/local-simulation/.env.aloudata-simulation.example
set +a
dev-support/local-simulation/scripts/start.sh
dev-support/local-simulation/scripts/check.sh
```

停止环境属于执行者主动操作，仅在确认没有其他任务复用该模拟栈后运行 `dev-support/local-simulation/scripts/cleanup.sh`；计划执行过程中不得为了“恢复干净状态”删除用户已有容器或卷。

## 2.2 固定测试数据

| 数据编号 | 内容 | 用途 | 关键断言 |
| --- | --- | --- | --- |
| DATA-01 | MySQL `orders`，至少包含日期、类型、地区、金额和订单 ID | JDBC SQL、筛选下推、长字段映射 | 预览行数受 limit 限制，筛选条件进入下推报告 |
| DATA-02 | PostgreSQL `orders` | 第二 JDBC 来源和跨源 Python | 与 DATA-01 使用不同金额，便于识别来源 |
| DATA-03 | 模拟 Aloudata 指标视图 | 指标视图流程 | 返回固定指标、维度和可识别数值 `120.5` |
| DATA-04 | 模拟 Aloudata 指标&维度 | 指标/维度选择和筛选 | 选择项与返回 Schema 一致 |
| DATA-05 | WireMock GET/POST API | Host、Path、Header、参数和 403/503/超时 | 错误状态稳定，非法参数不透传 |
| DATA-06 | `orders.csv/json/parquet/xlsx` 与补充 TXT | 五类文件入口 | 文件名、格式、字段、行数一致 |
| DATA-07 | 20+ 字段、80 字符数据源名、长错误文本 fixture | 布局和溢出 | 弹窗、侧栏、表格不突破视口 |
| DATA-08 | 旧版 Dashboard Schema fixture | 向后兼容 | 打开、保存、刷新后旧字段不丢失 |
| DATA-09 | KPI、图表、表格、筛选、时间筛选、组合卡片各一个 | 组件切换和状态隔离 | 切换后侧栏内容与组件类型一致 |
| DATA-10 | JDBC + 模拟 Aloudata 双输入 Dashboard | 端到端业务回归 | 最终结果 5 行并包含 `120.5`，沿用现有严格断言 |

所有动态 ID 由 seed 脚本写入 state 文件；测试代码按名称和 state 获取资源，不硬编码数据库主键。测试工程师在验收记录中写入 fixture 版本、seed 时间和 state 文件路径，但不得复制 JWT、认证值或连接密码。

---

### Task 1: 固化 UX 基线、令牌和自动化失败样例

**Files:**
- Modify: `mateclaw-dataagent-ui/src/assets/dashboard-design.css`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/PropertySection.vue`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/InlineHelp.vue`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/EditorEmptyState.vue`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/__tests__/PropertySection.spec.ts`

**Interfaces:**
- Consumes: Element Plus Tooltip/Popover、现有 `--db-*` 变量和 `prefers-reduced-motion`。
- Produces: `PropertySection`、`InlineHelp`、`EditorEmptyState` 三个统一基础组件和 `--insight-*` 视觉令牌。

- [ ] **Step 1: 写基础组件失败测试**

```ts
it('keeps help collapsed until hover focus or click and exposes an accessible name', async () => {
  const wrapper = mount(PropertySection, {
    props: { title: '数据集配置', help: '选择已有数据集或创建新数据集' },
  })
  expect(wrapper.text()).not.toContain('选择已有数据集或创建新数据集')
  const help = wrapper.get('[aria-label="数据集配置说明"]')
  await help.trigger('focus')
  expect(document.body.textContent).toContain('选择已有数据集或创建新数据集')
})

it('renders exactly one empty-state primary action when actionLabel is present', () => {
  const wrapper = mount(EditorEmptyState, { props: { description: '暂未配置数据集', actionLabel: '添加数据集' } })
  expect(wrapper.findAll('button')).toHaveLength(1)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/property/__tests__/PropertySection.spec.ts`

Expected: FAIL，原因是三个组件和洞察局部令牌尚未创建。

- [ ] **Step 3: 实现局部令牌和基础组件**

```css
.insight-editor-view {
  --insight-control-height: 32px;
  --insight-sidebar-width: 360px;
  --insight-radius-control: 6px;
  --insight-radius-card: 8px;
  --insight-space-section: 16px;
  --insight-space-field: 8px;
  --insight-motion-fast: 160ms;
}

@media (prefers-reduced-motion: reduce) {
  .insight-editor-view *,
  .insight-editor-view *::before,
  .insight-editor-view *::after {
    animation-duration: 1ms !important;
    transition-duration: 1ms !important;
  }
}
```

`InlineHelp` 使用原生按钮承载 Info SVG，支持 hover、focus、Enter、Space 和移动端点击；帮助内容关闭后焦点返回触发按钮。`PropertySection` 不把帮助正文渲染为常驻段落。

- [ ] **Step 4: 运行定向测试和样式检查**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/property/__tests__/PropertySection.spec.ts`

Expected: PASS；帮助触发按钮有可访问名称，空状态最多一个主操作。

- [ ] **Step 5: 提交**

```bash
git add -- mateclaw-dataagent-ui/src/assets/dashboard-design.css \
  mateclaw-dataagent-ui/src/views/insight/components/property
git commit -m "feat(insight): 统一属性配置基础视觉组件"
```

### Task 2: 统一属性配置草稿状态和回写出口

**Files:**
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/useComponentPropertyDraft.ts`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/property/__tests__/useComponentPropertyDraft.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/useCardAttributeBridge.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/CardAttributeSidebar.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`

**Interfaces:**
- Consumes: `InsightComponent`、`ComponentDatasetPipeline`、`readComponentDatasetPipeline()`、`writeComponentDatasetPipeline()`。
- Produces: `useComponentPropertyDraft()`；编辑器仅监听其 `commit()` 结果并触发 `handleComponentChange()`。

- [ ] **Step 1: 写组件切换和无损回写失败测试**

```ts
it('does not leak dataset or dialog draft when switching components', () => {
  const controller = useComponentPropertyDraft()
  controller.load(kpiWithJdbcPipeline)
  controller.patchPipeline({ pythonUser: 'result = orders' })
  controller.load(tableWithoutPipeline)
  expect(controller.draft.value?.componentId).toBe('table-1')
  expect(controller.draft.value?.datasetPipeline.datasetInputs).toEqual([])
})

it('round-trips unknown component config fields while updating datasetPipeline', () => {
  const controller = useComponentPropertyDraft()
  controller.load(componentWithLegacyAndExtensionFields)
  controller.patch({ title: '新标题' })
  const result = controller.commit()!
  expect(result.config?.extensionField).toEqual({ keep: true })
  expect(result.config?.datasetPipeline).toEqual(expectedPipeline)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/property/__tests__/useComponentPropertyDraft.spec.ts`

Expected: FAIL，证明当前单例状态无法提供组件级隔离契约。

- [ ] **Step 3: 实现纯草稿控制器**

```ts
export function useComponentPropertyDraft(): ComponentPropertyDraftController {
  const draft = ref<ComponentPropertyDraft | null>(null)

  function load(component: InsightComponent | null): void {
    draft.value = component ? createDraft(structuredClone(component)) : null
  }

  function commit(): InsightComponent | null {
    if (!draft.value || !validateDraft(draft.value)) return null
    return writeComponentDatasetPipeline(
      structuredClone(draft.value.component),
      structuredClone(draft.value.datasetPipeline),
    )
  }

  return { draft: readonly(draft), load, patch, patchPipeline, validate, commit, reset }
}
```

`useCardAttributeBridge.ts` 保留纯转换，不再读写跨组件单例。编辑器选中组件变化时调用 `load()`；确认操作调用 `commit()`；弹窗内部草稿独立于组件草稿。

- [ ] **Step 4: 添加正式编辑器集成测试**

在 `InsightDashboardEditorView` 组件测试中覆盖：KPI → 表格 → KPI 切换、未确认弹窗取消、标题修改、保存后刷新和旧 Schema 读取。

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/property/__tests__/useComponentPropertyDraft.spec.ts src/views/insight/components/card-attribute/__tests__/result-set.spec.ts`

Expected: PASS；切换组件无状态串扰，未知配置字段保留。

- [ ] **Step 5: 提交**

```bash
git add -- mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue \
  mateclaw-dataagent-ui/src/views/insight/components/card-attribute/CardAttributeSidebar.vue \
  mateclaw-dataagent-ui/src/views/insight/components/card-attribute/useCardAttributeBridge.ts \
  mateclaw-dataagent-ui/src/views/insight/components/property
git commit -m "refactor(insight): 统一属性配置草稿状态"
```

### Task 3: 收敛属性侧栏信息层级和帮助提示

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/AttributePanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/PropertyPanel.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`

**Interfaces:**
- Consumes: Task 1 的 `PropertySection`、`InlineHelp`、`EditorEmptyState`，Task 2 的统一草稿。
- Produces: 固定顺序、低噪声、统一状态反馈的属性侧栏。

- [ ] **Step 1: 写文案可见性和顺序失败测试**

```ts
it('shows KPI sections in the specified order without persistent explanatory paragraphs', () => {
  const wrapper = mountKpiPanel()
  const text = wrapper.text()
  expect(sectionTitles(wrapper)).toEqual([
    '组件标题', '多指标模式', '多TAB模式', '数据集配置',
    '筛选器绑定', 'Python 预处理', '结果集', '指标配置',
  ])
  expect(text).not.toContain('当前面板配置的是')
  expect(text).not.toContain('不显示「多指标模式 / 多TAB模式」')
})

it('keeps errors visible but hides explanatory help until requested', async () => {
  const wrapper = mountPanelWithInvalidAlias()
  expect(wrapper.get('[role="alert"]').text()).toContain('别名')
  expect(wrapper.text()).not.toContain('参数使用绑定方式')
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/PropertyPanel.spec.ts src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`

Expected: FAIL，当前常驻说明、非 KPI 提示和两套结构仍存在。

- [ ] **Step 3: 按信息分级改造侧栏**

具体规则：

- 删除“当前为某组件，不显示某能力”类占位说明。
- 删除“当前目标组件”“结果绑定组件”选择器；当前选中卡片就是唯一结果接收目标。
- 删除属性面板顶部独立“搜索”按钮；搜索只保留在“添加数据集”来源树内部。
- “SQL 只读”“筛选下推”“Python 系统区域”等迁入 `InlineHelp`。
- 保留结果集状态、执行中、失败、过期、字段缺失等状态信息。
- 0 个数据集时空状态内直接提供“添加数据集”；标题栏不重复显示同一按钮。
- 数据集不为空时只在列表尾部显示一个“添加数据集”。
- 每个区域只保留一个主按钮；低频操作进入 `el-dropdown` 的“更多操作”。
- 普通标题、Tab 名称和开关变更不触发 Toast。

- [ ] **Step 4: 清理正式页面 Emoji 和纯符号操作**

用 `Search`、`InfoFilled`、`Setting`、`Delete`、`Close`、`Picture` 等项目图标替换 `🔍`、`⚙️`、`🎨`、`✕`；删除按钮保留动态 `aria-label` 和 Tooltip。

Run: `rg -n "[🎨⚙️🔍📊📈]" mateclaw-dataagent-ui/src/views/insight --glob '*.vue'`

Expected: 正式编辑器依赖的组件无匹配；仅开发原型若仍保留匹配，必须受 Task 8 的开发路由限制。

- [ ] **Step 5: 运行定向测试并提交**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/PropertyPanel.spec.ts src/views/insight/components/__tests__/DatasetInputPanel.spec.ts`

Expected: PASS；错误就地显示、帮助默认隐藏、区块顺序符合原型。

```bash
git add -- mateclaw-dataagent-ui/src/views/insight/components/card-attribute/AttributePanel.vue \
  mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue \
  mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue \
  mateclaw-dataagent-ui/src/views/insight/components/__tests__
git commit -m "refactor(insight): 收敛属性侧栏信息层级"
```

### Task 4: 重整数据集来源选择与配置流程

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetSourcePicker.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DatasetSourceDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/DataSourceTreeDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/dataset/JdbcSqlDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/dataset/AloudataDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/dataset/ApiConfigDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/dataset/FileConfigDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetSourcePicker.spec.ts`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DatasetSourceDialog.spec.ts`

**Interfaces:**
- Consumes: `DatasetSourceSelection`、`datasetApi.previewDraft()`、`confirmDraft()`、`uploadFile()`、`registerApiDefinition()`。
- Produces: 单层“选来源 → 配置 → 预览 → 确认”流程；确认事件仍返回已有 `datasetId/sourceConfig/displayName` 契约。

- [ ] **Step 1: 写来源分组和弹窗动作失败测试**

```ts
it('separates reusable datasets from create-from-source actions', () => {
  const wrapper = mountPicker({ datasets: [ordersDataset], datasources: [mysqlDatasource] })
  expect(groupItems(wrapper, '已有数据集')).toContain('orders')
  expect(groupItems(wrapper, 'JDBC')).toContain('mysql-prod')
  expect(groupItems(wrapper, '接口')).toEqual(['接口'])
})

it('keeps preview inside the source dialog and exposes one primary confirm action', async () => {
  const wrapper = mountSourceDialog({ selection: jdbcSelection })
  expect(wrapper.findAll('.el-dialog')).toHaveLength(1)
  expect(primaryButtons(wrapper).map(button => button.text())).toEqual(['确认添加'])
  await wrapper.get('[aria-label="筛选预览"]').trigger('click')
  expect(wrapper.get('[role="tabpanel"][aria-label="预览结果"]').exists()).toBe(true)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/DatasetSourcePicker.spec.ts src/views/insight/components/__tests__/DatasetSourceDialog.spec.ts`

Expected: FAIL，当前已有数据集与原始数据源混排，预览和执行记录会打开叠加弹窗。

- [ ] **Step 3: 重构来源选择器**

来源结构固定为：

```ts
type SourcePickerGroupId = 'existing' | 'aloudata' | 'jdbc' | 'api' | 'file'

const groupOrder: SourcePickerGroupId[] = ['existing', 'aloudata', 'jdbc', 'api', 'file']
```

- “已有数据集”显示可复用数据集，点击后直接加入或进入只读摘要。
- JDBC 分组只显示 JDBC 连接，点击后进入 SQL 配置。
- Aloudata 下只显示“指标视图”“指标&维度”。
- 接口只有一个入口，点击后进入配置弹窗。
- 文件显示 Excel、CSV、TXT、JSON、Parquet。
- 搜索输入只存在于来源树内部；无结果时显示清空搜索按钮，不显示空白区域。

- [ ] **Step 4: 合并配置和预览，不再叠加 Dialog**

`DatasetSourceDialog` 内部使用两个视图状态：`config` 与 `preview`。底部动作固定为“取消 / 筛选预览 / 确认添加”；执行记录移入预览页的次级链接或抽屉。错误绑定到具体字段，API JSON 解析错误不能静默转换为空对象。

```ts
type SourceDialogView = 'config' | 'preview'
const activeView = ref<SourceDialogView>('config')
const fieldErrors = reactive<Record<string, string>>({})
```

- [ ] **Step 5: 覆盖异常和取消语义**

新增测试覆盖：SQL 为空、非只读 SQL、Aloudata 未选择视图、API Host/Path 为空、Header JSON 非法、文件未选择、预览 403/503、取消后不发出 `confirm`、预览后返回配置仍保留草稿。

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/DatasetSourcePicker.spec.ts src/views/insight/components/__tests__/DatasetSourceDialog.spec.ts`

Expected: PASS；错误就地显示且草稿不丢失。

- [ ] **Step 6: 提交**

```bash
git add -- mateclaw-dataagent-ui/src/views/insight/components/DatasetSourcePicker.vue \
  mateclaw-dataagent-ui/src/views/insight/components/DatasetSourceDialog.vue \
  mateclaw-dataagent-ui/src/views/insight/components/card-attribute/DataSourceTreeDialog.vue \
  mateclaw-dataagent-ui/src/views/insight/components/card-attribute/dataset \
  mateclaw-dataagent-ui/src/views/insight/components/__tests__
git commit -m "refactor(insight): 统一数据集来源配置流程"
```

### Task 5: 统一弹窗、表单、按钮和反馈样式

**Files:**
- Modify: `mateclaw-dataagent-ui/src/assets/dashboard-design.css`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/FieldMappingDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/FilterBindingDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/PythonScriptDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/PreviewDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/MetricConfigDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/MetricStyleDialog.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/__tests__/FieldMappingDialog.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/__tests__/MetricConfigDialog.spec.ts`

**Interfaces:**
- Consumes: Task 1 的令牌和帮助组件、Task 2 的草稿提交语义。
- Produces: `.insight-dialog--sm/md/lg/preview`、统一 footer、字段错误和异步状态规则。

- [ ] **Step 1: 写统一尺寸和按钮层级失败测试**

```ts
it.each([
  ['字段名称', 'insight-dialog--lg'],
  ['绑定筛选器', 'insight-dialog--lg'],
  ['编辑 Python 脚本', 'insight-dialog--preview'],
])('uses the shared dialog size class for %s', (title, className) => {
  const wrapper = mountDialogByTitle(title)
  expect(wrapper.get('.el-dialog').classes()).toContain(className)
  expect(primaryButtons(wrapper)).toHaveLength(1)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/card-attribute/__tests__/FieldMappingDialog.spec.ts src/views/insight/components/card-attribute/__tests__/MetricConfigDialog.spec.ts`

Expected: FAIL，当前各弹窗使用不同硬编码宽度和按钮规则。

- [ ] **Step 3: 实现弹窗和表单统一规则**

- 所有弹窗设置 `destroy-on-close`、`:close-on-click-modal="false"` 和明确 `aria-label`。
- Footer 顺序固定为取消在左侧操作组，主操作在最右；危险操作不与主操作同色。
- 表单标签使用 12px、正文 13px、控件高 32px；中文标签取消 uppercase。
- SQL、Python、字段列表使用纵向布局；短输入使用横向布局。
- 长字段名省略但可 Tooltip 查看全文；错误信息允许换行。
- 保存、预览、执行按钮防重复点击；完成后恢复焦点。
- 只有保存/上传/执行成功使用 Toast，字段校验不使用 Toast。

- [ ] **Step 4: 添加反馈去重测试**

```ts
it('shows field validation inline without emitting a global toast', async () => {
  const wrapper = mountJdbcDialog()
  await wrapper.get('[aria-label="确认添加"]').trigger('click')
  expect(wrapper.get('[role="alert"]').text()).toContain('请输入 SQL')
  expect(ElMessage.warning).not.toHaveBeenCalled()
})
```

- [ ] **Step 5: 运行测试和提交**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/card-attribute/__tests__ src/views/insight/components/__tests__/DatasetSourceDialog.spec.ts`

Expected: PASS；一个作用域仅一个主按钮，错误无重复提示。

```bash
git add -- mateclaw-dataagent-ui/src/assets/dashboard-design.css \
  mateclaw-dataagent-ui/src/views/insight/components/card-attribute
git commit -m "style(insight): 统一属性弹窗表单与反馈"
```

### Task 6: 改善画布可发现性、键盘操作和响应式布局

**Files:**
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/DashboardCanvas.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/ComponentPalette.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`
- Create: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/DashboardCanvas.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/ComponentPalette.spec.ts`

**Interfaces:**
- Consumes: 现有 `update-layout`、`select-component`、`delete-component` 事件。
- Produces: `nudge-component` 和 `resize-component` 键盘行为；移动端面板抽屉和无横向页面溢出布局。

- [ ] **Step 1: 写画布键盘和空状态失败测试**

```ts
it('moves the selected component with arrow keys and resizes with shift plus arrows', async () => {
  const wrapper = mountCanvas({ selectedId: 'kpi-1', editable: true })
  await wrapper.get('[data-component-id="kpi-1"]').trigger('keydown', { key: 'ArrowRight' })
  expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toMatchObject({ id: 'kpi-1', x: 1 })
  await wrapper.get('[data-component-id="kpi-1"]').trigger('keydown', { key: 'ArrowDown', shiftKey: true })
  expect(wrapper.emitted('update-layout')?.at(-1)?.[0]).toMatchObject({ id: 'kpi-1', h: 5 })
})

it('offers one add-component action in an empty canvas', () => {
  const wrapper = mountCanvas({ components: [] })
  expect(wrapper.get('[aria-label="从组件库添加组件"]').exists()).toBe(true)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/DashboardCanvas.spec.ts src/views/insight/components/__tests__/ComponentPalette.spec.ts`

Expected: FAIL，当前缩放依赖鼠标热区，空状态无直接动作。

- [ ] **Step 3: 实现可发现性与键盘替代**

- 选中组件时显示四角轻量缩放标记，未选中时不制造视觉噪声。
- 卡片根节点支持 focus；方向键移动，Shift+方向键调整尺寸，Delete 打开确认。
- 拖拽物料继续支持 Enter/Space 添加。
- 删除、设置、复制按钮使用统一 Tooltip 和 32px 热区。
- 空画布提供“从组件库添加组件”，点击后展开并聚焦组件库第一个物料。
- `prefers-reduced-motion` 下禁用入场动画。

- [ ] **Step 4: 实现响应式规则**

```css
@media (max-width: 1279px) {
  .editor-pages,
  .editor-palette,
  .editor-right-sidebar { position: fixed; z-index: 30; }
  .editor-canvas { min-width: 0; }
}

@media (max-width: 767px) {
  .editor-toolbar { min-height: 48px; }
  .editor-right-sidebar { width: min(92vw, 420px); }
  .insight-dialog { width: calc(100vw - 24px) !important; }
}
```

确保 1024px 以下侧栏以抽屉呈现；画布不被永久挤压，页面根节点 `scrollWidth <= clientWidth`。

- [ ] **Step 5: 运行测试和提交**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/views/insight/components/__tests__/DashboardCanvas.spec.ts src/views/insight/components/__tests__/ComponentPalette.spec.ts`

Expected: PASS；键盘操作和空状态动作可用。

```bash
git add -- mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue \
  mateclaw-dataagent-ui/src/views/insight/components/DashboardCanvas.vue \
  mateclaw-dataagent-ui/src/views/insight/components/ComponentPalette.vue \
  mateclaw-dataagent-ui/src/views/insight/components/__tests__
git commit -m "feat(insight): 完善画布键盘与响应式体验"
```

### Task 7: 补强后端 Schema、数据集草稿和执行契约回归

**Files:**
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTOTest.java`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/DatasetComposerControllerTest.java`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DashboardExecutionServiceTest.java`
- Conditional Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java`
- Conditional Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DatasetComposerController.java`
- Conditional Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DashboardExecutionServiceImpl.java`

**Interfaces:**
- Consumes: 已有 `/v1/dataset-composer/drafts/preview`、`/drafts/confirm`、Dashboard update、组件执行/状态/日志/结果/取消端点。
- Produces: 不随前端重构漂移的 JSON 和 HTTP 状态契约；不新增 UI 专属字段。

- [ ] **Step 1: 写 Dashboard Schema round-trip 测试**

```java
@Test
void preservesComponentDatasetPipelineAndUnknownConfigFieldsAcrossRoundTrip() throws Exception {
    String json = fixture("dashboard-component-pipeline.json");
    InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);
    String serialized = mapper.writeValueAsString(schema);
    JsonNode root = mapper.readTree(serialized);
    JsonNode config = root.at("/pages/0/components/0/config");
    assertThat(config.at("/datasetPipeline/datasetInputs/0/inputName").asText()).isEqualTo("orders");
    assertThat(config.at("/extensionField/keep").asBoolean()).isTrue();
}
```

- [ ] **Step 2: 写数据集草稿错误契约测试**

```java
@Test
void previewReturnsStableErrorWithoutPersistingDraftWhenDatasourceUnavailable() {
    when(executionService.previewDraft(any())).thenThrow(new DatasetReadException(
        DatasetReadErrorCode.SOURCE_UNAVAILABLE, "数据源暂不可用"));
    assertThatThrownBy(() -> controller.preview(request()))
        .isInstanceOf(DatasetReadException.class)
        .hasMessageContaining("数据源暂不可用");
    verify(datasetManageService, never()).create(any());
}
```

同时覆盖 400 非法参数、403 不可见数据源、503 上游不可达、确认成功返回 `datasetId + descriptor`，并断言响应中没有连接凭据。

- [ ] **Step 3: 写组件执行生命周期测试**

覆盖：创建组件执行、RUNNING、SUCCEEDED、FAILED、CANCELLED、日志读取、内联结果、ObjectRef 结果以及未知 executionId；断言组件 ID 和 Dashboard ID 不能串任务。

- [ ] **Step 4: 运行测试并只做最小生产修正**

Run:

```bash
make dashboard-dataagent-test
```

Expected: 新测试先暴露契约问题；生产代码仅修复失败契约，不增加 UI 偏好字段。最终 DataAgent 全量测试 0 failures、0 errors。

- [ ] **Step 5: 提交**

```bash
git add -- mateclaw-dataagent/src/test/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTOTest.java \
  mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/DatasetComposerControllerTest.java \
  mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DashboardExecutionServiceTest.java \
  mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java \
  mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DatasetComposerController.java \
  mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DashboardExecutionServiceImpl.java
git commit -m "test(dataagent): 固化仪表盘编辑器后端契约"
```

若三个生产文件没有变化，只暂存测试文件。

### Task 8: 收敛正式路由、原型隔离和资源加载

**Files:**
- Modify: `mateclaw-dataagent-ui/src/router/index.ts`
- Modify: `mateclaw-dataagent-ui/src/router/index.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/DashboardListView.vue`

**Interfaces:**
- Consumes: 正式路由 `/insight/dashboard/editor` 和 `/insight/dashboard/editor-preview`。
- Produces: 动态加载的唯一正式编辑器；原型路由仅 `import.meta.env.DEV` 下注册。

- [ ] **Step 1: 写路由失败测试**

```ts
it('uses one lazy-loaded editor and excludes prototype routes in production', () => {
  const editor = router.getRoutes().find(route => route.name === 'insight-dashboard-editor')
  expect(typeof editor?.components?.default).toBe('function')
  expect(router.hasRoute('insight-card-container-prototype')).toBe(import.meta.env.DEV)
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix mateclaw-dataagent-ui run test -- --run src/router/index.spec.ts`

Expected: FAIL，当前正式编辑器存在静态 import，原型路由未按环境隔离。

- [ ] **Step 3: 改为统一动态路由**

```ts
const InsightDashboardEditorView = () => import('@/views/insight/InsightDashboardEditorView.vue')

const developmentRoutes = import.meta.env.DEV
  ? [cardContainerPrototypeRoute, kpiMetricGroupPrototypeRoute]
  : []
```

列表页编辑、预览按钮都跳转到正式路由，不再直接内嵌另一套属性编辑器。

- [ ] **Step 4: 验证构建产物**

Run: `npm --prefix mateclaw-dataagent-ui run build`

Expected: 构建成功；编辑器成为独立 chunk；不得新增超过当前基线的主入口 chunk。Element Plus、ECharts 等既有大包警告单独记录，不伪装成已解决。

- [ ] **Step 5: 提交**

```bash
git add -- mateclaw-dataagent-ui/src/router/index.ts \
  mateclaw-dataagent-ui/src/router/index.spec.ts \
  mateclaw-dataagent-ui/src/views/insight/DashboardListView.vue
git commit -m "refactor(insight): 收敛正式编辑器入口"
```

### Task 9: 自动化 E2E、主题、响应式和无障碍门禁

**Files:**
- Create: `mateclaw-dataagent-ui/e2e/dashboard-editor-ux.spec.ts`
- Modify: `mateclaw-dataagent-ui/playwright.config.ts`
- Modify: `mateclaw-dataagent-ui/e2e/dashboard-multi-source.spec.ts`
- Modify: `mateclaw-dataagent-ui/e2e/dashboard-errors-and-compatibility.spec.ts`

**Interfaces:**
- Consumes: 本地模拟环境动态 state 文件、JWT、Dashboard ID、Chrome channel。
- Produces: 不依赖固定数据库 ID 的 UX 回归套件和失败截图/trace/video。

- [ ] **Step 1: 写正式主流程 E2E**

```ts
test('card property flow keeps help quiet and preserves configured dataset after save', async ({ page }) => {
  await openSeededDashboard(page, state.dashboardId)
  await selectCanvasComponent(page, 'KPI/指标卡')
  await expect(page.getByText('选择已有数据集或创建新数据集')).toBeHidden()
  await page.getByRole('button', { name: '添加数据集' }).click()
  await page.getByRole('treeitem', { name: /JDBC/ }).click()
  await configureAndPreviewJdbc(page)
  await page.getByRole('button', { name: '确认添加' }).click()
  await saveAndReload(page)
  await expect(page.getByText('orders')).toBeVisible()
})
```

- [ ] **Step 2: 增加响应式和主题测试**

逐一设置 375×812、768×1024、1024×768、1280×800、1440×1000；每个尺寸断言：

```ts
expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
```

四主题断言正文/背景对比度不低于 4.5:1，交互边界不低于 3:1；焦点轮廓可见。

- [ ] **Step 3: 增加键盘和无障碍测试**

- Tab 可到达页面树、组件库、画布卡片、属性面板和弹窗。
- Enter/Space 可展开帮助、选择来源和添加组件。
- 方向键可切换 Tab；方向键/Shift+方向键可移动或缩放画布组件。
- Esc 关闭最上层弹窗并恢复触发点焦点。
- 可见按钮、textbox、combobox、switch、tab 均有非空可访问名称。
- 错误使用 `role=alert`，状态使用 `aria-live` 或等价语义。

- [ ] **Step 4: 增加异常场景测试**

复用真实模拟服务返回 403、503、超时、空结果、脚本失败和取消；断言配置仍存在、错误就地显示、无重复 Toast、可重试。

- [ ] **Step 5: 运行 E2E**

Run:

```bash
MATECLAW_E2E_ALOUDATA_MODE=simulation \
MATECLAW_E2E_BROWSER_CHANNEL=chrome \
npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line
```

Expected: 全部通过，无 skipped、focused 或 flaky retry；双源结果仍为既有严格业务结果，视觉容差不得掩盖布局错误。

- [ ] **Step 6: 提交**

```bash
git add -- mateclaw-dataagent-ui/e2e/dashboard-editor-ux.spec.ts \
  mateclaw-dataagent-ui/e2e/dashboard-multi-source.spec.ts \
  mateclaw-dataagent-ui/e2e/dashboard-errors-and-compatibility.spec.ts \
  mateclaw-dataagent-ui/playwright.config.ts
git commit -m "test(insight): 补齐编辑器体验回归矩阵"
```

### Task 10: Google Chrome 9222 CDP 视觉验收与证据固化

**Files:**
- Create: `mateclaw-dataagent-ui/e2e/cdp-dashboard-editor-ux-check.mjs`
- Modify: `mateclaw-dataagent-ui/package.json`
- Create: `docs/superpowers/evidence/dashboard-editor-ux-acceptance.md`

**Interfaces:**
- Consumes: 用户启动的 Google Chrome CDP endpoint `http://127.0.0.1:9222`、同一轮 E2E state 文件、JWT、workspaceId 和 Dashboard ID。
- Produces: PNG 截图、AX 摘要、DOM 尺寸、焦点链、控制台错误、失败请求、候选 SHA 和验收报告。

- [ ] **Step 1: 编写只连接 9222 的脚本**

```js
import { chromium } from '@playwright/test'

const endpoint = process.env.MATECLAW_CDP_ENDPOINT ?? 'http://127.0.0.1:9222'
const browser = await chromium.connectOverCDP(endpoint)
const context = browser.contexts()[0]
if (!context) throw new Error('9222 上没有可用 Google Chrome context')
const page = context.pages()[0] ?? await context.newPage()
```

脚本不得调用 `chromium.launch()`，不得新建独立浏览器配置目录，不得把 Token 写入证据文件。脚本结束时只关闭自身创建的 Page，不关闭用户 Chrome。

- [ ] **Step 2: 固定证据结构**

每个场景写入：

```json
{
  "candidateSha": "git rev-parse HEAD",
  "url": "当前页面 URL",
  "viewport": { "width": 1440, "height": 1000 },
  "browser": "Google Chrome via CDP 9222",
  "timestamp": "ISO-8601",
  "actions": ["选择 KPI 卡", "添加数据集", "打开 JDBC", "筛选预览"],
  "screenshotPath": "绝对路径",
  "axUnnamedInteractiveCount": 0,
  "consoleErrors": [],
  "failedRequests": []
}
```

- [ ] **Step 3: 固定截图场景**

至少采集以下截图，保存在 `/tmp/mateclaw-dashboard-editor-ux-<candidateSha>/`：

1. `01-editor-default-1440.png`：四栏正式编辑器。
2. `02-kpi-property-clean.png`：KPI 属性侧栏，无常驻大段提示。
3. `03-help-tooltip.png`：键盘聚焦 Info 后提示可见。
4. `04-source-picker.png`：已有数据集与四类来源清晰分组。
5. `05-jdbc-config.png`：JDBC SQL 配置。
6. `06-jdbc-preview.png`：同一弹窗中的筛选预览。
7. `07-aloudata-config.png`：指标视图或指标&维度配置。
8. `08-python-and-result.png`：Python 与结果集状态。
9. `09-dialog-long-content.png`：长字段/长错误不溢出。
10. `10-editor-1024.png`：侧栏抽屉态。
11. `11-editor-375.png`：移动端面板态。
12. `12-dark-theme.png`：暗色主题。
13. `13-keyboard-focus.png`：画布选中与可见焦点。
14. `14-error-state.png`：503 或脚本失败就地错误。

- [ ] **Step 4: 增加命令入口**

```json
{
  "scripts": {
    "test:e2e:cdp:editor-ux": "node e2e/cdp-dashboard-editor-ux-check.mjs"
  }
}
```

- [ ] **Step 5: 启动并检查用户 Google Chrome**

用户未启动 9222 时，执行者使用以下命令启动独立测试 Profile：

```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/mateclaw-cdp-dashboard-editor-ux \
  --no-first-run \
  --no-default-browser-check
curl --fail --silent http://127.0.0.1:9222/json/version
```

Expected: `/json/version` 返回 `Google Chrome` 版本和 `webSocketDebuggerUrl`。不得连接 Safari、Chromium 或 Playwright 自启浏览器冒充此门禁。

- [ ] **Step 6: 执行视觉验收**

Run:

```bash
MATECLAW_CDP_ENDPOINT=http://127.0.0.1:9222 \
MATECLAW_UI_BASE_URL=http://127.0.0.1:15174 \
MATECLAW_E2E_STATE_FILE=/tmp/mateclaw-dashboard-e2e-state.json \
MATECLAW_CDP_SCREENSHOT_DIR=/tmp/mateclaw-dashboard-editor-ux-$(git rev-parse --short HEAD) \
npm --prefix mateclaw-dataagent-ui run test:e2e:cdp:editor-ux
```

Expected:

- 14 张截图和一份 JSON 摘要生成成功。
- 可见交互控件无空名称。
- 控制台无未处理 Error；业务预期 403/503 只记录为已覆盖场景。
- 没有意外 4xx/5xx、资源加载失败或布局横向溢出。
- Tooltip、弹窗、抽屉、焦点、滚动、主题和移动端状态均被真实操作触发，而非直接注入 DOM 状态。

- [ ] **Step 7: 编写验收记录并提交**

验收记录必须写明 PASS、FAIL、BLOCKED 或 NOT_RUN；不得把构建通过、Playwright 截图或旧截图写成 9222 PASS。

```bash
git add -- mateclaw-dataagent-ui/e2e/cdp-dashboard-editor-ux-check.mjs \
  mateclaw-dataagent-ui/package.json \
  docs/superpowers/evidence/dashboard-editor-ux-acceptance.md
git commit -m "test(insight): 固化编辑器CDP视觉验收"
```

### Task 11: 聚合门禁、工作树审查和交付

**Files:**
- Modify: `docs/superpowers/evidence/dashboard-editor-ux-acceptance.md`
- Modify: `docs/策略解读/原型设计.md`（仅当实现形成新的稳定视觉规则时补充，不改业务需求）

**Interfaces:**
- Consumes: Task 1～10 的提交、测试和视觉证据。
- Produces: 可追溯到同一候选 SHA 的发布判断。

- [ ] **Step 1: 执行前端单元测试和构建**

```bash
npm --prefix mateclaw-dataagent-ui run test -- --run
npm --prefix mateclaw-dataagent-ui run build
```

Expected: 所有测试通过；测试输出不得包含新增的 Vue 未解析组件、无效 DOM 属性或未处理 Promise 警告；构建成功。

- [ ] **Step 2: 执行后端和本地聚合门禁**

```bash
make dashboard-dataagent-test
make dashboard-verify-local
```

Expected: 退出码 0；DataAgent、Runner、UI 测试和构建全部通过。

- [ ] **Step 3: 执行真实 Chrome E2E 和 9222 CDP 验收**

先运行 Task 9 全量 Chrome channel E2E，再运行 Task 10 的 9222 脚本。两者必须基于同一候选 SHA 和同一轮 seed state。

- [ ] **Step 4: 检查代码与工作树**

```bash
git diff --check
git status --short
git diff --name-only HEAD~1..HEAD
rg -n "<<<<<<<|=======|>>>>>>>" mateclaw-dataagent-ui mateclaw-dataagent docs/plans
```

Expected: 无冲突标记和 whitespace error；未提交文件均能归属到本计划或明确标记为用户原有改动。

- [ ] **Step 5: 完成验收报告**

报告包含：候选 SHA、分支、服务版本、模拟环境、测试命令与数量、E2E 用例结果、9222 浏览器版本、viewport、截图路径、AX 扫描、控制台/网络摘要、已知限制、最终结论。

- [ ] **Step 6: 文档提交**

```bash
git add -- docs/superpowers/evidence/dashboard-editor-ux-acceptance.md docs/策略解读/原型设计.md
git commit -m "docs(insight): 记录编辑器体验验收结果"
```

若原型设计文档不需要变化，只提交验收记录。

---

## 3. 前端单元测试清单

| 编号 | 测试对象 | 场景 | 预期结果 |
| --- | --- | --- | --- |
| FE-UT-01 | `useComponentPropertyDraft` | KPI 切换到表格 | 不残留 KPI 数据集、结果集和弹窗草稿 |
| FE-UT-02 | `useComponentPropertyDraft` | 修改标题后提交 | 只更新当前组件，未知扩展字段保留 |
| FE-UT-03 | `useComponentPropertyDraft` | 取消弹窗 | 组件草稿不变化，不触发 `change` |
| FE-UT-04 | `AttributePanel` | KPI 卡 | 顺序严格符合原型 |
| FE-UT-05 | `AttributePanel` | 图表/表格 | 不显示 KPI 专属开关，也不显示解释占位文案 |
| FE-UT-06 | `PropertySection` | 默认帮助 | 帮助正文不常驻，Info 按钮可聚焦 |
| FE-UT-07 | `PropertySection` | 键盘打开帮助 | Enter/Space 打开，Esc 关闭并恢复焦点 |
| FE-UT-08 | `DatasetSourcePicker` | 来源分组 | 已有数据集、Aloudata、JDBC、接口、文件分组准确 |
| FE-UT-09 | `DatasetSourcePicker` | 搜索无结果 | 显示空状态和清空搜索动作 |
| FE-UT-10 | `DatasetSourceDialog` | JDBC 配置与预览 | 一个 Dialog 内完成，不出现叠加 Dialog |
| FE-UT-11 | `DatasetSourceDialog` | API JSON 非法 | 字段就地错误，不静默转换为空对象 |
| FE-UT-12 | `DatasetSourceDialog` | 403/503 | 配置保留，错误就地显示，可重试 |
| FE-UT-13 | `DatasetSourceDialog` | 确认中重复点击 | 只发送一次请求 |
| FE-UT-14 | `FieldMappingDialog` | 长字段名和重复目标名 | 不溢出，重复名阻止确认 |
| FE-UT-15 | `FilterBindingDialog` | 单选、多选、范围 | 序列化类型正确，错误关联字段 |
| FE-UT-16 | `PythonScriptDialog` | 重生成系统区域 | 用户区域保持不变 |
| FE-UT-17 | `PreviewDialog` | 空结果/大结果/ObjectRef | 状态、行数和受限提示正确 |
| FE-UT-18 | `DashboardCanvas` | 键盘移动/缩放 | 发出正确布局更新且阻止页面滚动 |
| FE-UT-19 | `DashboardCanvas` | 空画布 | 只有一个添加组件主操作 |
| FE-UT-20 | `router` | production build | 正式编辑器懒加载，原型路由不存在 |

## 4. 后端单元测试清单

| 编号 | 测试对象 | 场景 | 预期结果 |
| --- | --- | --- | --- |
| BE-UT-01 | `InsightDashboardSchemaDTOTest` | 新组件级 pipeline round-trip | 输入、筛选绑定、Python、结果配置无损 |
| BE-UT-02 | `InsightDashboardSchemaDTOTest` | 旧 `dataSource`/Tab Schema | 可读取、可保存，不被清空 |
| BE-UT-03 | `InsightDashboardSchemaDTOTest` | 未知 config 扩展字段 | round-trip 后仍存在 |
| BE-UT-04 | `DatasetComposerControllerTest` | JDBC 草稿预览 | 不落库，返回 rows/schema/executionId/pushdownReport |
| BE-UT-05 | `DatasetComposerControllerTest` | 草稿确认 | 返回 datasetId 和 descriptor，只创建一次 |
| BE-UT-06 | `DatasetComposerControllerTest` | 400 参数错误 | 稳定错误码和可读消息，无凭据泄露 |
| BE-UT-07 | `DatasetComposerControllerTest` | 403 数据源不可见 | 建立连接前拒绝 |
| BE-UT-08 | `DatasetComposerControllerTest` | 503 上游不可达 | 返回可识别不可用错误，草稿不落库 |
| BE-UT-09 | `DashboardExecutionServiceTest` | 组件级执行成功 | 状态、日志、结果和组件 ID 一致 |
| BE-UT-10 | `DashboardExecutionServiceTest` | 取消/超时/失败 | 状态稳定，可重试，不覆盖上次成功结果 |
| BE-UT-11 | `DashboardExecutionServiceTest` | ObjectRef 大结果 | 只返回受限预览和受控引用 |
| BE-UT-12 | `DashboardExecutionServiceTest` | 未知 executionId | 不读取其他任务数据 |

## 5. 测试工程师功能与体验用例

### 5.1 核心功能

| 编号 | 前置条件 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- |
| QA-01 | 已登录，有一个空 Dashboard | 进入洞察 → 编辑仪表盘 | 显示原页面树、组件库、画布和统一属性侧栏，无原型假卡片 |
| QA-02 | 空画布 | 点击空状态“从组件库添加组件” | 组件库展开并聚焦第一个组件 |
| QA-03 | 已有 KPI 卡 | 点击 KPI 卡 | 侧栏顺序符合固定顺序，显示多指标/多TAB |
| QA-04 | 已有图表卡 | 点击图表卡 | 不显示 KPI 专属开关，不显示解释占位文案 |
| QA-05 | KPI 侧栏 | 悬浮/聚焦数据集 Info | 帮助出现；移出/关闭后消失；布局不跳动 |
| QA-06 | KPI 侧栏无数据集 | 点击“添加数据集” | 来源按已有数据集、Aloudata、JDBC、接口、文件分组 |
| QA-07 | 有 JDBC 连接 | 选择 JDBC、输入 SQL、筛选预览 | 同一弹窗展示结果，无第二层 Dialog |
| QA-08 | 有 Aloudata 模拟连接 | 分别选择指标视图、指标&维度 | 字段和配置匹配所选模式，不出现 JDBC SQL |
| QA-09 | 本地 API 模拟服务 | 选择接口并填写 Host/Path/参数 | 预览成功，确认后形成可复用数据集 |
| QA-10 | 准备 CSV/XLSX/TXT/JSON/Parquet | 分别上传并预览 | 文件名、格式、字段和受限预览正确 |
| QA-11 | 已有两个数据集 | 添加第二个数据集 | Python 区显示且用户处理区域必填规则明确 |
| QA-12 | 已配置 Python | 重新生成系统区域 | 用户处理代码不丢失 |
| QA-13 | 已有筛选器 | 绑定到一个或多个输入 | 筛选范围清晰，执行时只作用于绑定输入 |
| QA-14 | 结果集已成功 | 修改数据集筛选 | 结果集显示“已过期”，卡片仍保留上一次数据 |
| QA-15 | 有未保存修改 | 保存并刷新页面 | 标题、数据集、筛选、Python、结果配置完整回显 |
| QA-16 | 旧 Dashboard fixture | 打开、修改标题、保存、刷新 | 旧数据源/Tab 配置不丢失，页面可继续使用 |
| QA-17 | 组合卡片 | 新增/删除/切换 Tab | 子组件位置和配置保持，侧栏无串状态 |

### 5.2 边界和异常

| 编号 | 输入或环境 | 操作 | 预期结果 |
| --- | --- | --- | --- |
| QA-18 | 80 字符数据源名 | 打开来源选择器 | 单行省略，Tooltip 可看全文，不撑宽弹窗 |
| QA-19 | 20+ 字段、长中文/英文名 | 打开字段映射 | 可滚动、表头固定、无水平页面溢出 |
| QA-20 | 8 个 Tab | 切换、重命名、删除 | 操作可见，当前 Tab 明确，键盘方向键可切换 |
| QA-21 | 1000 字错误消息 | 触发预览失败 | 错误换行或折叠，不把按钮挤出视口 |
| QA-22 | SQL 为空或非只读 | 点击预览/确认 | 对应字段下显示错误，不弹重复 Toast，不发请求 |
| QA-23 | API Header JSON 非法 | 点击预览 | 显示 JSON 解析错误，不作为空对象提交 |
| QA-24 | 模拟 403 | 预览受限数据源 | 显示无权限，配置不丢失，不能确认 |
| QA-25 | 模拟 503 | 预览上游数据源 | 显示暂不可用和重试，配置不丢失 |
| QA-26 | Runner 超时/取消 | 生成结果集 | 状态从运行中变为超时/已取消，可重试 |
| QA-27 | 数据为空 | 预览并生成结果集 | 明确“0 行”，不误报成功有数据 |
| QA-28 | 快速双击主按钮 | 确认数据集或保存 | 只有一次请求和一个结果 |
| QA-29 | 切换组件时弹窗未确认 | 切换到另一卡片再返回 | 未确认草稿不写入，已保存配置仍存在 |
| QA-30 | 网络恢复 | 503 后重试 | 成功后错误消失，保留原输入值 |

### 5.3 视觉、响应式和无障碍

| 编号 | 场景 | 预期结果 |
| --- | --- | --- |
| QA-31 | 1440×1000 | 四栏比例合理，画布为主要区域，侧栏约 360px |
| QA-32 | 1280×800 | 页面树/组件库可折叠，属性侧栏不覆盖主操作 |
| QA-33 | 1024×768 | 辅助面板以抽屉呈现，画布可操作，无页面横向滚动 |
| QA-34 | 768×1024 | 一次只展开一个辅助面板，遮罩和关闭行为正确 |
| QA-35 | 375×812 | 弹窗宽度为视口减 24px，Footer 按钮可见可点 |
| QA-36 | 四主题 | 控件、弹窗、空态、Alert、Tooltip 使用主题令牌，无固定浅色残留 |
| QA-37 | 键盘 Tab 遍历 | 所有可见控件可达、有名称、有可见焦点，无焦点陷阱 |
| QA-38 | 画布键盘操作 | 方向键移动，Shift+方向键缩放，不滚动页面 |
| QA-39 | Tooltip | hover、focus、点击均可打开；Esc 可关闭并恢复焦点 |
| QA-40 | 减少动态效果 | 系统开启 reduce motion 后无明显入场和拖拽过渡动画 |

## 6. 验收标准

### 功能门禁

- 正式入口只有一个编辑器实现，数据组件和控制/容器组件共享统一草稿与提交契约。
- 原型文档定义的属性顺序、数据集来源、筛选、Python、结果集和 KPI 指标配置全部可用。
- JDBC、Aloudata、接口、五类文件及双数据集 Python 流程不退化。
- 保存并刷新后 `config.datasetPipeline`、旧 Schema 和扩展字段无损。
- 403、503、超时、取消、空结果和大结果状态准确，配置不丢失。

### 视觉门禁

- 正式编辑器无 Emoji 图标；按钮、弹窗、表单、空状态和提示使用统一令牌。
- 属性侧栏没有常驻的大段帮助文字；错误和运行状态仍就地可见。
- 每个区域最多一个主按钮；弹窗 footer 顺序一致。
- 375、768、1024、1280、1440 五种宽度无页面级横向溢出、遮挡或不可见主操作。
- light、warm、eye-care、dark 四主题正文对比度不低于 4.5:1，交互边界和焦点不低于 3:1。

### 自动化门禁

- UI Vitest 全量通过，且不新增 Vue 未解析组件、无效 DOM 属性、未处理 Promise 警告。
- `npm --prefix mateclaw-dataagent-ui run build` 通过。
- `make dashboard-dataagent-test` 和 `make dashboard-verify-local` 通过。
- Chrome channel E2E 全量通过，无失败、跳过和只跑单例标记。
- Google Chrome 9222 CDP 生成 14 张截图和 JSON 摘要；可见无名交互控件为 0，控制台无未处理 Error，意外失败请求为 0。
- 所有证据对应同一候选 SHA；旧截图、其他 SHA 或 Playwright 自启浏览器不能替代 9222 证据。

### 交付门禁

- `git diff --check` 通过，无冲突标记。
- 每个任务独立提交，中文 commit，暂存范围与任务文件一致。
- 验收记录明确标记 PASS、FAIL、BLOCKED、NOT_RUN；未执行的真实 Aloudata 或正式环境联调不得写为通过。
- 当前工作树原有修改被保留，没有被本计划回滚、覆盖或误提交。

## 7. 推荐执行顺序

1. Task 1 建立视觉令牌和复用基础组件。
2. Task 2 统一状态与回写出口，先解决结构性风险。
3. Task 3 收敛侧栏信息层级。
4. Task 4 重整数据集来源流程。
5. Task 5 统一弹窗、表单、按钮和反馈。
6. Task 6 完成画布和响应式体验。
7. Task 7 固化后端契约。
8. Task 8 收敛路由和资源加载。
9. Task 9 建立自动化 E2E 门禁。
10. Task 10 在用户 Google Chrome 9222 做视觉验收。
11. Task 11 执行聚合门禁并形成同 SHA 证据。

Task 2～6 修改相互依赖的编辑器文件，适合由同一实现者连续执行；Task 7 可独立评审；Task 9～11 必须等 UI 与后端候选稳定后执行，避免快照和证据反复失效。
