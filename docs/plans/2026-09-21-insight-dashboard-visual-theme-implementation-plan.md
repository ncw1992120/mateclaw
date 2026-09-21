# 洞察仪表盘默认视觉与用户配色 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让“洞察 → 仪表盘”新建页面默认具备有层次的颜色与图标，并让非前端用户通过“选主题 → 预览 → 保存；必要时覆盖单个指标”配置视觉效果；提供 10 套包含 5 套暖色系的通用预设，用“策略解读”验证通用能力，不在组件中固化业务文案或指标名。

**Architecture:** 在现有 Dashboard Schema 增加可选 `theme`，把预设主题解析为画布容器局部 CSS 变量，并将指标色板、图表色板分别传给 KPI、组合卡片与 ECharts；每项指标只保存图标键和“跟随主题/自定义”的色彩意图。前端编辑器和正式预览使用同一个主题解析器，主题切换复用编辑器已有撤销/重做历史，不新增独立历史栈。后端沿用 `schemaJson` 存储与权限模型，补充 DTO 字段、配置校验和复制/AI 修改的无损回传测试，不新建主题表或新接口。

**Tech Stack:** Vue 3、TypeScript、Element Plus、`@element-plus/icons-vue`、ECharts 6、Vitest、Vue Test Utils、Playwright、Google Chrome CDP `9222`；Spring Boot、Java 21、JUnit 5、Jackson、Mockito。颜色校验优先在已有依赖中复用；确需增加时只引入 `colord` 与其 a11y 插件。

**Spec:** 本轮已确认的“默认颜色与图标、仪表盘级主题、组件级配色、Element Plus CSS Variables + ECharts Theme + 轻量颜色校验”讨论；`docs/策略解读/design.md`、`docs/策略解读/指标分组卡片原型设计.md`、`docs/策略解读/策略解读-prototype.html`、项目根 `AGENTS.md` 与 `MEMORY.md`。**交互细节以 `docs/策略解读/主题外观原型设计.md`（Q1～Q12，2026-09-21 确认）与 `docs/策略解读/主题外观原型.html` 为准**，本计划已吸收其 Q11（指标样式弹窗重设计）、Q12（密度档 + 组件 Token 消费）及本次补充的 Schema 对齐、暖色预设、切换确认、实时字段预览、键盘操作和异常状态要求。本计划即这些决策的可执行细化，不另建一份冲突的设计说明。

## Global Constraints

- 主题能力属于通用仪表盘；不得在 `KpiCardWidget`、主题预设或后端 DTO 中硬编码“策略解读”的指标名、布局、数据源或色值映射。
- 当前已有 `html[data-theme]` 的 light/warm/eye-care/dark 和 `--db-*` 全局变量；仪表盘主题仅作用于编辑器画布与正式预览画布，不改变全站主题，也不污染编辑器侧栏、弹窗和其他页面。
- 旧 Schema 缺少 `theme` 时保持现有视觉；新建仪表盘显式写入默认主题 `blue`。已有“策略解读”可由用户在主题面板选择默认主题后保存，不做隐式批量迁移。
- 历史 `kpiMetrics[].styles.*.color` 为显式 HEX，必须保留并按自定义色处理；新建指标默认写入“跟随主题”语义。字段注册表仍是展示名/单位的唯一事实源，视觉配置按 `fieldKey` 挂载，不能把视觉设置误当字段名或数据筛选条件。
- 预设主题优先于默认 Token；单组件/单指标显式覆盖优先于预设；无效颜色、未知图标键、未知主题 ID 有安全回退并向编辑者提示，不执行任意 SVG/HTML/CSS 或远程图标 URL。
- 图标只从受控本地图标注册表中选择，显示必须有文字或 `aria-label`；状态与涨跌不能只靠颜色表达。保留当前 `--db-up`/`--db-down` 与“好坏”语义色分离，不用主题色重新定义涨跌方向。
- 组件不自带主题配置面板，只声明消费主题 Token（§1.5）；全仓唯一优先级例外规则 = “组件/指标显式配置 > 仪表盘主题”。指标样式弹窗按 Q11 重设计为「例外管理器」（四字段平铺、砍字段下拉/独立 HEX 行/字体下拉/右侧展示区）；`styles.*.family` 旧数据只读兼容——读取与 round-trip 保留，UI 不展示、不改写。
- 卡片级密度档（Q12）是组件级配置：`config.density ∈ {compact, standard, large}`，默认 `standard`，随组件 config 持久化与复制/AI 修改 round-trip；“恢复预设”只清仪表盘 `theme.overrides`，不清除密度档。
- 主题是仪表盘级配置，不按页面 Tab 分开保存；页面切换、组件选择和组合卡片内部切换均继承同一个主题。
- 预设切换存在未保存的主题级覆盖时必须二次确认；确认后只清理主题级覆盖，不清理指标级视觉例外、组件密度档或数据配置。
- `metricPalette` 只负责 KPI/指标强调色，`chartPalette` 只负责 ECharts 系列；未配置 `metricPalette` 时允许解析器回退到 `chartPalette`，但 UI 必须显示当前生效来源。
- 主题切换、指标样式编辑必须进入现有编辑器撤销/重做历史；不新增主题专属历史栈。
- 使用项目现有 Element Plus、ECharts、Vue 组件；不引入第二套 UI 框架、图表框架或完整 BI 产品。Iconify 只作为图标数量确实不足后的独立评估项，本轮不引入运行时远程 Iconify API。
- 编辑/预览/保存/刷新/仪表盘复制/AI 修改均要保留主题配置；数据查询与筛选请求不得因为纯视觉变更发生变化。
- 所有改动保留工作区其他未提交内容；执行者在每项任务前复查 `git status --short`，只暂存该任务文件。当前 `src/types/index.ts`、`InsightDashboardEditorView.vue`、`CombinationCardWidget.vue` 已有并行未提交改动，实施时必须逐块合并，不得整文件覆盖。
- 真实视觉验收必须连接用户已有的 Google Chrome CDP `http://127.0.0.1:9222`；自行启动的 Playwright Chromium 只能用于自动化回归，不能作为 9222 视觉验收证据。

## Review Focus

