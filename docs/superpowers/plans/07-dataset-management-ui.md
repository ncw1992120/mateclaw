# 数据源与数据集管理入口 Implementation Plan

> 2026-09-14 状态更新：上述审计问题已按本地模拟范围逐项修复或收敛；正式入口、文件上传、HTTP/API 登记定义选择和统一预览已由真实 E2E 覆盖。页面闭环在本地模拟为 `PASS`，真实对象存储和外部连接登记仍属于环境联调条件。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 让用户能在现有 DataAgent 管理界面中配置 JDBC、Aloudata、HTTP/API 和文件来源，并把来源固化为可复用数据集。

**Architecture:** 复用现有 `DataAgentDatasourceController`、`DataAgentDatasetController`、`DatasourceForm.vue` 和 `DatasetEdit.vue`，以类型化 DTO 扩展而不是继续堆叠无结构 `connectionParams/sourceConfig`。凭据只写入后端加密配置，数据集响应只返回脱敏定义和统一 Descriptor。

**Tech Stack:** Java 21/Spring Boot、Jakarta Validation、Vue 3/TypeScript、Vitest、Vue Test Utils。

**Spec:** `docs/策略解读/design.md` 第 3、4、7 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 9 节（MGMT-C01～MGMT-UI05）。

**当前状态（2026-09-13）：** 四类来源管理入口、来源定义校验、权限矩阵和候选提交上的 UI `24/24` 测试已完成，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`；当前工作树追加五种来源选项、HTTP/API 仅引用已登记定义及 Aloudata 只读视图回归后为 `27/27`。

**当前头部基线（2026-09-14）：** 在提交 `240c2f04ae1af781bfde79b785955988105e2581` 上 UI 全量为 `10 files / 42 tests passed`，production build 与 `DESIGN-PASS` 通过；上段 `27/27` 保留为历史阶段记录。

## 前端产品闭环缺口（2026-09-14）

> 2026-09-14 实施进展：已关闭 `FE-CLOSE-02` 的“无正式入口”部分。新增 `/datasets`、`/datasets/new`、`/datasets/:id/edit` 路由，配置中心数据配置页增加“数据集管理”入口；列表支持读取已有数据集并进入新建、编辑/预览，路由包装器负责取消、返回和保存后的列表导航。另已将数据源新建从固定 Aloudata 改为先选择 MySQL、PostgreSQL、SQL Server 或 Aloudata，再进入连接表单；文件来源已接入受控上传接口并自动回填 `StoredFileRef.objectId`，不再要求用户手填对象 ID；HTTP/API 改为从所选数据源 `connectionParams.apiDefinitions` 目录下拉选择；编辑器中暂未开放的操作现在会给出明确反馈。仪表盘脚本结果绑定已由 09 子计划的真实 Chrome channel E2E 在本地模拟环境关闭，正式外部数据源联调仍是后续 Gate。

本地模拟数据和接口只替代外部系统，不降低正式前端入口、交互完整性和项目视觉风格要求。当前确认以下问题：

| 编号 | 级别 | 问题 | 复现步骤 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| FE-CLOSE-01 | P0 | 数据配置入口只展示指标平台 | 登录后进入“配置 → 数据配置”，查看列表并点击新建 | 可选择并管理 JDBC、Aloudata、HTTP/API、文件所需连接或注册项 | 本地模拟已补来源选择页并移除新建时固定 `source-id="60"`；HTTP/API 定义和文件对象在数据集配置中登记，正式外部登记管理仍待联调 |
| FE-CLOSE-02 | P0 | 五种来源编辑器没有正式入口 | 从顶部“配置”、数据配置页和洞察列表查找“新建数据集” | 可进入数据集列表和新建/编辑页，刷新、保存、取消、返回均正常 | 已完成正式路由和配置中心入口；真实 Chrome channel E2E 覆盖创建、预览、取消和返回 |
| FE-CLOSE-04 | P0 | 文件上传、下载是空实现 | 在数据集编辑器选择“文件” | 完成文件选择、校验、上传、对象引用回填、预览；下载不可用时不展示 | 本地模拟已完成选择、格式/大小校验、受控上传、对象引用回填和创建后统一预览；页面未展示未实现下载按钮 |
| FE-CLOSE-05 | P1 | HTTP/API 要求手填内部定义 ID | 在数据集编辑器选择“HTTP/API” | 从当前工作区已登记 API 中搜索选择，展示 endpoint 摘要、参数和预览错误 | 已改为读取所选数据源 `apiDefinitions` 目录下拉选择并展示 method/path/参数摘要；本地模拟创建/预览已通过，定义登记管理页和真实 API 错误联调仍待完善 |
| FE-CLOSE-06 | P1 | 多个可见按钮点击无反馈 | 点击“了解如何配置”“来源表”“新建计算字段”“分组依据”“聚合编辑器”“字段设置”“更多” | 动作可用；不在本期范围的动作应移除或禁用并解释 | 未开放操作均显示明确的后续版本提示；真实配置能力仍待后续实现 |
| FE-CLOSE-08 | P1 | 页面未完全遵循项目主题与组件风格 | 分别切换 light、warm、eye-care、dark 查看数据集编辑页 | 复用 `--theme-*`、Element Plus 和现有配置页布局，各主题下背景、边框、文字、焦点一致 | 数据集空态、预览工具栏/容器、结果空态及数据源表单全局基础容器、步骤条、白名单和操作按钮已改用主题令牌，并由四主题真实 E2E/CDP 快照验证；非 Chrome 原生外观及其他页面固定颜色仍待专项收敛 |
| FE-CLOSE-09 | P2 | 可访问性和可理解性风险 | 仅用键盘操作表单，并检查控件名称、焦点、错误关联和文字对比度 | 所有控件有可访问名称，顺序和焦点清晰，错误可定位，文字对比度足够 | 配置态核心原生控件已补齐 `for/id`、`aria-label`，预览表格行选择和编辑控件也有名称；四主题对比度已由 Chrome CDP 复验通过（辅助文字最低 4.97:1、状态标签最低 6.24:1），完整键盘遍历仍待专项验收 |

上述 P0 需以正式入口和真实后端 E2E 证据关闭；当前本地模拟 P0 已通过，正式外部登记/连接联调仍不得提前标记为生产 `PASS`。

**本次范围说明：** UI 继续兼容现有访问上下文，但不新增身份、角色或跨工作区权限；本地数据源和文件对象优先使用 `dev-support/local-simulation/` 的 Docker 服务。

**本地模拟：** 页面开发验证统一复用 `dev-support/local-simulation/` 的 MySQL/PostgreSQL、WireMock、MinIO 和 Python Runner；不在本子计划中另起数据库、对象存储或 API mock。

**开发验证配置：** 页面创建数据集时使用本地 MySQL/PostgreSQL、`local_sales_view`、已登记 OpenAPI `orders` 和 MinIO `files/<name>`；页面仍只提交来源类型化定义，不提交任意 URL、Header、本地路径或凭据。

**执行约定：** UI 验证前先运行模拟前置检查；页面创建的数据集必须能被 09 的同一工作区闭环读取，失败时记录来源类型和模拟服务响应，不回退到手工数据库写入。

**本轮复验记录（2026-09-13）：** 模拟五类来源定义可用，UI 全量 `27/27` 及生产构建通过；页面来源配置与 09 本地 E2E 共用同一批 fixture。新增用例锁定五种来源类型选项，验证 HTTP/API 只提交已登记的 `apiDefinitionId`，并验证 Aloudata 只提交选中的 `analysisViewId`，页面不接受任意 URL/Header/SQL。

## Global Constraints

- 数据源连接与数据集是两个对象：连接保存认证和访问边界，数据集保存已固化的表、SQL、指标视图、API 映射或文件引用。
- Aloudata 只允许选择已有指标视图；JDBC 才显示 SQL；HTTP/API 不允许脚本填写 URL；文件只允许选择已上传对象。
- 数据源列表和数据集目录均按现有权限过滤；没有租户名命名规则。

---

### Task 1: 类型化创建与更新契约

**Files:**
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasourceCreateRequest.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasourceUpdateRequest.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasetCreateRequest.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasetUpdateRequest.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasetSourceDefinition.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentDatasourceController.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentDatasetController.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/DatasetManagementControllerTest.java`

