# 项目记忆

## 当前状态

- 开发工作流约定：直接使用本地 `feature/dev_fu` 分支，不创建开发用 Git worktree；开始前检查分支和工作区，保留无关改动，仅提交本次任务文件。
- 2026-09-07：首次接手。仓库为 Maven 多模块项目，当前分支 `dataagent`，HEAD 为 `56e3870c`。
- 通用运行时由 `mateclaw-server` 提供，入口为 `vip.mate.MateClawApplication`，默认端口 `18088`；`mateclaw-ui` 使用 `/api/v1`。
- DataAgent 是独立宿主：`mateclaw-dataagent` 通过 `mateclaw-sdk` 嵌入 server runtime，入口为 `vip.mate.dataagent.DataAgentApplication`，端口 `18089`、context-path `/dataagent/api`；配套 UI 开发端口 `5174`。
- 根 `pom.xml` 当前 reactor 只包含 `mateclaw-plugin-api`、`mateclaw-server`、`mateclaw-plugin-sample`，没有纳入 `mateclaw-sdk`/`mateclaw-dataagent`；后两者需要单独按依赖顺序构建。
- 2026-09-26 已验证本机 JDK `21.0.12.1`，Maven `3.9.16` 位于 `/Users/srant/.maven/apache-maven-3.9.16/bin/mvn`（可能不在交互 shell 的 `PATH` 中）；DataAgent 可单独运行 `clean package -DskipTests` 构建。

## 产品能力原则

- 仪表盘/报表需求不能按单个原型做一次性特例实现。当前策略解读原型只是验证洞察仪表盘通用搭建能力的切入点，最终目标是让非前端、非 IT 用户通过傻瓜式组件和配置，快速搭建类似的分析页面、经营报表和数据看板。
- 设计新能力时优先抽象为可复用的组件、参数、事件、动作和模板能力；不得把“策略解读”等业务名称、指标文字、布局坐标和交互逻辑硬编码进通用组件。
- 用户心智应接近“拖组件、选数据、配参数、连联动、预览发布”，而不是编写 Vue、Hook、SQL 或事件处理代码。复杂能力应通过可视化配置暴露，并提供合理默认值、校验、预览和错误提示。
- 组件之间的联动应采用声明式的 Dashboard Runtime 模型：组件产生标准化交互事件，动作将事件映射为仪表盘参数变化，参数绑定驱动相关组件重新查询或刷新。组件内部 Hook 只负责接入运行时，不作为用户配置协议。
- 需要区分三类能力：展示组件（KPI、图表、数据表格等）、控制组件（筛选器、时间筛选、视角/变量选择等）和操作组件（查询、重置、刷新、导航、详情/业务动作等）。操作类组件应是通用 Action Card，而不是当前原型专属按钮。
- 公共交互协议至少应覆盖参数类型与作用域、事件来源与 payload、动作类型、组件绑定关系、刷新/请求依赖和错误状态；任意 API 类动作必须通过后端白名单、权限和审计控制，不能允许仪表盘直接调用任意地址。
- 评估功能是否完成时，要验证“非 IT 用户能否仅通过编辑器完成配置、能否理解联动关系、预览和发布后行为是否一致”，而不仅是当前原型页面能否显示出来。

## 洞察仪表盘预览

- 画布布局和组件是否存在由 `InsightDashboardSchema` 驱动的前端 `DashboardCanvas` 渲染；`InsightDataBindService` 只提供组件数据。没有直连 `dataSource` 的组件（例如数据集管道/脚本绑定组件）不应返回“未配置数据源”错误 DTO，否则前端错误分支会遮住组件内容；无查询结果应保留组件并呈现各组件空态，真实连接或执行错误才设置 `error`。预览页加载直连数据后，再单独恢复结果集与脚本绑定。
- 仪表盘预览页的外层和内容区背景必须复用同一份解析后的 `dashboardTheme` 页面底色，和 `DashboardCanvas` 保持一致；工具栏、页签、组件卡片可继续使用各自的表面色。
- 数据集与 Python“查看数据”弹窗将“查询”和“组件渲染”分成两个动作：查询只刷新原始结果；点击“组件渲染”才把本次有效结果通过 `render` 事件应用到画布，并在弹窗中用 KPI/图表/表格实际组件展示同一份渲染数据。数据集和 Python 弹窗共用 `ComponentDataPreview` 与 `componentPreviewData`；KPI 查询多行校验仍阻止应用渲染。
- KPI 组件“查看数据 → 组件渲染”必须按当前 `componentData.kpiList` 临时生成预览指标配置；旧 `kpiMetrics` 中未出现在结果集的字段不可显示，也不能按位置或首个 KPI 值兜底。空结果应显示空态，临时投影不得写回仪表盘配置。

## 属性面板筛选绑定

- 旧 `CardAttributeSidebar` 的组件级筛选绑定展示区和编辑弹窗已移除；历史 `scriptFilterBindings` 的读取、回显、脚本执行和保存兼容需要保留。当前筛选器与数据集的配置入口在数据集输入/查询配置流程中，勿因删除旧入口而删除运行时兼容链路。

## Python 编辑器输入输出契约