- 旧 KPI 的 `styles.value.color = "#1f2329"` 在换主题后仍保持原色；新 KPI 随主题改变颜色；Task 2/4 的单测与 QA-10 覆盖。
- 暗色/护眼/暖色的全站主题切换后，画布配色仍可读，侧栏不被仪表盘主题污染；Task 1/6 与 QA-08、CDP 截图矩阵覆盖。
- 含顶层 KPI、组合卡片子 KPI 和 ECharts 的仪表盘复制/AI 修改后，`theme`、指标图标、覆盖色不丢；Task 3/5 与 QA-13 覆盖。
- 用户输入 `#fff` 白字白底、非法 HEX、超长图标键或未知主题时，保存被拦截或按明确回退规则展示，不能产生不可读空白；Task 2/3 与 QA-11/12 覆盖。
- 图表已有自定义 `option.color` 时保留它；未自定义时用主题调色板，切换主题只改视觉，不重查数据；Task 5/6 与 QA-15 覆盖。
- 单卡大量指标（10～20 个）：配色/图标按稳定序号循环不串位；密度档三档切换字号整体变化、`config.density` 复制/AI 修改 round-trip 不丢、恢复预设不清除；数据表格/筛选器等按 §1.5 映射换肤且无新增配置项；Task 1/3/5 与 QA-21/22/23 覆盖。
- 用户已配置主题级覆盖后切换预设：不得静默丢失覆盖，确认后只清主题级覆盖；Task 4 与 QA-24 覆盖。
- 修改指标四字段字号/粗细/颜色后，画布必须真的变化且不增加数据请求；Task 2 与 QA-22 覆盖。
- 无选中图标、预设卡 Enter/Space、图标网格焦点、Esc 回焦点等键盘路径必须完整；Task 2/4 与 QA-17 覆盖。
- 主题解析失败、保存中、保存失败和未知预设必须保留可操作草稿并安全回退；Task 1/4/6 与 QA-25/26 覆盖。

---

## 0. 基线与交付范围

项目已经有 `src/assets/dashboard-design.css` 的 `--db-*` Token、六组卡片颜色和四种全站主题；`src/utils/color-presets.ts` 有局部取色器预设；KPI 的 `KpiMetricConfig.styles` 已支持固定 HEX 色；ECharts 在 `src/composables/useEChartsRenderer.ts` 中直接 `echarts.init(container)`，并允许传入 `option.color`。因此这次主要补齐的是仪表盘级主题配置、默认图标、主题与卡片/图表的统一映射，以及旧显式颜色的优先级。

后端 `InsightDashboardUpdateRequest.schemaJson` 是原样字符串入库，但仪表盘复制、AI 修改及 Schema 规范化会用 `InsightDashboardSchemaDTO` 反序列化再序列化。新的主题/指标字段若只在前端声明，可能在这些路径被丢弃；后端测试是本计划的交付内容。

本计划的最终用户操作为：

```text
进入“策略解读”编辑器 → 点击“主题外观” → 选择预设主题并立即在画布预览
→ 可选：修改主色/卡片/图表色板 → 可选：给某个指标选图标或覆盖色
→ 保存 → 正式预览 → 刷新/复制仪表盘后视觉一致
```

不包含新数据源、图表类型、图标远程市场、全站主题编辑器或业务专用组件。

## 1. 固定的数据与交互契约

### 1.1 Schema 字段

新增字段均可选；旧 Schema 不应因为读取而被自动写回。

```ts
type DashboardThemePreset = 'blue' | 'indigo' | 'teal' | 'amber' | 'dark-data'
type DashboardThemeMode = 'preset' | 'custom'
type MetricColorMode = 'theme' | 'custom'

interface DashboardThemeConfig {
  mode: DashboardThemeMode
  presetId: DashboardThemePreset
  overrides?: Partial<{
    primary: string
    secondary: string
    background: string
    cardBackground: string
    border: string
    text: string
    textMuted: string
    positive: string
    negative: string
    warning: string
    info: string
    metricPalette: string[]
    chartPalette: string[]
    radius: 'small' | 'medium' | 'large'
    shadow: 'none' | 'soft' | 'strong'
  }>
}

interface KpiMetricVisualConfig {
  iconKey?: string       // 白名单中的本地图标键；无值时按序号使用通用图标
  colorMode?: MetricColorMode
  accentColor?: string   // colorMode === 'custom' 时有效
}

interface KpiMetricFieldStyle {
  size: number
  family: string          // 旧数据只读兼容（Q11）：读取与 round-trip 保留，UI 不展示、不改写
  color?: string          // 旧值不删除；新建 theme 模式可省略
  colorMode?: MetricColorMode // 缺失且有 color 时按 custom 解释
  bold: 'bold' | 'normal'
}

// InsightDashboardSchema.theme?: DashboardThemeConfig  // 仪表盘级，所有页面共享
// KpiMetricConfig.visual?: KpiMetricVisualConfig
// InsightComponent.config.visual?: { colorMode: MetricColorMode; accentColor?: string }
// InsightComponent.config.density?: 'compact' | 'standard' | 'large'   // 卡片密度档（Q12），默认 standard

interface ResolvedDashboardTheme {
  source: 'legacy' | 'configured'
  primary: string
  secondary: string
  background: string
  cardBackground: string
  border: string
  text: string
  textMuted: string
  positive: string
  negative: string
  warning: string
  info: string
  metricPalette: string[]
  chartPalette: string[]
  radius: 'small' | 'medium' | 'large'
  shadow: 'none' | 'soft' | 'strong'
}

interface ThemeValidationError {
  field: string
  code: 'invalid_color' | 'contrast_too_low' | 'invalid_palette' | 'invalid_preset'
  message: string
}
```

后端在 `InsightDashboardSchemaDTO` 镜像 `theme` 与 `KpiMetric.visual`。组件级视觉设置暂存既有 `config.visual` Map，组合卡片子项如现有 DTO 未声明，先补完整 round-trip 所需字段/类型再开放其独立配置；不能只在前端展示已保存却会被复制路径吞掉的选项。未知字段兼容依照当前 DTO/Jackson 策略，不靠前端假设其必然被保留。

主题契约补充：`theme.overrides.metricPalette` 只用于 KPI/指标强调色，`theme.overrides.chartPalette` 只用于图表系列；若缺少 `metricPalette`，解析器回退到 `chartPalette`。`theme.overrides.radius` 与 `theme.overrides.shadow` 统一纳入覆盖项，因此“恢复预设”会清理它们，但不会清理指标级 `visual`、组件 `density` 或数据配置。`theme.mode` 由编辑器根据是否存在覆盖项维护，读取旧 Schema 时不要求补写。

### 1.2 颜色与图标优先级

```text
页面背景、卡片背景：全站主题 Token（旧 Schema）/ 仪表盘预设 + 用户覆盖（新 Schema）
图表颜色：图表显式 option.color > 仪表盘 chartPalette > 既有 ECharts 默认
指标强调色：指标 custom > 组件 custom > metricPalette[稳定序号] > --db-accent
指标文字色：styles.*.colorMode=custom 的 HEX/旧 styles.*.color 显式 HEX > theme 模式对应语义 Token
指标字号：styles.*.size 显式值（例外，仅用户改动后存在）> 卡片密度档 config.density > 主题基准（Task 1 densityScale）
状态/涨跌色：--db-positive/--db-danger 和 --db-up/--db-down 分别计算，绝不混用
图标：指标 iconKey > 受控注册表按顺序给出的通用默认图标 > 无图标（文本照常显示）
```