**Interfaces:**
- `DatasetSourceDefinition` 在同一文件中定义按 `sourceType` 判别的封闭接口及五个嵌套 record：`JdbcTableDefinition`、`JdbcSqlDefinition`、`AloudataViewDefinition`、`HttpApiDefinition`、`FileDefinition`。
- `POST /v1/datasets` 和 `PUT /v1/datasets/{id}` 接收类型化定义；响应不返回密码、认证 Header、签名 URL 或原始加密字段。

- [x] **Step 1: 写失败 Controller 测试**：已补充数据集创建/更新控制器对类型化来源定义的转发测试、列表/创建/删除方法的 viewer/member 权限声明测试，以及经过 `DataAgentWorkspaceInterceptor` 的 MockMvc viewer 读取/member 写入允许与拒绝测试；服务层创建/更新会统一调用 `DatasourceManageService.checkDatasourceReadable`，拒绝跨工作区、未授权或不可见数据源；数据集和数据源控制器所有返回 `R` 的管理端点均增加显式 workspace/global-admin 安全边界反射矩阵检查。
- [x] **Step 2: 运行测试**：类型化定义测试先因 `DatasetSourceDefinition` 不存在而失败，随后已通过；新增数据源可见性拒绝和控制器转发测试通过。
- [x] **Step 3（第一段）: 实现 DTO 校验和 Service 映射**：已增加 sealed 类型化来源定义及 JDBC/Aloudata/HTTP/文件五种 record；创建和更新数据集支持 `sourceDefinition`，旧 JDBC 请求继续兼容，来源定义序列化到内部 `sourceConfig`，数据集名称执行 NFKC 规范化、空值拒绝和工作区内唯一性检查，不复制 Adapter 查询逻辑。
- [x] **Step 4: 重跑测试**：数据集全部管理路由、数据源全部管理路由和文件上传路由均通过真实 `MockMvc + DataAgentWorkspaceInterceptor` 行为矩阵；定向测试全部通过。仍不提交，等待整体候选 SHA 验收。

