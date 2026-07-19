# 洞察系统仪表组件设计规范

## 1. 设计目标

- 打造现代、专业、可信赖的数据洞察界面
- 提升信息层级与数据可读性
- 通过动画与交互增强操作流畅感
- 保证多端、多分辨率下的可用性
- 沉淀可复用的设计 Token 与组件模式

## 2. 设计原则

### 2.1 层次清晰（Clarity）

- 使用背景色、边框、阴影建立空间层级
- 标题、指标、说明文字使用明确的字号与字重梯度
- 关键指标优先展示，辅助信息降级处理

### 2.2 克制用色（Restraint）

- 以品牌橙（`--main-orange`）为唯一强调色
- 成功/上涨使用品牌绿，失败/下跌使用警示红
- 中性灰阶用于文字、边框、背景分隔

### 2.3 即时反馈（Responsiveness）

- 悬停、选中、加载均提供视觉反馈
- 状态切换使用 150-250ms 过渡动画
- 数据更新时采用淡入替代生硬替换

### 2.4 密度适配（Density）

- 默认密度兼顾信息展示与呼吸感
- 紧凑模式用于小尺寸组件与筛选器
- 响应式断点：1920/1440/1024/768/480

## 3. 设计 Token

### 3.1 颜色

| Token | 用途 |
|-------|------|
| `--main-orange` | 品牌主色、强调、选中、上涨 |
| `--dark-orange` | 悬停、聚焦、重强调 |
| `--light-orange` | 浅色背景、KPI 高亮卡片 |
| `--very-light-orange` | 标签、提示、趋势条背景 |
| `--theme-bg` | 页面底层背景 |
| `--theme-surface` | 卡片/面板背景 |
| `--theme-surface-elevated` | 浮层面板、工具栏 |
| `--theme-surface-hover` | 悬停背景 |
| `--theme-text` | 主要文字 |
| `--theme-text-secondary` | 次要文字、说明 |
| `--theme-text-muted` | 占位、禁用、时间戳 |
| `--theme-border` | 默认边框 |
| `--theme-border-strong` | 分隔线、表头边框 |