默认图标按卡片内指标顺序循环，不按“关联计划数”等中文名称猜测。用户可通过选择器把某项指标改为具体图标。复制组件、调整位置、切 Tab 或切数据集后，以稳定 `fieldKey` 维持选择；新字段才应用默认值。

### 1.3 配置页面

- 编辑器顶部工具栏增加“主题外观”入口。抽屉第一层为预设主题缩略卡；第二层“自定义”提供常用颜色、图表色板、圆角和阴影，所有更改在画布实时预览，但只有点击“保存仪表盘”才入库；关闭抽屉不另设第二套持久化按钮。
- 预设主题缩略卡扩展为 10 套：云枢蓝、靛蓝、青绿、琥珀、深色数据、玫瑰红、珊瑚橙、暖橙、金黄、酒红；暖色预设与深色预设均可在任意全站主题下选用。
- 如果当前存在主题级未保存覆盖，点击其他预设先弹确认；确认文案说明将清理仪表盘级颜色/形状覆盖，但不会影响指标级视觉例外、组件密度和数据配置。
- 主题选择器提供“恢复预设”，只清当前仪表盘 `theme.overrides`，不删除组件或 KPI 的单项覆盖。
- 在现有 `MetricStyleDialog.vue` 按 Q11 重设计为「例外管理器」：弹窗收窄为 420px 单栏，**删除字段下拉**（展示名/指标值/单位/辅助说明四字段平铺为表格：字段名 | 字号滑杆 10–40px | 粗细开关 | 颜色色块）、**删除独立 HEX 输入行**（色块即唯一入口，取色器内含 HEX）、**删除字体下拉**（`family` 只读兼容，UI 不展示）、**删除右侧展示区**（画布实时同步即预览，弹窗内仅保留强调色单行内联预览承担 3:1 对比度校验）。新增「图标与强调色」区（图标选择、跟随主题/自定义）置顶与「重置该指标」按钮；所有修改即时写编辑器草稿并同步画布，无「确定/取消」。字号滑杆未显式改动时跟随卡片密度档，改动即成为指标显式例外，双击重置 = 清除例外。已有固定 HEX 在进入配置时显示为“自定义（旧配色）”，防止用户切主题后被悄悄改色。
- 图标选择器只显示一套经审核的 16～24 个 Element Plus SVG 图标，带图形、中文名称和搜索；无图标时可清除选择。选择器不接受任意类名、SVG 字符串或 URL。
- 自定义色值即时校验 HEX 格式和文字/背景对比度；正常字号文字对比度至少 4.5:1，大字号和有意义的非文字图形至少 3:1。对不合格组合就地显示“当前颜色与背景对比不足”，禁用保存该配置；辅助信息仍有文字标签，不单靠颜色区分。
- 自定义层新增“指标色板”和“图表色板”的职责说明；两者分别影响 KPI 强调色和 ECharts 系列。指标色板未覆盖时显示“回退到图表色板”，每一行展示当前生效来源。
- 画布、组合卡片内子卡片、正式预览使用同一个 `DashboardThemeProvider`/解析函数。弹窗可能 Teleport 到 `body`，其主题预览色通过显式变量或配置传入，不依赖 DOM 祖先继承。
- 卡片级密度档（Q12）：KPI 分组卡与组合卡片头部提供 紧凑/标准/大号 三档（仅编辑态可见），一次性映射 指标名/数值/图标 三组字号；值存 `config.density`，未设置按 `standard`；「恢复预设」不清除；单卡大量指标不逐指标设背景，统一继承卡片背景（容器显式背景仍优先）。

### 1.5 组件消费映射与「大量指标」策略（Q12）

**原则：组件不自带主题配置面板，只声明消费哪些 Token**；全仓唯一例外规则 = “组件/指标显式配置 > 仪表盘主题”。

| 组件 | 主题消费方式 |
| --- | --- |
| 卡片（KPI 分组卡） | 背景/边框/文字随主题；指标强调色 = 色板[稳定序号]、图标 = 按序循环，仅例外单独配 |
| 组合卡片 | 子组件继承父画布主题；容器显式背景仍按现有优先级覆盖；密度档随卡片 config |
| 全部 ECharts 图表 | `withDashboardChartTheme` 注入色板与轴/图例色；显式 `option.color` 优先 |
| `DataTableWidget.vue` | 表头底色 = 卡片底混入 primary 6%、正文 `text`、表头文字 `textMuted`、边框 `border`、斑马纹混入 `background`、hover 混入 primary 8% |
| `FilterSelectWidget.vue` / `TimeFilterWidget.vue` | 控件边框 `border`、文字 `text`/`textMuted`；Teleport 到 body 的下拉面板必须显式传入变量，不依赖 DOM 祖先继承 |
| `AiAnalysisWidget.vue` | `primary`/`info` 点缀，主按钮按 text/card 反色 |

单卡大量指标（10～20 个）不逐个配置：配色与图标按 `fieldKey` 稳定序号循环色板与图标库（零配置、切主题整体换色）；用户只对需要突出的个别指标覆盖强调色/图标；字号统一走密度档。深色预设下全部规则同样生效，涨跌/状态色依旧锁定。

### 1.4 默认预设与依赖选择

| 预设 | 主色 | 色板方向 | 使用场景 |
| --- | --- | --- | --- |
| `blue` | `#1E40AF` | 蓝、紫、青、绿、橙 | 新建仪表盘默认 |
| `indigo` | `#4F46E5` | 靛蓝、青、紫 | 分析型 |
| `teal` | `#0F766E` | 青绿、蓝、橙 | 运营型 |
| `amber` | `#B45309` | 琥珀、蓝、绿 | 经营型 |
| `dark-data` | `#60A5FA` | 明亮蓝、紫、青 | 深色数据展示 |
| `rose` | `#BE185D` | 玫红、紫、蓝、橙 | 用户、内容、品牌运营 |
| `coral` | `#C2410C` | 珊瑚、橙、青、紫 | 活动、增长、营销 |
| `orange` | `#EA580C` | 暖橙、蓝、绿、紫 | 交易、销售、业务运营 |
| `gold` | `#A16207` | 金黄、棕、蓝、青 | 财务、经营、目标管理 |
| `burgundy` | `#9F1239` | 酒红、玫红、靛蓝、金 | 高级经营、品牌、管理看板 |

每套预设必须同时给出背景、卡片、正文、边框、正负向色及图表色板，经浏览器计算后验证对比度；表中主色只是入口色，不代表所有文字都使用此色。项目已有 Element Plus Icons/ECharts，直接复用。先检查锁文件是否已有颜色工具；没有时用 `colord/plugins/a11y` 作为唯一新增运行时包，并将色值解析/对比度封装在 `dashboard-theme.ts`，不向组件扩散依赖。ECharts 可由现有 `option.color` 注入色板，不必为每位用户动态调用 `registerTheme`；如需完整图表轴/图例样式，可在同一个主题 option builder 中统一生成。