### Task 2: 管理界面与来源选择器

**Files:**
- Modify: `mateclaw-dataagent-ui/package.json`
- Modify: `mateclaw-dataagent-ui/package-lock.json`
- Create: `mateclaw-dataagent-ui/vitest.config.ts`
- Modify: `mateclaw-dataagent-ui/src/types/index.ts`
- Modify: `mateclaw-dataagent-ui/src/api/datasource.ts`
- Modify: `mateclaw-dataagent-ui/src/api/dataset.ts`
- Modify: `mateclaw-dataagent-ui/src/views/datasource/DatasourceForm.vue`
- Modify: `mateclaw-dataagent-ui/src/views/dataset/DatasetEdit.vue`
- Create: `mateclaw-dataagent-ui/src/views/dataset/components/JdbcDatasetDefinition.vue`
- Create: `mateclaw-dataagent-ui/src/views/dataset/components/AloudataViewPicker.vue`
- Create: `mateclaw-dataagent-ui/src/views/dataset/components/HttpApiDatasetDefinition.vue`
- Create: `mateclaw-dataagent-ui/src/views/dataset/components/FileDatasetDefinition.vue`
- Test: `mateclaw-dataagent-ui/src/views/dataset/__tests__/DatasetEdit.spec.ts`

**Interfaces:**
- Consumes: 类型化创建/更新 API、Aloudata 视图目录 API、HTTP 参数定义 API、文件上传登记 API、Descriptor/preview API。

- [x] **Step 1（第一段）: 增加测试基础和来源定义测试**：已有 Vitest/Vue Test Utils；新增测试锁定 SQL 仅 JDBC 生成、非 JDBC 不携带 SQL。
- [x] **Step 2（第一段）: 运行测试**：来源定义新增用例先因模块不存在失败，补齐工具后该阶段与现有 UI 测试合计 15/15 通过；后续扩展后的当前工作树 UI 全量为 27/27，通过结果见总体验收记录。
- [x] **Step 3（第一段）: 接入现有 DatasetEdit**：增加来源类型选择；JDBC SQL 显示 SQL 编辑器并提交类型化 `sourceDefinition`；JDBC 表继续使用原表选择流程；Aloudata 增加只读指标视图目录加载和选择，提交 `analysisViewId`，不编辑视图 SQL；HTTP/API 仅输入后端已登记的 `apiDefinitionId`，文件仅输入已登记的 `objectId` 和格式，不接受任意 URL、Header 或本地路径。
- [x] **登记定义固化**：HTTP/API 数据集创建或更新时，根据数据源连接 `connectionParams.apiDefinitions` 解析 `apiDefinitionId`，将已登记的 endpoint、参数映射和结果路径固化到内部 `sourceConfig`；请求体不能直接注入 URL、Header 或凭据，缺少登记定义时拒绝保存。
- [x] **Step 4（第一段）: 运行 UI test 与 build**：当前工作树 27 个 Vitest 用例通过，production build 通过；`DatasetEdit.spec.ts` 覆盖五种来源选项、JDBC_SQL 的 SQL 入口、FILE 的无 SQL 约束、HTTP/API 只引用已登记定义及 Aloudata 只读视图选择；补充了真实 E2E 的 Chrome channel 配置，并在真实 Compose 栈中完成 API+文件和旧 Schema 兼容场景验证。
- [x] **Step 5: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=DatasetManagementControllerTest test
npm --prefix mateclaw-dataagent-ui test -- --run \
  src/views/dataset/__tests__/DatasetEdit.spec.ts