### 3.2 圆角

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-sm` | 4px | 按钮、标签、输入框 |
| `--radius-md` | 8px | 卡片、面板 |
| `--radius-lg` | 12px | 大卡片、抽屉、模态 |
| `--radius-xl` | 16px | 特殊容器、悬浮面板 |

### 3.3 阴影

| Token | 用途 |
|-------|------|
| `--shadow-card` | 默认卡片阴影 |
| `--shadow-card-hover` | 悬停卡片阴影 |
| `--shadow-dropdown` | 下拉/浮层阴影 |

### 3.4 间距

以 4px 为基准单位：

- `--space-xs`: 4px
- `--space-sm`: 8px
- `--space-md`: 12px
- `--space-lg`: 16px
- `--space-xl`: 24px

### 3.5 字体层级

| 层级 | 字号 | 字重 | 用途 |
|------|------|------|------|
| H1 | 20px | 700 | 页面标题 |
| H2 | 16px | 600 | 区域标题、卡片标题 |
| H3 | 14px | 600 | 小标题、Tab |
| Body | 13px | 400 | 正文、表格 |
| Caption | 12px | 400 | 说明、元信息 |
| Metric | 24-32px | 700 | KPI 数值 |

### 3.6 动画

| Token | 值 | 用途 |
|-------|-----|------|
| `--transition-fast` | 150ms ease | 颜色、边框 |
| `--transition-base` | 250ms ease | 位置、阴影、尺寸 |
| `--transition-slow` | 350ms ease | 页面切换、抽屉 |

## 4. 组件规范

### 4.1 卡片容器（Widget Card）

- 背景：`--theme-surface`
- 圆角：`--radius-md`
- 阴影：`--shadow-card`
- 边框：1px solid `--theme-border`
- 悬停：阴影加深、微上浮（`translateY(-2px)`）
- 选中：边框变为 `--main-orange`，添加橙色光晕

### 4.2 工具栏（Toolbar）

- 背景：`--theme-surface-elevated`
- 底部边框：`--theme-border`
- 左侧：返回按钮 + 标题/名称输入
- 右侧：主次操作按钮分组
- 高度：48-52px

### 4.3 页面 Tab

- 默认：透明背景、次要文字
- 悬停：背景 `--theme-surface-hover`
- 选中：背景 `--main-orange`、白色文字
- 切换使用 200ms 背景色过渡
- 子页面 Tab 使用较低饱和度的选中态

### 4.4 KPI 卡片

- 单指标：大号数值居中，说明与趋势在下方
- 多指标：横向等分，使用细竖线分隔
- 趋势上涨：`--success-color`，下跌：`--danger-color`
- 高亮卡片：浅色橙背景 + 主色边框

### 4.5 图表组件

- 标题区：左侧标题，右侧组件级时间筛选
- 图表区：自动填充剩余空间
- Tab 栏位于标题下方
- 无数据：中心占位，带图标与提示

### 4.6 表格组件

- 表头：浅色背景、加粗、居中
- 单元格：居中、8px 垂直内边距
- 斑马纹：`--theme-surface-hover`
- 行悬停：高亮

### 4.7 筛选器

- 全局筛选栏：面板式圆角容器，内部筛选项横向排列
- 组件内筛选：紧凑模式，标题与控件同行或上下堆叠
- 变化触发 300ms 防抖

### 4.8 AI 分析

- 顶部：标题 + 生成按钮/加载指示
- 内容：Markdown 渲染，分区卡片化
- 数据概览与 AI 生成结论分区展示
- 底部：免责声明

## 5. 布局规范

### 5.1 编辑器布局

- 页面菜单树：200px（可折叠）
- 物料面板：200px
- 属性面板：280px（可折叠）
- AI 面板：340px（可选展开）
- 画布：弹性剩余空间
- 小屏（<1024px）：侧边栏折叠为图标抽屉

### 5.2 预览布局

- 顶部：工具栏 + 页面 Tab
- 中部：全局筛选栏（如有）
- 下部：画布网格
- 报告抽屉：50% 宽度，移动端 100%

### 5.3 响应式断点

| 断点 | 行为 |
|------|------|
| >=1440px | 完整多栏布局 |
| 1024-1439px | 属性面板默认折叠 |
| 768-1023px | 编辑器侧边栏折叠，预览页工具栏换行 |
| <768px | 单栏堆叠，KPI 卡片单列，表格横向滚动 |

## 6. 动画规范

### 6.1 入场动画

- 卡片：从下方淡入上滑，stagger 30-50ms
- 页面内容：整体淡入 250ms
- Tab 内容：交叉淡入 200ms

### 6.2 交互动画

- 按钮悬停：背景色/边框 150ms
- 卡片悬停：上移 2px + 阴影加深 250ms
- 选中：边框颜色 150ms + 微弱缩放

### 6.3 加载状态

- 数据加载：骨架屏或脉冲占位
- AI 生成：旋转图标 + 渐进文字提示
- 报告生成：工具栏指示器

## 7. 实现要点

- 所有新增样式优先使用 CSS 变量，避免硬编码
- 组件内使用 scoped CSS，通用动画与工具类放全局
- 动画使用 `transform` 与 `opacity`，启用 GPU 加速
- 响应式使用 CSS 媒体查询与容器查询结合
- 保持 Vue 组件职责单一，样式与逻辑解耦
- 表格与图表使用容器尺寸监听实现自适应

## 8. 实现说明

### 8.1 文件清单

| 文件 | 说明 |
|------|------|
| `src/assets/dashboard-design.css` | 全局设计 Token、通用工具类、动画 |
| `src/views/insight/DESIGN_SPEC.md` | 本设计规范文档 |
| `src/views/insight/components/DashboardCanvas.vue` | 画布布局、卡片容器、全局筛选栏 |
| `src/views/insight/components/KpiCardWidget.vue` | KPI 指标卡片（单/多指标、Tab） |
| `src/views/insight/components/ChartWidget.vue` | ECharts 图表卡片 |
| `src/views/insight/components/DataTableWidget.vue` | 数据表格卡片 |
| `src/views/insight/components/AiAnalysisWidget.vue` | AI 分析内容卡片 |
| `src/views/insight/components/FilterSelectWidget.vue` | 筛选器组件 |
| `src/views/insight/components/TimeFilterWidget.vue` | 时间筛选器组件 |
| `src/views/insight/DashboardPreviewView.vue` | 预览视图、页面 Tab、报告抽屉 |
| `src/views/insight/InsightDashboardEditorView.vue` | 编辑器视图、四栏布局、移动端面板 |
| `src/i18n/locales/zh-CN.ts` | 中文国际化 |
| `src/i18n/locales/en-US.ts` | 英文国际化 |

### 8.2 响应式适配

- **>=1440px**：完整四栏编辑器布局，属性面板常驻
- **1024-1439px**：编辑器页面树/物料面板折叠为图标栏
- **768-1023px**：编辑器属性面板变为右侧抽屉，预览页工具栏换行
- **<768px**：编辑器仅展示画布，底部出现页面/组件/属性切换栏，点击后以侧滑抽屉形式打开对应面板；预览页 KPI 单列、表格横向滚动、筛选器纵向堆叠

### 8.3 国际化

所有新增用户可见文案均通过 `vue-i18n` 注入，避免硬编码。关键新增 key：

- `insight.reportGenerate` / `reportView` / `reportTitle` / `reportDownload`
- `insight.reportGenerated` / `reportGenerateFailed` / `generatingReport`
- `insight.aiDisclaimer`
- `insight.editorMobile.pages` / `components` / `properties`
- `insight.firstPageName` / `pageDefaultName` / `subPageDefaultName`
- `insight.previewFailed`

### 8.4 性能注意

- 卡片入场动画使用 `animation-delay` 实现 stagger，动画仅触发一次
- 阴影/位移过渡使用 `transform` 和 `box-shadow`，避免触发重排
- 全局 CSS 变量已考虑暗黑主题，通过 `html[data-theme='dark']` 覆盖阴影