## 2. 文件职责与修改边界

| 文件 | 改动 |
| --- | --- |
| `mateclaw-dataagent-ui/src/types/index.ts` | 增加 `DashboardThemeConfig`、`KpiMetricVisualConfig` 及可选字段。 |
| `mateclaw-dataagent-ui/src/utils/dashboard-theme.ts`（新） | 10 套预设、校验、主题解析、局部 CSS 变量、指标/图表色板与 `densityScale`（密度档→指标名/数值/图标字号）生成；纯函数。 |
| `mateclaw-dataagent-ui/src/utils/dashboard-icon-registry.ts`（新） | 受控图标键、中文名称和本地 Element Plus 图标组件映射。 |
| `mateclaw-dataagent-ui/src/utils/dashboard-schema.ts` | 旧版兼容、新建默认值与保留 `theme`；避免不相关字段丢失。 |
| `mateclaw-dataagent-ui/src/assets/dashboard-design.css` | 只添加仪表盘作用域 Token/必要的视觉状态；保留全站变量定义。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DashboardThemePanel.vue`（新） | 预设、自定义颜色、对比度错误和即时预览配置。 |
| `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue` | 主题入口、草稿变更、预设切换确认、现有撤销/重做历史接入、保存与画布 Theme Provider。 |
| `mateclaw-dataagent-ui/src/views/insight/DashboardPreviewView.vue` | 使用已保存主题渲染正式预览，无编辑控件。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DashboardCanvas.vue` | 向顶层和嵌套 Widget 透传已解析主题；既有拖拽事件不变。 |
| `mateclaw-dataagent-ui/src/views/insight/components/KpiCardWidget.vue` | 指标默认图标、单项强调色、主题文字与旧 HEX 优先级。 |
| `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/MetricStyleDialog.vue` | 按 Q11 重设计：四字段平铺表格（无字段下拉/独立 HEX 行/字体下拉/展示区）、图标与强调色区、字号滑杆跟随密度档、重置该指标、即时生效写草稿。 |
| `mateclaw-dataagent-ui/src/views/insight/components/ChartWidget.vue`、`src/composables/useEChartsRenderer.ts` | 把调色板合入 option，在主题变化时重绘；显式 `option.color` 优先。 |
| `mateclaw-dataagent-ui/src/views/insight/components/CombinationCardWidget.vue` | 子组件继承主题；容器自己的背景配置仍按现有优先级覆盖；卡片头部密度档控件与 `config.density` 消费。 |
| `mateclaw-dataagent-ui/src/views/insight/components/KpiCardWidget.vue`、`DataTableWidget.vue`、`FilterSelectWidget.vue`、`TimeFilterWidget.vue`、`AiAnalysisWidget.vue` | 按 §1.5 映射消费主题 Token；不新增主题配置项；Teleport 下拉显式传变量。 |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java` | 增加主题与指标视觉字段，并核对组合卡片子项 round-trip。 |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/DashboardThemeValidator.java`（新） | 主题字段和图标键的白名单、长度与色值校验，供保存链路调用；旧无主题 Schema 直接通过。 |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/InsightDashboardServiceImpl.java` | 保存/复制/AI 修改时调用校验并保留主题；数据查询不介入主题。 |
| `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTOTest.java` | 主题、旧 Schema、图标和指标颜色的 Jackson round-trip。 |
| `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/InsightDashboardThemeServiceTest.java`（新） | 保存、复制、无效配置及越权场景的服务层定向测试。 |
| `docs/plans/fixtures/insight-dashboard-theme-schema.json`（实施时新建） | 前后端 round-trip 共用的最小 JSON 夹具：theme、KPI visual、旧 styles、datasetPipeline。 |
| `mateclaw-dataagent-ui/e2e/dashboard-theme.spec.ts`（新） | 编辑器保存到正式预览的端到端回归。 |
| `mateclaw-dataagent-ui/e2e/cdp-dashboard-theme-visual-check.mjs`（新） | 仅连接 Chrome 9222，覆盖 10 套预设、截图、AX、控制台和网络取证。 |
| `docs/plans/2026-09-21-insight-dashboard-visual-theme-acceptance.md`（实施时新建） | 按候选 SHA 记录测试与视觉证据、问题和最终结论。 |

## 3. 实施任务

每个任务先写能失败的行为测试，再做最小实现，最后仅暂存任务相关文件并用中文 commit。若执行环境无法运行某项命令，验收记录填 `BLOCKED` 与原因，不得写 `PASS`。任务间接口以 §1 的类型和优先级为准。

### Task 1：主题模型、预设和旧 Schema 兼容

**Files:** `src/types/index.ts`、`src/utils/dashboard-theme.ts`、`src/utils/dashboard-schema.ts`、`src/assets/dashboard-design.css`；新建 `src/utils/__tests__/dashboard-theme.spec.ts`，扩展 `src/utils/__tests__/dashboard-schema.spec.ts`。路径均相对 `mateclaw-dataagent-ui/`。

**Interfaces:** `resolveDashboardTheme(config: DashboardThemeConfig | undefined, globalMode: 'light' | 'warm' | 'eye-care' | 'dark'): ResolvedDashboardTheme`；`themeCssVariables(theme: ResolvedDashboardTheme): Record<string,string>`；`validateDashboardTheme(config): ThemeValidationError[]`；`densityScale(density: 'compact' | 'standard' | 'large' | undefined): { metricName: number; metricValue: number; icon: number }`（未知值回退 standard）。

- [x] 写失败测试：缺少 `theme` 时保留旧 `--db-*` 视觉；新建时 `blue` 显式写入；10 种预设均返回完整 Token 且 5 种暖色预设的文字/卡片对比度合格；自定义只覆盖点选的字段；`metricPalette` 与 `chartPalette` 职责分离且指标色板缺失时回退图表色板；未知 `presetId` 和 `NaN`/非法色值有明确回退或校验错误；再次迁移不丢 `theme`；`densityScale` 三档字号递增且 `standard` 为基准、未知档位回退。先以缺少模块和 Schema 透传断言确认失败，再实现。

  ```ts
  const legacy = resolveDashboardTheme(undefined, 'light')
  expect(legacy.source).toBe('legacy')
  expect(themeCssVariables(legacy)).toEqual({}) // 旧仪表盘沿用现有 CSS Token
  const teal = resolveDashboardTheme({ mode: 'preset', presetId: 'teal' }, 'light')
  expect(teal.source).toBe('configured')
  expect(teal.chartPalette).toHaveLength(5)
  expect(resolveDashboardTheme({ mode: 'preset', presetId: 'rose' }, 'light').primary).toBe('#BE185D')
  expect(validateDashboardTheme({ mode: 'custom', presetId: 'blue', overrides: { text: '#FFFFFF', cardBackground: '#FFFFFF' } }))
    .toEqual(expect.arrayContaining([expect.objectContaining({ code: 'contrast_too_low' })]))
  ```
- [x] 运行 `pnpm exec vitest run src/utils/__tests__/dashboard-theme.spec.ts src/utils/__tests__/dashboard-schema.spec.ts`，先确认新增断言因主题模块与 Schema 透传缺失而失败。
- [x] 实现纯主题解析器和局部 CSS 变量映射，局部使用 `--insight-*` 变量，并从这些变量派生画布内 `--db-*`；`html[data-theme]` 全局变量继续由原 CSS 管理。色板中每个预设至少提供 5 个数据系列色。
- [x] 将 10 套预设完整写入注册表：`blue`、`indigo`、`teal`、`amber`、`dark-data`、`rose`、`coral`、`orange`、`gold`、`burgundy`；每套均包含页面背景、卡片背景、边框、正文、次要文字、正负向色、警告、信息、指标色板和图表色板。
- [x] 统一 `theme.overrides` 结构，圆角和阴影也放入覆盖项；实现 `themeSource(theme, token)` 返回“预设/主题覆盖/回退/旧配色”的来源标签，供后续面板和指标弹窗展示。
- [x] 同命令复跑，确认 2 个定向测试文件共 11 个断言通过；再运行 `pnpm build`，`vue-tsc --noEmit` 和 Vite 均通过。
- [x] 精确暂存本任务文件，提交 `feat(洞察): 建立仪表盘主题预设与兼容模型`（待本任务交付时记录 SHA）。

### Task 2：本地图标注册表与 KPI 视觉意图

**Files:** `src/utils/dashboard-icon-registry.ts`、`src/utils/kpi-metrics.ts`、`src/views/insight/components/KpiCardWidget.vue`、`src/views/insight/components/card-attribute/MetricStyleDialog.vue`；新增 `src/utils/__tests__/dashboard-icon-registry.spec.ts`、`src/views/insight/components/__tests__/KpiCardWidget.theme.spec.ts`，扩展现有 KPI 配置测试。

**Interfaces:** `resolveMetricVisual(metric, componentVisual, theme, index): { iconKey: string | null; accentColor: string; textColors: Record<'name' | 'value' | 'unit' | 'helper', string> }`，`resolveDashboardIcon(iconKey): Component | null`。`styleToCss(style, themeColor?)` 保留旧单参数调用；在 `colorMode='theme'` 时用传入的主题色，旧 `styles.*.color` 没有 `colorMode` 时按自定义处理。`metric.visual.colorMode='theme'` 只控制指标图标/强调色，文本字段色仍由各自的 `styles.*.colorMode` 决定。

- [x] 写失败测试：四个新 KPI 获得不同默认本地图标；未知图标键仍有可读名称且不执行 HTML；旧 KPI 固定 HEX 保存/刷新不变；新 KPI 切换主题后强调色变化；切换 `fieldKey` 顺序或重投影不串色；关闭图标时标题仍保留；修改四字段字号/粗细/颜色后画布同步变化且请求次数不增加；无选中图标时图标网格仍可通过 Tab/方向键进入。先以缺少注册表/解析函数和默认 colorMode 断言确认失败。

  ```ts
  const oldStyle = { size: 28, family: 'system', color: '#1f2329', bold: 'bold' as const }
  expect(styleToCss(oldStyle, '#ffffff')).toContain('color:#1f2329')
  const newStyle = { size: 28, family: 'system', colorMode: 'theme' as const, bold: 'bold' as const }
  expect(styleToCss(newStyle, '#172554')).toContain('color:#172554')
  expect(resolveDashboardIcon('untrusted:remote-icon')).toBeNull()
  ```
- [x] 运行对应 Vitest 文件，先确认新增测试因注册表、解析函数和默认主题样式缺失而失败。
- [x] 建立 23 个受控本地图标的键/名称/组件清单；KPI 图标使用 `aria-hidden="true"`，文字名称维持可见。新建指标的四类字段使用 `colorMode='theme'` 且不写固定 HEX；`styleToCss`、`KpiCardWidget` 调用显式传入解析后的主题色，并保留旧单参数 API。旧 Schema 的固定 `styles.*.color` 未迁移或清空，`family` 仍保留读取与 round-trip。
- [x] 按 Q11 重设计 `MetricStyleDialog` 为 420px 单栏四字段平铺样式表，保留字号/粗细/颜色即时配置，增加图标开关、强调色和「重置该指标」；取消独立 HEX 行、字体下拉和右侧展示区，画布作为实时预览。
- [x] 确保 `KpiCardWidget` 消费四字段字号、粗细、颜色、单位/辅助说明及默认图标；每次修改只改变响应式草稿，未引入数据请求。
- [x] 补齐图标选择器的完整方向键/Enter/Space/Esc 网格交互：无选中项时按钮仍可 Tab 进入，方向键按 6 列网格移动，Enter/Space 选中，Esc 关闭弹窗。
- [x] 复跑定向测试（19/19）与 `pnpm build`，`vue-tsc --noEmit` 和 Vite 均通过；旧样式兼容测试通过。
- [x] 精确暂存并提交 `feat(洞察): 为指标卡增加默认图标与配色配置`（待本任务交付时记录 SHA）。

### Task 3：后端 Schema round-trip、保存和复制契约

**Files:** `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java`、新建 `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/DashboardThemeValidator.java`、修改 `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/InsightDashboardServiceImpl.java`、扩展 `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTOTest.java`、新建 `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/InsightDashboardThemeServiceTest.java` 和共享夹具 `docs/plans/fixtures/insight-dashboard-theme-schema.json`。

**Interfaces:** Jackson 中 `theme` 和 `kpiMetrics[].visual` 字段名与 §1 一致；复制仍由 `copyDashboard(id)` 完成，更新仍由 `updateDashboard(id, request)` 完成；不新增主题 API。

- [x] 写失败测试：完整主题配置及 KPI 图标/颜色经 DTO read→write 字段和值无损；10 个预设 ID 均可通过白名单校验；`metricPalette`、`chartPalette`、`overrides.radius`、`overrides.shadow` round-trip 无损；组件级 `config.density` round-trip 无损；旧 Schema 不带主题仍可 read→write；无效配置不能保存非法 CSS。先以缺少 DTO 字段/校验器确认失败。

  ```java
  String themeFixtureJson = Files.readString(Path.of("../docs/plans/fixtures/insight-dashboard-theme-schema.json"));
  InsightDashboardSchemaDTO parsed = mapper.readValue(themeFixtureJson, InsightDashboardSchemaDTO.class);
  JsonNode written = mapper.readTree(mapper.writeValueAsString(parsed));
  assertEquals("teal", written.at("/theme/presetId").asText());
  assertEquals("chart-bar", written.at("/pages/0/components/0/kpiMetrics/0/visual/iconKey").asText());
  assertEquals("custom", written.at("/pages/0/components/0/kpiMetrics/0/styles/value/colorMode").asText());
  ```
- [x] 用本地 Java 21 + Maven 3.9.16 运行定向命令；首次失败指向缺少 DTO/校验器，补齐后通过 8/8。
- [x] 增加 DTO 可选字段，`DashboardThemeValidator` 校验主题结构/图标键白名单、长度和色值；在保存 Schema 入库前调用，复制/AI 修改继续使用同一 DTO 映射。无主题旧配置直接通过。不改数据查询、数据库结构和权限接口；组合卡片子项继承父主题，不新增独立视觉字段。
- [x] 使用 `docs/plans/fixtures/insight-dashboard-theme-schema.json` 完成 DTO read/write 对齐，检查 `theme`、`visual`、`fieldKey`、密度与色板无损。
- [x] 精确暂存并提交 `feat(洞察): 保全仪表盘主题与指标视觉配置`（待本任务交付时记录 SHA）。

### Task 4：主题面板与编辑器保存流程

**Files:** 新建 `src/views/insight/components/DashboardThemePanel.vue` 和对应 `__tests__/DashboardThemePanel.spec.ts`；修改 `src/views/insight/InsightDashboardEditorView.vue`、`src/views/insight/components/DashboardCanvas.vue`；必要时修改 `src/utils/color-presets.ts`，统一色板来源。

**Interfaces:** 面板通过 `modelValue: DashboardThemeConfig` / `update:modelValue` 只改编辑器 Schema 草稿；保存继续使用已有 `handleSave()` 的 `schemaJson`；Canvas 接收解析后的主题对象/样式，不直接读取全站存储。

- [x] 写失败测试：入口可键盘操作；预设切换立刻改变画布、未点保存时后端不会收到更新；存在主题级覆盖时切换预设必须确认；恢复预设只清覆盖项；色值非法或未知预设有就地提示。
- [x] 运行面板相关 Vitest，先确认新组件不存在导致导入失败。
- [x] 实现 10 套缩略预设、主色/背景/卡片/文本、指标/图表色板和主题即时预览；自定义项展示当前生效来源；只更新编辑器 Schema 草稿，不发数据查询，保存沿用既有按钮。
- [x] 为预设卡补齐 `role=radio`、Enter/Space、方向键；存在覆盖时发出确认事件，由编辑器二次确认后清除主题级覆盖；保存失败仍由既有保存链路保留草稿。
- [x] 复跑定向测试 2/2 与 `pnpm build`；主题面板使用可滚动抽屉布局，375/768/1024/1440 的进一步 CDP 视觉矩阵留到 Task 6。
- [x] 精确暂存并提交 `feat(洞察): 增加仪表盘主题外观配置`（待本任务交付时记录 SHA）。

### Task 5：ECharts、组合卡片与预览统一消费主题

**Files:** `src/composables/useEChartsRenderer.ts`、`src/views/insight/components/ChartWidget.vue`、`src/views/insight/components/CombinationCardWidget.vue`、`src/views/insight/components/DataTableWidget.vue`、`src/views/insight/components/FilterSelectWidget.vue`、`src/views/insight/components/TimeFilterWidget.vue`、`src/views/insight/components/AiAnalysisWidget.vue`、`src/views/insight/DashboardPreviewView.vue`，必要时调整 `src/views/insight/components/DashboardCanvas.vue`；扩展 `src/views/insight/components/__tests__/ChartWidget.spec.ts`，新建 `src/composables/__tests__/useEChartsRenderer.theme.spec.ts` 与预览回归测试。

**Interfaces:** `withDashboardChartTheme(option: Record<string,unknown>, theme: ResolvedDashboardTheme): Record<string,unknown>` 从 `useEChartsRenderer.ts` 导出且必须返回新对象；`renderECharts(container, optionRaw, theme?)` 的可选第三参保持原调用兼容；显式 `option.color` 不覆盖，原有 option 白名单、函数剥离和 100KB 上限不变。

- [x] 写失败测试：图表无显式色板时使用 `chartPalette`；显式 `option.color` 原值保留；切主题只改变视觉 option，不改变原 option 数据。

  ```ts
  const tealTheme = resolveDashboardTheme({ mode: 'preset', presetId: 'teal' }, 'light')
  const raw = { series: [{ type: 'bar', data: [1, 2] }] }
  const themed = withDashboardChartTheme(raw, tealTheme)
  expect(themed.color).toEqual(tealTheme.chartPalette)
  expect(raw).not.toHaveProperty('color') // 不能就地改后端返回的 option
  expect(withDashboardChartTheme({ ...raw, color: ['#123456'] }, tealTheme).color).toEqual(['#123456'])
  ```
- [x] 运行相关 Vitest，先确认 `withDashboardChartTheme` 尚不存在而失败。
- [x] 将解析后的主题从编辑器/预览注入 Canvas 与子 Widget；在 ECharts 白名单和函数剥离前构造局部副本，显式色板优先，轴/图例文字跟随主题；组合卡片内 KPI/图表继承父画布主题。Canvas 根节点注入局部 `--insight-*`/`--db-*`，数据表格、筛选器、时间筛选和 AI 组件沿用这些作用域 Token，旧 Schema 未传主题时保持旧视觉。
- [x] 复跑图表/组合卡片/Canvas 定向测试 13/13 与 `pnpm build`；全量 Vitest 留到 Task 6 汇总回归。
- [x] 精确暂存并提交 `feat(洞察): 统一图表和组合卡片主题渲染`（待本任务交付时记录 SHA）。

### Task 6：真实产品流 E2E 与 CDP 9222 视觉验收

**Files:** 新建 `mateclaw-dataagent-ui/e2e/dashboard-theme.spec.ts`、`mateclaw-dataagent-ui/e2e/cdp-dashboard-theme-visual-check.mjs`；必要时给 `package.json` 加 `test:e2e:cdp:theme` 脚本；实施时新建同目录验收记录文档。

- [ ] 编写 E2E：从 `http://127.0.0.1:5174/?nav=insight` 的列表进入真实“策略解读”编辑页；切预设/改一项指标/保存/预览/刷新/复制；断言 Schema 与 computed style、图标及图表色板，不以 toast 作为成功证据。测试使用独立测试仪表盘或事先备份并恢复“策略解读” Schema，避免污染用户配置。
- [ ] 在测试数据和本地 Java 21 后端就绪时运行 `pnpm exec playwright test e2e/dashboard-theme.spec.ts`，期望全部通过。接口错误、空数据与筛选失败不能伪装成视觉通过。
- [ ] CDP 脚本只调用 `chromium.connectOverCDP('http://127.0.0.1:9222')`；检查 `/json/version` 确认连到 Google Chrome；只创建/关闭自身 Page，不关闭用户浏览器、不修改用户现有 Tab；登录复用浏览器既有会话，不把 Cookie/JWT/密码写入截图目录。
- [ ] 采集 light/warm/eye-care/dark × 10 套预设的代表矩阵，以及 375/768/1024/1440 宽度、默认图标/自定义图标、图表/组合卡片、主题抽屉、保存后预览、焦点态与对比度。记录同一候选 SHA 的截图、AX 快照摘要、控制台 error、失败请求、computed style 与 ECharts option.color；视觉审阅确认无截断、低对比度、图标丢失及全站污染。
- [ ] 产出 `docs/plans/2026-09-21-insight-dashboard-visual-theme-acceptance.md`：写明候选 SHA、Chrome 版本与 CDP 端点、数据准备、场景/截图路径、单测/E2E 命令和结果。若 9222 不可用，记 `BLOCKED`，不能用 Playwright 新开的浏览器冒充真实验收。
- [ ] 修正验收发现的缺陷后重新跑受影响测试、构建和 CDP 场景；精确暂存并提交 `test(洞察): 补齐仪表盘主题全链路与视觉验收`。