npm --prefix mateclaw-dataagent-ui run build
```

预期：MGMT-C01～MGMT-C07 和 MGMT-UI01～MGMT-UI05 全部 PASS；切换来源类型时只出现允许字段，非 JDBC 无 SQL 输入，编辑连接不回显认证信息，后端字段错误能定位到对应控件。

## 验收标准

**2026-09-14 快照与面板布局修复：** 编辑器属性区改为纵向可滚动，输入面板不再被子面板 `height:100%` 裁剪；双源 E2E 使用“脚本结果数据集输入”这一实际可访问标题，并逐行等待字段描述完成，避免异步 Descriptor 状态造成快照漂移。真实本地栈数据集入口创建、双源、错误/兼容和大结果场景合计 11 条 E2E 全部通过；快照基线已按当前渲染重新生成并以非更新模式验证。

**2026-09-14 键盘可访问性修复：** 组件库条目原先仅支持拖拽，键盘用户无法添加组件；现为每个条目补充 `role="button"`、`tabindex="0"` 和可访问名称，并支持 Enter/Space 直接添加。数据集配置中的 JDBC 表列表原先仅支持鼠标点击，现补充 `role="checkbox"`、`tabindex="0"`、`aria-checked` 和 Enter/Space 切换。新增 `ComponentPalette.spec.ts` 与 `DatasetEdit.spec.ts` 回归测试，UI 全量为 `10 files / 38 tests passed`；Chrome CDP 已确认新建/编辑入口可见，表项行为由单测覆盖。

**2026-09-14 空表目录闭环修复：** JDBC 数据源尚未完成 Schema 探测时，页面原先只显示空态，用户无法知道如何继续。现增加“刷新表目录”入口，调用 `schema-discovery` 后重新加载表列表，并在失败时显示可理解的错误提示；`DatasetEdit.spec.ts` 新增回归。Chrome CDP 在模拟 JDBC 数据源返回 102 张表后确认表项可见，Enter/Space 实际切换 `aria-checked`。

**2026-09-15 数据源连接表单语义修复：** Chrome CDP 发现原生端口、密码和复选框控件存在空 AX 名称，且复选框隐藏规则曾引入 Vite CSS 500。现为输入、选择器和 5 个复选框补充显式 `aria-label`，使用保留语义的透明控件并修正括号；定向 E2E `1 passed`，完整 Chrome 矩阵 `25 passed`，CDP 复验 AX 空名称数为 `0`。

**2026-09-14 空态插图主题修复：** 新建数据集空态 SVG 原先保留固定浅色 `fill/stroke` 属性，暗色主题存在视觉回退风险；现移除固定颜色，统一由 `.illustration-*` 的 `--theme-*` 令牌控制。Chrome CDP 暗色主题复验卡片、线条和勾选描边均使用当前主题颜色。

**2026-09-14 表项焦点可见性补充：** 为可键盘操作的 JDBC 表项增加 `:focus-visible` 主题化 2px 轮廓和偏移，避免深色主题下焦点不可辨识；定向单测与构建通过，Chrome CDP 实测焦点轮廓已生效。

**2026-09-14 键盘审计补充：** Chrome CDP 对 `/datasets/new` 和双源编辑器的所有当前可聚焦 `button/input/select/textarea/[tabindex]` 控件执行名称检查，结果分别为 `5/5`、`52/52` 均存在文本、`aria-label`、`title` 或 `id`。这关闭了本地模拟范围内“无名称控件”缺口；四主题对比度仍需设计专项工具验证。

- MGMT-C01～MGMT-UI05 全部通过且无跳过，UI production build 成功。
- JDBC、Aloudata 指标视图、HTTP/API、文件都能通过真实后端创建数据集并查看 Descriptor/受限预览。
- 类型化 DTO 是唯一新入口；旧 JDBC 请求仅通过明确兼容映射保留。
- 无权限来源不出现在选择器中，即使伪造 ID 提交也被服务端拒绝。

## 视觉验收（CDP）

使用 CDP 连接当前候选 SHA 的真实管理 UI，执行测试矩阵中的 VIS-UI01～VIS-UI02。最低使用 `Page.captureScreenshot`、`Runtime.evaluate` 和 `Accessibility.getFullAXTree`，并保存每个场景的页面截图和操作证据。

- 切换 JDBC、Aloudata、HTTP/API、文件时，字段显隐、来源选择器和错误布局与当前来源一致；非 JDBC 不出现 SQL 输入。
- 编辑已有连接时认证输入为空；文件和 HTTP/API 页面不显示本地路径、签名 URL、Header 或凭据。
- 数据集选择、Descriptor 和受限预览覆盖空态、加载态、成功态、失败态；字段名称、类型和错误提示可读且不发生布局溢出。
- 视觉证据必须记录 `candidateSha、url、viewport、browser、timestamp、cdpActions、screenshotPath`，并能回溯到对应 UI 用例。

预期：VIS-UI01～VIS-UI02 全部 PASS；截图来自当前候选 SHA 的真实页面，且页面文本/DOM 不包含敏感值。

**本轮实测结果（2026-09-13）：** `VIS-UI01` 当前本地模拟通过；`VIS-UI02` 失败。文件、HTTP 和 JDBC 数据集的 Descriptor 均显示 `0 个字段`，但输入预览已有字段和数据；原始 JSON 预览在窄侧栏中发生横向截断。复现步骤和截图见统一验收记录。

### 本轮逐用例验收记录（2026-09-13）

验收环境：当前工作树 HEAD `0c1b066f6f5059fcf8930294c111707e8d39c91f`、本地模拟 E2E 服务、工作区 `1`、Google Chrome viewport `1440x736`。持久化 CDP 截图位于 `/tmp/mateclaw-dashboard-cdp-current`，编辑器截图为 `dashboard-editor.png`。

| 用例 | 结果 | 页面操作与实际结果 |
| --- | --- | --- |
| VIS-UI01 | `PASS`（本地模拟） | 进入 `洞察`→编辑 `E2E JDBC + Aloudata Dashboard`，打开数据集选择器，确认可选 `E2E JDBC Orders Dataset`、`E2E Aloudata Metrics Dataset`、`E2E HTTP Orders Dataset`、`E2E File Orders Dataset`；切换至文件/HTTP 后页面不出现 SQL。进入 `配置`→`数据配置` 编辑已有 Aloudata 连接，认证值为空且未显示旧认证值。 |
| VIS-UI02 | `PASS（本地模拟，修复后）` | Descriptor 空 Schema 探测期间显示“字段探测中…”，受控输入预览完成后补齐字段；字段和预览使用结构化表格。空输入补充场景显示“每个数据集输入都必须选择数据集”，未发起无效执行。 |

**当前工作树复验补充（2026-09-14）：** 当前实现的静态/组件验证已覆盖 Descriptor 自动补齐和结构化输入预览；非交互 CDP 脚本在 `/tmp/mateclaw-dashboard-cdp-20260914/` 生成编辑器截图。用户 Chrome `9222` 可通过 CDP 打开到洞察列表，但 CUA 交互通道连续返回 `Unable to load browser request-header policy`，本轮未能完成 CUA 点击式 VIS-UI01～VIS-UI02；因此不新增未经交互确认的 PASS。真实 E2E 的当前阻塞是 09 计划记录的双源视觉快照差异，不是本子计划 Descriptor 单测失败。

**提交后回归补充（2026-09-14）：** 提交 `00c772d8c315b941b2e0171fd26f53fd12b19416` 上 UI 全量为 `9 files / 35 tests passed`，production build 与设计门禁均通过。该结果只证明自动化回归，不替代 CUA 交互截图；当前 CUA 请求头策略错误仍待服务恢复。

**来源兼容约束补充（2026-09-14）：** 数据源选择与数据集来源类型已建立前端兼容校验：Aloudata 只显示/允许指标视图，JDBC 才允许 JDBC 表和 JDBC SQL；切换数据源时自动回到兼容来源，保存前再次阻断非法组合。`DatasetEdit.spec.ts` 新增 Aloudata + JDBC SQL 回归，UI 全量回归更新为 `9 files / 36 tests passed`。

**主题令牌修复补充（2026-09-14）：** 发现数据集新建空态的背景、标题、描述和链接仍使用固定浅色值，dark/warm/eye-care 主题下视觉不一致。现已改为 `--theme-surface`、`--theme-text`、`--theme-text-secondary` 和 `--main-orange`；新增四主题真实 E2E，先复现失败再验证通过。Chrome CDP 已采集四张当前页面截图，dark 主题现场可见且无布局溢出。

**预览主题令牌补充（2026-09-14）：** 继续发现数据集预览 `.toolbar`、`.toolbar-btn`、`.data-preview` 和 `.preview-empty` 固定使用白色/浅灰色。现已统一使用主题 surface、border、secondary text 和主操作色；新增真实 E2E 覆盖列表入口进入文件数据集预览并逐项比较四主题计算样式，修复前失败、修复后通过。

**表格主题令牌补充（2026-09-14）：** 继续收敛数据集表格、字段大纲、分页和编辑态中的固定颜色，统一替换为主题 surface、border、text、muted 和主操作令牌。完整 UI 单测 `37 passed`、生产构建成功；本地模拟真实 E2E（含双源、错误/兼容、正式创建绑定和两项四主题检查）`14 passed`。

**空态插画主题补充（2026-09-14）：** 空态 SVG 的卡片、占位线和勾选图标原先也使用固定浅色值；现已增加语义类并改用主题 surface、border 和主操作令牌。四主题真实 E2E 增加 SVG fill/stroke 校验，最新完整 E2E `14 passed`，仍保留其他历史页面专项审计为后续工作。

服务端 `DatasetManageServiceImpl` 已增加同等校验，覆盖绕过 UI 直接提交创建/更新请求的场景；`DatasetCatalogServiceTest` 新增 JDBC/Aloudata 交叉绑定拒绝用例。

另新增 `e2e/dataset-management-entry.spec.ts` 的文件数据集创建用例，覆盖上传、创建/同步和进入预览；首次执行因本地缺少 Playwright 浏览器二进制未运行，随后已切换系统 Chrome channel 完成执行。

2026-09-14 已使用系统 Chrome channel 和真实 DataAgent 执行该用例，结果 `2 passed`；创建后的文件预览实际显示 CSV 行数据（含 `120.5`、`east`）。

当前提交的 CDP 视觉复验已选择模拟 Aloudata 数据源，确认来源类型自动切换为指标视图且 JDBC 表/SQL 选项不可选；截图见 `/tmp/mateclaw-cdp-aloudata-compatibility.png`。

#### VIS-UI02 问题复现

1. 启动本地模拟服务并使用工作区 `1` 的 `admin` 登录。
2. 打开 `洞察`，编辑 `E2E JDBC + Aloudata Dashboard`。
3. 在“脚本数据集输入”中选择 `E2E File Orders Dataset` 或 `E2E HTTP Orders Dataset`，点击“查看字段”，确认仍为 `0 个字段`。
4. 点击“输入预览”，确认预览返回包含 `id`、`order_date`、`region`、`status`、`amount` 的行。
5. 保持 Chrome viewport `1440x736`，观察预览区域：原始 JSON 在右侧栏中被截断并出现横向滚动。

预期：Descriptor 字段名、类型和数量与输入预览一致，且预览内容在面板内可读、不发生布局溢出。当前实现已在空 Schema 中间态显示“字段探测中…”并由受控预览补齐字段；统一证据见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md` 的“Descriptor 空 Schema 中间态修复与 Chrome 复验”。

