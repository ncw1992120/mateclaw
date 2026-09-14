# 数据源与数据集管理入口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 让用户能在现有 DataAgent 管理界面中配置 JDBC、Aloudata、HTTP/API 和文件来源，并把来源固化为可复用数据集。

**Architecture:** 复用现有 `DataAgentDatasourceController`、`DataAgentDatasetController`、`DatasourceForm.vue` 和 `DatasetEdit.vue`，以类型化 DTO 扩展而不是继续堆叠无结构 `connectionParams/sourceConfig`。凭据只写入后端加密配置，数据集响应只返回脱敏定义和统一 Descriptor。

**Tech Stack:** Java 21/Spring Boot、Jakarta Validation、Vue 3/TypeScript、Vitest、Vue Test Utils。

**Spec:** `docs/策略解读/design.md` 第 3、4、7 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 9 节（MGMT-C01～MGMT-UI05）。

**当前状态（2026-09-13）：** 四类来源管理入口、来源定义校验、权限矩阵和候选提交上的 UI `24/24` 测试已完成，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`；当前工作树追加五种来源选项、HTTP/API 仅引用已登记定义及 Aloudata 只读视图回归后为 `27/27`。

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
| VIS-UI02 | `FAIL` | 依次选择文件、HTTP、JDBC 数据集并点击“查看字段”，均显示 `0 个字段`；随后点击“输入预览”，却能返回含 `id/order_date/region/status/amount` 的有效行。原始 JSON 放在窄侧栏中，字段内容横向溢出，需要滚动，字段和类型不易阅读。空输入补充场景显示“每个数据集输入都必须选择数据集”，未发起无效执行，前置校验子项通过。 |

**当前工作树复验补充（2026-09-14）：** 当前实现的静态/组件验证已覆盖 Descriptor 自动补齐和结构化输入预览；非交互 CDP 脚本在 `/tmp/mateclaw-dashboard-cdp-20260914/` 生成编辑器截图。用户 Chrome `9222` 可通过 CDP 打开到洞察列表，但 CUA 交互通道连续返回 `Unable to load browser request-header policy`，本轮未能完成 CUA 点击式 VIS-UI01～VIS-UI02；因此不新增未经交互确认的 PASS。真实 E2E 的当前阻塞是 09 计划记录的双源视觉快照差异，不是本子计划 Descriptor 单测失败。

#### VIS-UI02 问题复现

1. 启动本地模拟服务并使用工作区 `1` 的 `admin` 登录。
2. 打开 `洞察`，编辑 `E2E JDBC + Aloudata Dashboard`。
3. 在“脚本数据集输入”中选择 `E2E File Orders Dataset` 或 `E2E HTTP Orders Dataset`，点击“查看字段”，确认仍为 `0 个字段`。
4. 点击“输入预览”，确认预览返回包含 `id`、`order_date`、`region`、`status`、`amount` 的行。
5. 保持 Chrome viewport `1440x736`，观察预览区域：原始 JSON 在右侧栏中被截断并出现横向滚动。

预期：Descriptor 字段名、类型和数量与输入预览一致，且预览内容在面板内可读、不发生布局溢出。实际：Descriptor 与预览不一致，预览布局不可读。该问题保持 `FAIL`，统一证据见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md` 的“本轮 Google Chrome CDP 视觉验收”。