## 4. 测试工程师用例与预期结果

测试准备：在隔离工作区准备一份含顶层 KPI、四指标 KPI 分组、ECharts 折线/饼图、数据表、筛选器、AI 分析、组合卡片子 KPI/图表以及两个页面的仪表盘；复制“策略解读”作为验收样本，记录原始 `schemaJson` 与 Dashboard ID。每个用例注明应用主题、仪表盘主题、视口、是否保存。不得以固定业务数值作为视觉断言。

| ID | 操作 | 预期结果 |
| --- | --- | --- |
| QA-01 | 新建仪表盘并加入 KPI/图表 | Schema 有 `theme.presetId=blue`；画布呈现图标、数据色板、清晰文字和层次化卡片。 |
| QA-02 | 打开旧版无 `theme` 的仪表盘 | 页面与改动前一致，未自动改写 Schema；保存无关字段也不新增主题。 |
| QA-03 | 选择 9 个非默认预设（含 `indigo`、`teal`、`amber`、`dark-data` 及 5 个暖色预设） | 每次即时改变画布及图表配色；侧栏与列表页不跟着换色；暖色预设文字和卡片对比度合格。 |
| QA-04 | 修改主色、页面背景、卡片背景、指标色板和两项图表色 | 预览即时反映；KPI 和图表分别消费对应色板；每项颜色显示当前生效来源；图表系列顺序稳定，改色时数据值和筛选条件不变。 |
| QA-05 | 关闭主题抽屉再重新打开，尚未保存 | 草稿在本次编辑会话继续存在；刷新前不应把未保存草稿写入后端。 |
| QA-06 | 点击“恢复预设” | 仅仪表盘覆盖色、圆角和阴影被清空；单指标自定义色/图标、组件密度和数据配置不受影响。 |
| QA-07 | 保存并进入正式预览，然后刷新 | 预览与编辑器画布相同；刷新后主题、图标和图表色板仍在。 |
| QA-08 | 切换全站 warm/eye-care/dark | 当前仪表盘主题仍可读；文本、边框、图标、图表图例符合对比度要求，应用导航保持全站主题。 |
| QA-09 | 给某项 KPI 选图标并拖拽指标位置，切 Tab/切页面 | 图标与 `fieldKey` 绑定，位置或顺序变化不导致图标串到其他指标。 |
| QA-10 | 给新 KPI 设“跟随主题”，给旧 KPI 保留固定 HEX | 新 KPI 切主题后变色；旧 KPI 固定 HEX 保持原值，且在 UI 中显示“自定义”。 |
| QA-11 | 输入 `#GGGGGG`、空色值、超长字符串、未知图标键 | 就地提示/安全回退；无法保存非法自定义配置；控制台无未处理异常。 |
| QA-12 | 设白底白字或深底深灰字 | 显示具体对比度不足提示，不能保存该组合；旁边文字/状态仍可辨识。 |
| QA-13 | 复制仪表盘、通过 AI 修改标题后重新打开 | `theme`、`kpiMetrics[].visual`、原有数据集绑定与组件 ID 映射仍正确。 |
| QA-14 | 从组合卡片里查看子 KPI 和子图表 | 默认继承父仪表盘主题；容器已配置的背景继续优先，图标不丢。 |
| QA-15 | 图表原先有显式 `option.color`，再切仪表盘主题 | 显式色板保持；无显式色板的图表跟随主题；数据请求次数不增加。 |
| QA-16 | 通过筛选器、时间筛选器刷新数据 | 数值/图表更新；主题和图标保持；筛选请求不含视觉配置字段。 |
| QA-17 | 键盘操作主题入口、预设卡、取色器和指标图标 | Tab 顺序合理；预设卡方向键切换、Enter/Space 选中；图标网格无默认选中项时仍可进入；Esc 关闭后焦点回到触发按钮；有中文可访问名称。 |
| QA-18 | 375/768/1024/1440 视口切换，含 20 个指标及长名称 | 抽屉和内容可滚动；文字/图标不相互遮挡，无页面横向溢出或丢失操作按钮。 |
| QA-19 | 保存失败、网络断开或 403 时修改主题 | 错误就地可见，草稿不丢；正式预览仍显示上次已保存主题。 |
| QA-20 | 选择未知预设 ID 的历史/外部 Schema | 页面安全回退到可用主题，编辑器显示兼容提示，不执行任意 CSS/SVG。 |
| QA-21 | 单卡 12+ 指标切主题、切密度档（紧凑/标准/大号） | 配色与图标按稳定序号循环且不串位；三档字号整体变化（仅未显式改字号的指标跟随）；`config.density` 随保存/复制/AI 修改 round-trip 保留；「恢复预设」不清除密度档。 |
| QA-22 | 指标样式弹窗重设计回归 | 四字段平铺无下拉；字号滑杆未改动跟随密度档、双击清除例外；字体入口不存在且旧 `family` 数据渲染不变；颜色单入口（色块）且旧 HEX 标「旧配色」；「重置该指标」一键回跟随主题；关闭即生效、画布同步。 |
| QA-23 | 数据表格/筛选器/时间筛选/AI 分析切主题 | 表头、斑马纹、边框、控件、按钮按 §1.5 映射换肤；涨跌 ▲▼ 颜色锁定；Teleport 到 body 的下拉面板配色正确；各组件无新增主题配置项。 |
| QA-24 | 已修改主题级颜色/形状覆盖后切换预设 | 出现确认弹窗；取消不改变草稿；确认后只清理主题级覆盖，指标级视觉例外、组件密度和数据配置保留。 |
| QA-25 | 模拟主题解析失败、未知预设、保存中和保存失败 | 未知预设安全回退并提示；保存中不能重复提交；保存失败错误就地显示，草稿保留，正式预览继续使用上次已保存主题。 |
| QA-26 | 修改指标名称/数值/单位/辅助说明字号、粗细和颜色 | 画布对应字段即时变化，不触发数据请求；刷新/复制后视觉配置 round-trip 保留。 |