### 可访问名称修复（2026-09-14）

数据集配置和预览页的关键原生输入已补充显式 `id/for` 或 `aria-label`：数据集名称、数据源、来源类型、JDBC SQL、指标视图、HTTP/API 定义、文件对象/格式、搜索字段、全选/行选择、单元格编辑和刷新预览。该修复只增强 AX/键盘语义，不改变数据集契约和执行逻辑。

### 2026-09-14 洞察列表状态标签主题适配

洞察列表的成功/草稿状态标签已从固定浅色值改为主题语义令牌；暗色主题使用不透明深色状态底色，修复标签与页面主题不一致及对比度不足。新增四主题成功/草稿标签回归用例，系统 Chrome channel `1 passed`；Google Chrome CDP `9222` 实测暗色标签对比度 `8.24:1`，截图 `/tmp/mateclaw-cdp-insight-list-status-dark-fixed.png`。

同一回归还覆盖卡片“撤回/删除”操作按钮：移除固定橙/红颜色，改为主题操作令牌；原删除按钮在浅色卡片上的对比度 `3.76:1`，修复后四主题均达到 WCAG AA。

### 2026-09-14 洞察列表筛选与视图切换状态语义

状态筛选和网格/列表切换按钮新增动态 `aria-pressed`，真实点击切换回归通过；Chrome CDP 现场确认“草稿”和“列表视图”状态正确暴露，截图 `/tmp/mateclaw-cdp-insight-filter-aria-dark.png`。