- Py 脚本执行前，数据集必须先按 `datasetInputs[].queryConfig` 与运行时查询参数生成查询计划；展示字段、筛选绑定、排序和分页属于输入契约，不能由 Python 脚本自行重新拼接。
- 查询计划应在 DataAgent 侧完成权限校验、字段/筛选/排序/分页下推，并物化为按 `inputName` 读取的 prepared input；Py Runner 默认只读取已经处理好的结果，不接收 `datasetId`、`filters`、`columns` 或连接信息。
- 数据集卡的“查询配置”定义静态规则；“查询数据”填写本次实际筛选值并查看实际入参、各输入结果和 Python 最终输出。Py 编辑器可以复用这些入口，但不得维护第二份查询规则。
- Python 最终结果的查看筛选只作用于已经生成的 Python 输出，不重新查询数据源；操作符从绑定的页面筛选器推断（单选 `=`、多选 `in`），时间筛选拆为 `>=` 开始和 `<` 结束（左闭右开）。未启用条件不生效；筛选器绑定失效时应提示并禁止静默套用。
- Python 最终结果弹窗点击“查询”后只更新当前结果表；点击“组件渲染”时，将当前筛选、排序和展示字段投影后的数据转换为组件渲染数据，通过 `render` 事件同步到画布并显示弹窗内组件预览。不要在普通查询时隐式覆盖画布。
- Py 编辑器的系统代码应由查询配置生成固定输入参数和输出契约示例，用户代码只负责 Join、计算和业务逻辑；查询配置或脚本变化后，既有结果必须标记为过期。
- 当前实现已有 Planner → prepared input → Runner 的新链路，但仍保留旧 `datasets.read` 兼容接口；Py 编辑器预览目前默认提交空 `queryContext`/运行参数，因此“查询数据中的实际筛选值驱动 Py 预览”仍需作为产品交互和验收项补齐。

## Python 编辑器样例加载性能

- 打开 `PythonScriptDialog` 时，编辑器正文应立即生成并保持可编辑；上游样例查询在后台完成后，仅当用户尚未改动正文时才回填造数注释，避免慢查询阻塞编辑或覆盖输入。
- 编辑器造数样例通过 `fetchDatasetSampleRows` 调用 `previewInput` / `previewDatasetDraft`；这些请求要显式传 `{ timeout: 0 }`，覆盖 UI Axios 的默认 60 秒客户端超时。该设置只取消浏览器等待上限，不改变 DataAgent 到 Aloudata 的服务端读取超时。

## KPI 指标字段投影

- KPI 卡片投影以查询配置中的 `displayFields[].role === 'measure'` 为权威来源；日期/维度字段不得因为进入结果集 `columns` 就自动变成 KPI。恢复结果集时可能只有部分列元数据，不能据此缩减已配置指标；顶层卡片与组合卡片页签中的子卡片共用同一套递归恢复与投影逻辑。

## Aloudata 实时指标/维度选择

- 选择器的类目、指标、维度应走配置数据源的实时接口：`category/list`、`metrics/list`、`dimension/list`；指标详情由 `metrics/batchDetail` 和 `metrics/dimensionAll` 提供，维度悬浮详情由 `dimension/detail?dimName=...` 提供。2026-09-23 对已配置 Aloudata 数据源实测上述接口成功；详情响应字段为平铺的 `dimName`、`dimDisplayName`、`originDataType`、`dimDescription` 等。
- Aloudata 的全局系统维度 `metric_time` 不应塞进普通目录分类；选择器需要将规范字段作为根级虚拟维度，并继续用指标-维度关联关系校验其可用性。当前 `AloudataFieldSelector.vue` 属于 `DatasetSourceDialog.vue` → `DatasetInputPanel.vue` 链路，但截至 2026-09-29 `DatasetInputPanel.vue` 未被生产代码引用；修改此选择器时，不能把单独组件测试误报为仪表盘页面的真实浏览器验收。
- `AloudataDialog.vue` 加载已选指标可用维度时调用单指标 `metric_dimensions`（`/metrics/dimension`）；不能只 mock 批量 `metric_all_dimensions`（`/metrics/dimensionAll`）。本地 mock 缺少前者会让已选指标的关系变成“无法校验”，弹窗禁用确认；修复后仍由选择器逐项禁用不兼容的指标/维度并给出悬浮原因。
- 当日 DataAgent 已确认运行 profile 为 `pgsql`（非 `local-mock`）；在本地 LaunchAgent 重启后遇到 PostgreSQL 密码认证失败。不要打印或提交数据库凭据，真实 UI 联调前需先恢复本机 DB 凭据；浏览器自动化不可用时应将 UI 点击验收明确标记为未执行，不可用接口直连代替。

## 本地 DataAgent 重启

- `docs/策略解读/restart-dataagent-backend.sh` 从本地 `feature/dev_fu` 分支 HEAD 创建临时 detached 部署工作树，在其中执行 `mateclaw-dataagent/pom.xml clean package -DskipTests` 后启动新 JAR；不会 fetch 并替换成远端分支提交。该临时目录仅供脚本构建/运行使用，不是日常开发 worktree。
- 脚本仅接管同一 Git common directory 下、由 DataAgent JAR 启动的旧进程；临时部署工作树在前台脚本随 JVM 退出时清理。`mock`、`runner`、`stop-mock` 等专用子命令不触发后端构建。
- 回归验证：`bash dev-support/local-simulation/scripts/test_latest_deploy_worktree.sh`、`bash dev-support/local-simulation/scripts/test_restart_dataagent_backend_build.sh`；真实构建使用 JDK 21 与本机 Maven 3.9.16。

## 洞察仪表盘弹窗层级

- Insight 视图内的 Element Plus `<el-dialog>` 与 `<el-drawer>` 必须显式设置 `append-to-body`。否则 Teleport 会被禁用，弹窗留在局部 stacking context 中，可能被画布覆盖或被容器裁切；新增覆盖测试应持续检查该目录下所有弹窗和抽屉。