## 5. 验证标准与证据门禁

### 单元与契约

- 前端：新增 `dashboard-theme`、Schema 迁移、KPI、图表、主题面板、组合卡片定向测试全部通过；全量 `pnpm exec vitest run` 通过；`pnpm build` 包含 `vue-tsc --noEmit` 和 Vite 构建成功。
- 后端：`InsightDashboardSchemaDTOTest` 与 `InsightDashboardThemeServiceTest` 定向测试全部通过，至少覆盖旧 Schema、完整主题 round-trip、复制/AI patch、非法输入及权限。后端全量测试如依赖 Testcontainers/Docker，单列其环境条件和结果，不把未执行项写成通过。
- 契约：同一份 Schema 夹具在前端解析、后端 DTO read/write、前端重新读取后，`theme`、`kpiMetrics[].visual`、`fieldKey`、已有 `config.datasetPipeline` 保持语义等价；新增视觉字段不进入数据查询参数。

### 视觉与可用性

- 必须通过用户 Google Chrome CDP 9222，在同一候选 SHA 上收集“编辑器 + 正式预览 + 保存刷新 + 主题矩阵 + 视口矩阵”的可追溯截图；逐张人工审阅，不以截图文件存在作为通过条件。
- 正文对比度 ≥ 4.5:1；大字号文字和承担含义的图标 ≥ 3:1；颜色选择器中的低对比组合有即时错误。所有图标提供相邻文字或可访问名称；筛选、警告、上涨/下跌具备图标/文字等非颜色线索。
- 375、768、1024、1440 宽度不出现横向页面溢出、内容遮挡或无法操作的主题弹窗。动画遵守 `prefers-reduced-motion`；主题切换不得产生闪烁的全页样式重排。
- CDP 过程中无新增未处理控制台异常和非预期失败请求；错误场景的预期 4xx 与产品错误状态逐条标注，不混入成功场景。
- 提交前 `git diff --check`、冲突标记扫描、暂存文件列表检查；提交后记录最终 SHA。若计划实施包含 push，另核对本地/远程 SHA 一致；本计划本身不执行发布。