### 2026-09-14 AI 助手关闭按钮可访问名称

AI 助手面板关闭按钮新增 `aria-label="关闭 AI 助手"`；系统 Chrome channel 回归和 CDP 实际打开/关闭操作均通过，截图 `/tmp/mateclaw-cdp-ai-close-button.png`。

### 2026-09-14 页面树操作按钮可访问名称

编辑器页面树更多操作按钮新增 `aria-label="页面操作"`；真实编辑入口回归和 Chrome CDP 现场检查通过，截图 `/tmp/mateclaw-cdp-page-actions-button.png`。

### 2026-09-14 当前页面键盘遍历补充

用户 Chrome `9222` 对 `/datasets/new` 和 `/?nav=insight` 各执行最多 80 次 `Tab`，可见焦点控件均具备名称（两页 `missingCount=0`）；该证据覆盖当前数据集入口和洞察编辑器，不替代全站及跨浏览器专项审计。

### 2026-09-14 页面树新增按钮可访问名称

页面树原先的 `+` 按钮仅以符号作为名称，现增加 `aria-label="新增页面"`；页面树更多操作仍为“页面操作”。定向系统 Chrome E2E `1 passed`，CDP 现场确认两个名称均可读，截图 `/tmp/mateclaw-cdp-page-add-a11y.png`。