## 6. 依赖与风险处理

1. **旧固定 HEX 阻断主题：** 以 `fieldKey` 和 `colorMode` 判别新旧配置；旧数据显式优先。不得用正则批量改写旧 `styles` 色值。
2. **DTO 丢字段：** 在前端配置面板上线前先完成后端 read/write 与复制测试；任何 AI patch 路径都要复用同一 Schema 结构。
3. **暗色预设与全站 dark 叠加：** 主题解析同时读取仪表盘配置与应用主题，明确画布局部作用域；画布外不注入 `--el-*` 全局变量。
4. **图标包体积：** 只静态导入注册表内选定的 16～24 个 Element Plus Icons，不 `import *` 全量暴露给用户配置；图标键白名单可供后端校验。
5. **图表重绘：** 构造 option 新对象并沿用 ECharts 实例管理/ResizeObserver；主题变化只更新视觉 option，不触发执行 API。保留原 option 的 100KB 检查、白名单和 XSS 函数剥离。
6. **验收数据安全：** 真实“策略解读”已由用户配置；E2E 优先在复制仪表盘上测试，删除测试副本只在明确是本次创建且已核对 ID 后执行。不得改写生产样本，也不得把凭据写入测试证据。
7. **预设切换丢失覆盖：** 预设切换前计算 `theme.overrides` 是否为空；非空时必须走确认弹窗，取消保持草稿引用和值不变，确认只清主题覆盖，不清指标视觉和组件密度。
8. **指标/图表色板混用：** 解析器、组件和面板统一使用 `metricPalette`/`chartPalette` 的职责定义；测试同时覆盖两套色板、指标色板缺失回退和来源提示，禁止组件各自解释。
9. **原型宣称实时但组件未消费样式：** KPI 渲染必须使用四字段 size/bold/color/unit/helper；样式变更测试监听网络请求计数，保证视觉更新不触发数据查询。