### 2026-09-14 数据源配置关闭控件可访问名称

数据源配置页右上角关闭控件由不可聚焦的 `span` 改为语义化 `button`，增加 `aria-label="关闭数据源配置"`；主题 E2E、UI 全量 `42/42`、构建和 Chrome CDP 现场复验通过，截图 `/tmp/mateclaw-cdp-datasource-close-a11y.png`。

### 2026-09-14 文件预览字段分组键盘操作

文件预览的维度/度量分组由鼠标专用 `div` 改为可聚焦按钮，支持 Enter/Space、`aria-expanded` 和动态分组名称；文件预览 E2E、UI 全量 `42/42`、构建和 Chrome CDP 现场切换复验通过，截图 `/tmp/mateclaw-cdp-field-group-keyboard.png`。

### 2026-09-14 文件预览字段可见性按钮语义

字段行显示/隐藏控件由点击专用 `span` 改为原生 `button type="button"`，补充按状态变化的“显示列/隐藏列” `aria-label`，并以按钮重置样式保持现有视觉。定向文件预览 E2E `1 passed (4.5s)`、UI 全量 `42/42`、构建、`DESIGN-PASS` 通过；当前模拟 fixture 无字段行，真实字段按钮的点击/截图验收列为后续联调项。

### 2026-09-14 图表组件内部 Tab 键盘语义

该项属于运行时组件的可访问性补齐：图表多 Tab 由点击专用 `div` 改为 `tablist/tab`，提供选中状态、roving `tabindex`、Enter/Space 激活及方向键/Home/End 导航。新增 `ChartWidget.spec.ts` 后 UI 全量为 `11 files / 43 tests passed`，构建和 `DESIGN-PASS` 通过；当前模拟看板没有多 Tab 图表，现场 CDP 交互待真实多 Tab fixture 补验。

### 2026-09-14 指标/维度类目树键盘语义

Aloudata 指标/维度类目树节点改为可聚焦 `treeitem`，补充 `aria-selected` 和计数名称；有子节点时，展开控件提供 `role="button"`、`aria-expanded`、动态名称并支持 Enter/Space。新增 `CategoryTreeNode.spec.ts` 后 UI 全量为 `12 files / 44 tests passed`，构建和 `DESIGN-PASS` 通过；真实指标平台页面的 CDP 焦点截图待联调 fixture。

首次 CDP 点击本地模拟数据源“同步元数据”曾返回“未定义的 API 端点: metric_list”，不能用空态替代现场树节点验收。

**模拟端点补齐（2026-09-14）：** 已为本地 WireMock 增加元数据同步所需六个端点，并让 DataAgent 在数据库端点配置缺项时使用核心默认端点；`AloudataEndpointServiceTest` 通过。重建服务后 Chrome CDP 已实际生成“销售，1 个指标”树节点，聚焦按 Enter 后选中状态正确切换，截图 `/tmp/mateclaw-cdp-metric-tree-keyboard-pass.png`。

**同步后面板刷新（2026-09-14）：** 修复父页面同步成功后未递增 `panelRefreshKey` 的问题；Chrome CDP 实际确认同步前后指标/维度类目树自动刷新，指标和维度数据可见，截图 `/tmp/mateclaw-cdp-sync-refresh-panel.png`。

**类目树 roving tabindex（2026-09-14）：** 当前选中节点为 `tabindex=0`，其余节点为 `-1`，避免 Tab 重复遍历；Chrome CDP 已读取两棵真实模拟类目树并按 Enter 验证选中状态，截图 `/tmp/mateclaw-cdp-tree-roving-tabindex.png`。

### 2026-09-14 多页面 Tab 可访问语义

预览页顶级/子页面导航新增 `tablist/tab`、`aria-selected` 和 roving `tabindex`；临时双页面看板的真实 Chrome channel 回归及 CDP 现场检查通过，截图 `/tmp/mateclaw-cdp-multipage-tabs.png`。

补充实现方向键（左右/上下、Home/End）切换页面并将焦点移动到新 Tab；Chrome CDP `ArrowRight` 现场验收通过，截图 `/tmp/mateclaw-cdp-multipage-tabs-arrow.png`。

### 2026-09-14 维度类目持久化与统计修复

维度同步完整保存类目 ID/名称、编码和显示状态，类目统计使用完整实体查询。Chrome CDP 实测维度树显示“时间与区域，2 个维度”及“日期/区域”字段；截图 `/tmp/mateclaw-cdp-dimension-tree-fixed-final.png`。
### FE-CLOSE-09 可访问性对比度（2026-09-14）

- [x] 数据集列表辅助文字与状态标签改用主题语义令牌，不再使用固定颜色。
- [x] 四主题通过 Chrome CDP 实测 WCAG AA 对比度（辅助文字最低 4.97:1，状态标签最低 6.24:1）。
- [x] 使用项目配置的系统 Chrome channel 补跑自动化对比度用例（`1 passed`）；Playwright 自带 Chromium 在当前 macOS ARM 架构不支持，标准 E2E 镜像仍可另行使用。
**2026-09-14 来源类型边界修复：** `sourceType=api` 的数据源原先被“非 Aloudata 即 JDBC”逻辑误判，导致 JDBC 表/SQL 选项错误开放。现显式归一化 `api/http/http_api`，自动切换 `HTTP_API` 并禁用 JDBC 表/SQL；兼容历史响应缺少 `sourceType` 的数据源。新增回归测试，Chrome CDP 已验证 API 数据源选项状态。

**2026-09-14 后端来源边界补强：** 服务端新增 HTTP/API 数据集只能绑定 `api/http/http_api` 数据源的校验，防止绕过前端直接用 JDBC 连接创建 HTTP/API 数据集；`DatasetCatalogServiceTest` 已覆盖拒绝路径。

**2026-09-14 非 JDBC 表目录误导修复：** HTTP/API、Aloudata 和文件来源不再显示无效的“刷新表目录”按钮；仅 `JDBC_TABLE` 模式展示表目录、空态和 Schema 探测入口，其他来源显示“当前来源类型不使用 JDBC 表目录”。新增 `DatasetEdit.spec.ts` 回归，Chrome CDP 已验证。

**2026-09-14 JDBC 类型兼容补强：** 前端来源判断补齐 `oracle`、`snowflake`、`bigquery`、`redshift`、`clickhouse`、`doris` 等后端支持的 JDBC 类型，避免合法数据源被降级为文件来源；新增 Oracle 回归测试。
**2026-09-14 服务端反向边界补强：** DataAgent 同时拒绝 JDBC 数据集绑定 `api/http/http_api` 等非 JDBC 数据源，防止绕过前端提交不兼容来源；新增 `DatasetCatalogServiceTest` 拒绝路径。
**2026-09-14 JDBC 反向绑定补充：** 服务端新增 JDBC 数据集绑定非 JDBC 数据源的通用拒绝校验（不仅限于 Aloudata），与前端来源类型限制保持一致；DataAgent 全量门禁通过。