## 7. 执行交接

按 Task 1→6 顺序实施；Task 3 后端 Schema 兼容须在主题配置正式保存前完成。每个 Task 更新验收记录和中文 commit；前后端分别完成定向测试后再做一次全量回归。最终由测试工程师按 QA-01～QA-23 复核，并以 CDP 9222 的实际截图和交互记录给出 `PASS / FAIL / BLOCKED`。若用户只要求本实施计划，停在文档评审，不提前修改产品源码。

交互契约补充说明：本计划已吸收 `docs/策略解读/主题外观原型设计.md` 的 Q1～Q12 决策（2026-09-21 确认）——Q11 指标样式弹窗重设计（例外管理器）、Q12 密度档（`config.density`）与组件 Token 消费映射（§1.5）、Q1～Q10 的草稿星号/占位呈现/对比度范围/语义色边界/图标循环/dark-data 组合/画布交互/过渡动画/抽屉会话等；实现中遇到交互歧义以该文档及其配套原型 `主题外观原型.html` 为准，若需偏离先回写设计文档再实施。

### 实施进度

| 任务 | 状态 | 证据 |
| --- | --- | --- |
| Task 1 主题模型、预设和旧 Schema 兼容 | 已完成 | 前端定向测试 11/11；`pnpm build` 通过；本地提交后推送记录待补 |
| Task 2 图标注册表与 KPI 视觉意图 | 已完成 | 定向测试 19/19；`pnpm build` 通过；图标网格支持 Tab/方向键/Enter/Space/Esc |
| Task 3 后端保存、复制与 round-trip | 已完成 | Java 21 + Maven 3.9.16 定向测试 8/8；主题保存前校验、DTO fixture round-trip 通过 |
| Task 4 主题配置面板与编辑器接入 | 已完成 | 面板定向测试 2/2；`pnpm build` 通过；编辑器主题草稿/确认切换/画布 KPI 透传已接入 |
| Task 5 图表、组合卡片与预览消费主题 | 已完成 | 定向测试 13/13；`pnpm build` 通过；编辑/预览 Canvas 主题变量与 ECharts 色板已接入 |
| Task 6 E2E、CDP 9222 视觉验收 | 未开始 | — |

## 参考方案

- [Element Plus CSS Variables 与局部主题覆盖](https://element-plus.org/en-US/guide/theming.html)
- [Apache ECharts 主题与色板](https://echarts.apache.org/handbook/en/concepts/style/)
- [colord 对比度与可读性插件](https://github.com/omgovich/colord)
- [Iconify 本地化扩展选项](https://github.com/iconify/iconify-unplugin)
