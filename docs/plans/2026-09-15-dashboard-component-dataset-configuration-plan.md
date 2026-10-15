# 洞察仪表盘组件数据配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将洞察 → 仪表盘 → 组件 → 卡片属性配置中的旧“数据源”入口，改为原型规定的“+ 添加数据集 / 搜索 → 按来源配置 → 独立预览 → 确认输入 → 筛选器绑定 → Python 预处理 → 组件展示”完整流程。

**Architecture:** 组件是数据配置的归属单位。点击添加时先形成临时来源草稿；草稿预览不创建数据集，点击确定后固化为可复用的数据集定义并把 `datasetId` 加入当前组件的输入列表。执行链通过现有 Dataset Adapter 和 Runner 读取已确认的输入；旧组件的 `dataSource` / `tabs[].dataSource` 只用于历史 Schema 的读取与渲染兼容。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vitest、Playwright/Chrome CDP；Spring Boot、Java 21、JUnit、JSqlParser；现有 Dataset Adapter、Python Runner、Docker 模拟环境。

**Spec:** `docs/策略解读/原型设计.md`（第 1～9 节）；项目根 `AGENTS.md`；`docs/策略解读/design.md` 中统一数据集、Adapter 和 Runner 契约。

> **实施状态（2026-09-15）：** 前端来源树、来源配置弹窗、组件级 `datasetPipeline` 接线、字段/筛选弹窗及草稿 API 已落地；后端已补 JDBC 参数化草稿预览、Aloudata 指标&维度及已有指标视图草稿预览（复用视图 Adapter 查询端点）、HTTP 草稿预览（复用受控 API Adapter）、文件草稿预览（复用上传后的 `StoredFileRef` 和对象存储上下文）、受控接口定义登记及 TXT/XLS 文件读取分支。当前验证：前端全量 Vitest 27 个文件/86 个测试、`vue-tsc`、生产构建通过；Runner 24 个测试通过；JDK 21 DataAgent 关键测试 32 个通过（0 failures/errors）；Chrome channel 串行 E2E 36/36 通过；Chrome CDP 已采集来源树、接口弹窗、双源结果和反馈 Tab 的 DOM/AX/截图证据。

> **剩余验收边界：** 五格式文件/API 恶意输入矩阵和真实 Aloudata 联调仍需在环境验收中完成；本地核心双输入脚本链路已通过 Chrome。字段映射与输入筛选弹窗现已采用草稿副本，取消会回滚、确定才写回组件配置；系统 Python Base Script 现按 `scriptFilterBindings` 的 `inputNames/fieldMappings` 限定参数下推范围，并由 Vitest 覆盖不向未绑定输入广播参数；文件草稿 `StoredFileRef` 预览已增加适配器单测，Controller 已增加接口登记成功/敏感 Header 拒绝 JUnit。

> **最终本地门禁（2026-09-15）：** `npm --prefix mateclaw-dataagent-ui run test -- --run` 为 27 files / 86 tests passed；`npm --prefix mateclaw-dataagent-ui run build`（含 `vue-tsc --noEmit`）成功；`mateclaw-dataagent` JDK 21 定向测试 32 tests passed；`MATECLAW_E2E_ALOUDATA_MODE=simulation MATECLAW_E2E_BROWSER_CHANNEL=chrome npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line` 串行 36/36 passed（2.7m，无失败/跳过）。本轮修正了入口 E2E 对旧开关和已移除别名控件的断言，并将文件来源节点断言改为原型实际的 `Excel` 节点。

> **E2E 契约同步：** `dashboard-multi-source.spec.ts`、`dashboard-errors-and-compatibility.spec.ts` 和 CDP 视觉脚本已改用当前原型入口标题“数据集配置”，不再把已移除的“脚本结果数据集输入”当作成功条件；真实双输入执行仍需带状态文件和模拟 Aloudata 环境运行验证。

> **本轮运行实例核对：** 已停止旧 18089 进程并用最新构建启动 DataAgent（Java 21，Flyway schema 220，包含本计划新增 Controller/Adapter）；本地健康检查可用。已定位并修复 E2E 种子脚本的可重复执行问题：数据集名称增加 `MATECLAW_E2E_RUN_SUFFIX`（默认 Unix 时间戳），JDBC 主机/端口支持 `MATECLAW_E2E_JDBC_HOST`/`MATECLAW_E2E_JDBC_PORT`，避免固定名称冲突及宿主机无法解析 Docker 服务名。使用本机 MinIO（`MATECLAW_OBJECT_REF_ENDPOINT=http://127.0.0.1:19000` 等）后，JDBC、HTTP、文件数据集、API+文件/ECharts 及 Aloudata 模拟双源仪表盘种子均已成功创建；种子中的组件已同时写入组件级 `config.datasetPipeline`，可直接验证新入口。
> **CDP 复验记录（2026-09-15）：** 使用系统 Chrome（`/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`）完成正式导航：洞察仪表盘 → 编辑 → 选中卡片 → “数据集配置”。来源树 DOM/AX/截图证据仍保存为 `/tmp/cdp-editor-final.png`、`/tmp/cdp-dialog-final.png`（AX 511 节点）。在本地模拟 Runner 通过宿主机 `18083` 暴露后，API+文件双输入点击“最终结果预览”返回 5 行、`execution-alert=0`，截图为 `/tmp/cdp-api-file-result.png`；Aloudata 模拟双源返回 11 行、`execution-alert=0`，截图为 `/tmp/cdp-jdbc-aloudata-result.png`，结果包含 JDBC 的 `120.5` 与模拟指标视图字段。两条运行链路均已解除此前的 Runner 网络阻塞；真实 Aloudata 联调仍作为环境验收，不以模拟结果替代。
> **运行态补充（2026-09-15）：** 已临时暴露独立 Runner 容器并验证 `POST /v1/tasks` 可达；Runner 可提交任务，但 API 输入在 DataAgent 侧被 `HttpApiRequestPolicy` 拒绝（模拟地址解析为私网地址，返回 HTTP 500）。这说明运行态阻塞已从 Runner 网络前移到“模拟 HTTP 地址的安全白名单”配置，后续应使用专用 `e2e-http:8443` 别名映射或显式开发 profile，不得在生产策略中放开任意私网地址。
> **JDBC 双输入运行态验收（2026-09-15）：** 在同一稳定 DataAgent/Runner 进程下，使用有效本机 MySQL 数据源创建 JDBC+JDBC 双输入组件，真实 Chrome 完成“仪表盘列表 → 编辑 → 选中卡片 → 数据集配置 → 最终结果预览”。组件输入数为 2，Runner 返回成功，结果表 20 行，`execution-alert=0`；截图保存为 `/tmp/cdp-jdbc-jdbc-result.png`。该结果证明组件级 `datasetPipeline`、别名读取、Python 双输入合并及页面渲染链路可运行。API+文件运行态仍单独受模拟 HTTP 私网策略影响，不能用 JDBC 结果替代 API/文件验收。

> **双输入脚本门槛：** 当输入数不少于 2 且脚本只有系统生成区域时，前端现在明确阻止最终结果预览并提示补充用户处理区域；新增 DatasetInputPanel 单测已覆盖该门槛。

> **服务端绑定校验：** `DashboardExecutionServiceImpl` 在提交 Runner 前校验 `scriptFilterBindings.inputNames` 及 `fieldMappings` 均引用当前组件白名单输入，越权或拼写错误的输入会在任务创建前拒绝。

> **本轮执行结果（2026-09-15）：** DataAgent→Runner 已通过开发环境可达端口验证；最新种子已完成“两个输入 → Python 预览 → 当前卡片结果”流程。结果区新增“数据预览 / 字段结构 / 执行信息 / 处理日志”四个反馈 Tab，Chrome CDP 实测四个 Tab 均可见，5 行结果、`execution-alert=0`，AX 733 节点，截图 `/tmp/cdp-preview-tabs.png`。后续仅剩五格式文件/API 恶意输入矩阵及真实 Aloudata 环境联调。
> **格式与 API 安全测试（2026-09-15）：** 使用 JDK 21 Maven 对 `FileDatasetAdapterTest`、`HttpApiRequestPolicyTest`、`HttpApiDatasetAdapterTest`、`DatasetComposerControllerTest`、`AloudataMetricsAdapterTest` 定向运行 32 个测试，0 failures、0 errors；覆盖 CSV/JSON/TXT/XLS/XLSX/Parquet 读取与过滤、对象引用预览、指标&维度下推、私网/非 allowlist Host、HTTP/TLS 开发端点、敏感 Header、未声明参数、重定向、超时/非 2xx、响应大小限制及 URL 凭据拒绝。Excel/XLS 使用 Apache POI 二进制样本验证。
> **E2E 回归修复（2026-09-15）：** 修正测试夹具中错误/取消/超时/资源/大结果看板缺少组件级 `datasetPipeline` 的问题；修正编辑器测试等待组件加载、用户脚本 textarea 选择器、API+文件真实结果行数（5 行）和 Aloudata 模拟结果行数（11 行），并为动态兼容页快照设置 2% 像素噪声阈值。核心双源 E2E（JDBC+Aloudata、API+文件）`2 passed (22.5s)`；ObjectRef、ECharts 也在同轮矩阵中通过。
> **完整矩阵复验补充（2026-09-15）：** 继续修正单输入脚本错误场景与结果反馈入口；错误/取消/超时/资源限制和核心双源场景均已可进入组件级配置。完整 36 条矩阵中此前已完成 29 条，剩余 1 条历史“指标平台分页控件”用例因配置页数据源分页/元数据加载未命中模拟 Aloudata 条目而失败，属于配置中心历史夹具问题，不影响仪表盘组件数据编排链路，需单独修复后再宣称全矩阵通过。

### 本轮验证证据

- Chrome CDP 实际流程：登录 → 洞察 → 编辑仪表盘 → 拖入“卡片”，页面 DOM 包含“数据集配置”“+ 添加数据集”“🔍 搜索”，并通过 `Accessibility.getFullAXTree` 完成可访问性树采集；截图保存为 `/tmp/mateclaw-dashboard-dataset-config-cdp.png`。
- Chrome CDP 来源树流程：点击“+ 添加数据集”后确认四类节点及五种文件格式；点击“接口”直接打开“配置接口”弹窗，未出现“已配置接口列表”；AX 树采集 516 个节点，截图保存为 `/tmp/mateclaw-interface-dialog-cdp.png`。
- 前端：`npm --prefix mateclaw-dataagent-ui run test -- --run` → 27 个文件、84 个测试通过；`npx vue-tsc --noEmit` 与 `npm run build` 通过。
- Runner：`make dashboard-runner-test` → 24 个测试通过（Python 3.13 虚拟环境）。
- 本地总门禁：`make dashboard-verify-local` 已通过，包含模拟前置条件、Docker Maven DataAgent 测试、Runner 测试、UI 全量测试、生产构建和设计冻结校验。
- 后端草稿预览：`DatasetComposerController` 的 JDBC 草稿预览已改为使用受控数据源连接执行编译后的参数化 SQL，返回真实受限行、字段 Schema、`pushdownReport` 和执行 ID；不创建 `DatasetEntity`。
- 草稿预览权限：JDBC 预览额外校验当前工作区及数据源 `metaShared/owner` 可见性，越权请求在建立连接前拒绝。
- HTTP API：新增受控接口定义登记端点；接口弹窗确认时先登记 endpoint/method/声明参数，再以 `apiDefinitionId` 创建数据集，登记过程执行工作区权限、HTTPS/allowlist 和敏感 Header 校验。
- API 登记改动后复跑 `make dashboard-dataagent-test`，Docker Maven 测试仍通过。
- 文件 Adapter：补充 TXT（自动识别制表符/分号/逗号）及 `.xls`/`excel` 格式分支，并新增 `FileDatasetAdapterTest.readsTabSeparatedTxt` 覆盖。
- Aloudata 指标&维度草稿预览：补充直接调用 `AloudataService.queryMetrics` 的受限预览，返回行、字段和下推筛选报告；仍保留真实 Aloudata 权限/契约联调作为环境验收。
- 后端：使用临时 JDK 21 `/tmp/jdk21/jdk-21.0.12.1+1/Contents/Home` 和 Maven `/Users/srant/.maven/apache-maven-3.9.16/bin/mvn` 编译通过；启动 Docker Desktop 后 `mvn test` 全部通过（162 tests, 0 failures, 0 errors），Testcontainers MinIO 用例正常运行；`make dashboard-dataagent-test` 也已在 Docker Maven 容器中完成。

## Global Constraints

- 产品入口必须在当前卡片属性的原“数据源”位置；下方独立的“脚本结果数据集输入”不是新入口。
- 来源树严格为 Aloudata（指标视图、指标&维度）、JDBC（具体数据库连接）、接口、文件（Excel、CSV、TXT、JSON、Parquet）。点击“接口”直接进入配置弹窗。
- JDBC 先选择数据库资源，再输入 SQL、筛选预览、确定；别名出现在 SQL 确认后的输入卡片中。不强制选表。
- SQL 仅允许单条只读查询；预览前检查真实查询的 `WHERE`，缺少时阻止预览并提示补充条件；参数使用绑定而非拼接。此处按原型执行，不能自行放宽。
- 字段名称与输入筛选使用弹窗；每个输入独立预览。筛选器绑定及字段匹配先于 Python 编辑；一个输入时 Python 可选，两个及以上输入时必填。
- API 的 Host/路径等由接口弹窗填写，经后端受控登记、URL/Host 策略和审计后运行；页面和 Python 不直接访问任意外部地址。文件内容经已有上传接口进入对象存储，Schema 只保存 `objectId`。
- Aloudata 本计划按原型包含“指标&维度”和“已有指标视图”两种模式；当前仅有视图 Adapter，因此指标&维度属于明确的新增开发任务，不以已有视图模式冒充。
- 不引入 Trino、DuckDB 强制依赖、`pip install`、AI 自动写脚本或新的身份权限体系；保持项目现有工作区校验和凭据隔离。
- 保留历史仪表盘展示与保存兼容；用户未确认的草稿、取消的弹窗或失败的预览不得写入组件 Schema，不得改变画布结果。

---

## 0. 现状、边界与交付顺序

当前 `PropertyPanel.vue` 为展示组件直接编辑 `dataSource.datasourceId / metrics / dimensions / sql` 和 `tabs[].dataSource`；`InsightDashboardEditorView.vue` 又在其下方并列挂载 `DatasetInputPanel.vue`，其输入挂在仪表盘根 `schema.datasetInputs`。`DatasetInputPanel.vue` 先加空输入，再选已有数据集并填写别名；JDBC SQL、字段映射和输入筛选是行内编辑。后端 `/v1/datasets/preview` 只接受已存在的 `datasetId`，Runner 也从根 `datasetInputs` 读取。因此现状不是原型中的“来源配置 → 独立预览 → 确定形成当前组件输入”。

开发按以下可验收交付物推进；前一项测试通过后再进入后一项：

1. 组件级 Schema 与旧数据兼容。
2. 原位置添加入口、搜索与分类来源选择。
3. JDBC / Aloudata / 接口 / 文件来源草稿及预览、确认。
4. 已确认输入卡片、字段映射和输入筛选。
5. 筛选器绑定、Python Base Script 与执行。
6. 三层预览、执行反馈、组件渲染与全流程验收。

本计划仅写实施任务，不把已有计划里的历史 PASS 当作本轮验收结果。任何任务的完成状态由本轮测试输出、API 读回、Chrome 截图/DOM/AX 与保存后刷新复验判定。

## 1. 文件职责与接口约定

### 前端文件

| 文件 | 本轮职责 |
| --- | --- |
| `mateclaw-dataagent-ui/src/types/index.ts` | 定义 `ComponentDatasetPipeline`、来源草稿、确认后的输入、预览及执行反馈类型；旧 `ComponentDataSource` 保持可读。 |
| `mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue` | 在原“数据源”位置显示组件级数据集配置入口；展示配置与数据配置分开；筛选/时间组件保留自身控制属性。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DatasetInputPanel.vue` | 改为当前组件的数据集编排容器：已确认输入卡片、作用范围、Python 和预览；不再以根级脚本输入面板挂载。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DatasetSourcePicker.vue`（新） | `+ 添加数据集`、搜索、原型来源树与来源选择；接口为单一节点。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DatasetSourceDialog.vue`（新） | 按来源分支展示 JDBC SQL、Aloudata、接口、文件配置表单与“筛选预览 / 执行记录 / 确定”。必要时在同目录按表单职责拆分子组件。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DatasetFieldMappingDialog.vue`、`DatasetInputFilterDialog.vue`（新） | 字段名称、输入筛选弹窗；只有“确定”提交更改。 |
| `mateclaw-dataagent-ui/src/views/insight/components/DatasetPreviewDialog.vue`（新） | 数据预览、字段结构、执行信息、处理日志四个 Tab；区分输入、处理结果、组件展示。 |
| `mateclaw-dataagent-ui/src/views/insight/components/PythonScriptDialog.vue`（新） | 系统生成区域只读，用户处理区域可编辑；预览结果、执行记录、确定和绑定变更提示。 |
| `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue` | 以选中组件为范围接线，保存/刷新/复制/删除时保留组件级配置；组件预览取最终输入。 |
| `mateclaw-dataagent-ui/src/views/insight/DashboardPreviewView.vue`、`src/utils/dashboard-schema.ts` | 新旧 Schema 读取兼容与发布预览。 |
| `mateclaw-dataagent-ui/src/api/dataset.ts`、`src/api/insight-dashboard.ts` | 草稿预览/确认、组件执行/预览的类型化 HTTP 调用。 |
| `mateclaw-dataagent-ui/src/utils/script-template.ts` | 按显式筛选器作用范围、字段映射生成系统区域；更新时保留用户区域。 |

### 后端文件

| 文件 | 本轮职责 |
| --- | --- |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java` | 可读写组件级数据配置；兼容旧 `dataSource` 和根级 `datasetInputs`。 |
| `.../dto/DatasetSourceDefinition.java`、`.../dataset/DatasetSourceType.java` | 补 Aloudata 指标&维度定义；接口弹窗形成受控 API 定义；文件格式不以 FILE 单值丢失。 |
| `.../controller/DataAgentDatasetController.java`、新增 `.../controller/DatasetComposerController.java` | 复用现有数据集 CRUD/descriptor/preview；增加来源草稿预览、确定和 SQL 执行记录读回。 |
| `.../service/impl/DatasetManageServiceImpl.java`、`.../service/impl/DatasetExecutionServiceImpl.java` | 草稿确认后登记数据集，草稿预览不落库；严格按来源选择 Adapter。 |
| `.../dataset/jdbc/JSqlParserValidationService.java`、`.../dataset/jdbc/JdbcDatasetAdapter.java` | 后端只读 AST 校验、`WHERE` 预览门槛、参数绑定、限定返回行数与下推报告。 |
| `.../dataset/AloudataAnalysisViewAdapter.java`、新增 `.../dataset/AloudataMetricsAdapter.java` | 已有指标视图复用；指标&维度补统一描述、预览、过滤下推。 |
| `.../dataset/http/HttpApiRequestPolicy.java`、`.../dataset/http/HttpApiDatasetAdapter.java` | 接口登记和读取消费同一受控定义；请求头、Host、方法、超时、参数校验及脱敏。 |
| `.../dataset/file/FileSchemaInspector.java`、`.../dataset/file/FileDatasetAdapter.java`、`.../controller/DataAgentDatasetFileController.java` | 五种文件格式的上传、字段探测、读取和大小限制。 |
| `.../service/impl/DashboardExecutionServiceImpl.java`、`.../service/impl/InsightDataBindServiceImpl.java`、`.../controller/DataAgentInsightDashboardController.java` | 按组件范围解析输入/筛选/脚本，保存后执行并绑定最终行集到组件；旧根级执行保持兼容。 |

以上路径中的 `...` 均以 `mateclaw-dataagent/src/main/java/vip/mate/dataagent` 为根。新增类和 HTTP 路径在首次契约任务中固定，后续任务只按该契约实现。

### 组件级 Schema（本计划固定契约）

新配置放在 `pages[].components[].config.datasetPipeline`，避免把一个卡片的输入误作用于其他卡片；已确认的 `datasetId` 指向可复用的数据集定义，组件配置只存引用和本次输入参数：

```json
{
  "config": {
    "datasetPipeline": {
      "datasetInputs": [
        {
          "datasetId": "101",
          "inputName": "table1",
          "sourceType": "JDBC_SQL",
          "displayName": "JDBC · db1",
          "fieldMappings": [{ "source": "strategy_id", "target": "策略 ID" }],
          "filters": [{ "field": "event_date", "role": "dimension", "operator": "between", "value": ["2026-08-01", "2026-08-31"] }]
        }
      ],
      "scriptFilterBindings": [
        {
          "filterComponentId": "filter_1",
          "inputNames": ["table1"],
          "fieldMappings": { "table1": "strategy_id" }
        }
      ],
      "script": "",
      "parameters": [],
      "executionPolicy": { "timeoutSeconds": 30, "maxRows": 500 }
    }
  }
}
```

- `displayName` 仅供卡片显示来源；连接凭据、接口敏感请求头、原始文件内容不进入 Schema。
- `fieldMappings.target` 是最终数据集字段名；下推前由服务端把最终字段名还原为各来源字段名。
- 无脚本且只有一个输入时，最终结果就是该输入的映射后行集；两个及以上输入必须有能产生最终结果的用户处理脚本才允许最终预览、保存发布。未放置筛选器的卡片应显式确认“无筛选器绑定”，不用凭空创建绑定记录。
- 旧 Schema 没有 `config.datasetPipeline` 时，原 `dataSource` / `tabs[].dataSource` 原样渲染。旧根 `datasetInputs/script/scriptBindings` 保留运行时兼容读取；不在加载时自动写回，也不把多个旧输入猜测分配给某一组件。用户在旧卡片上首次编辑新数据配置并确认后，才写组件级结构。

### 草稿接口（本计划固定 DTO 与路由）

`POST /dataagent/api/v1/dataset-composer/drafts/preview`：请求为 `sourceType、datasourceId?、sourceConfig、filters、limit`，返回 `rows、schema、pushdownReport、executionId`；草稿仅用于当前登录用户和工作区的一次预览，不返回凭据。`POST /dataagent/api/v1/dataset-composer/drafts/confirm`：请求另含 `name`，返回 `datasetId、descriptor`；服务端将来源配置固化为可复用定义，接口来源在事务内先登记受控 API 定义，文件来源只使用已有 `objectId`。`GET /dataagent/api/v1/dataset-composer/executions/{executionId}`：读取该用户自己的脱敏 SQL/来源执行信息。

已有 `POST /dataagent/api/v1/datasets/preview` 仍用于确定后输入卡片预览。组件最终预览/运行新增组件范围 `POST /dataagent/api/v1/insight/dashboards/{dashboardId}/components/{componentId}/executions`，并复用已有执行状态、结果、日志、取消接口；组件预览返回 `InsightComponentDataDTO`。所有写入复用现有工作区角色要求，读取执行记录必须校验所属工作区和用户。

## 2. 开发任务与单元测试

每项任务采用“先写关键失败测试 → 运行确认失败原因 → 最小实现 → 定向测试 → 集成测试”的顺序。测试断言业务结果和边界，不用字符串快照代替行为断言。

### Task 1：组件级契约与旧 Schema 兼容

**Files:** `src/types/index.ts`；`src/utils/dashboard-schema.ts`；`InsightDashboardSchemaDTO.java`；对应 `dashboard-schema.spec.ts`、`InsightDashboardSchemaDTOTest.java`。

**Interfaces:** 定义 `ComponentDatasetPipeline { datasetInputs: DashboardDatasetInput[]; scriptFilterBindings: DashboardScriptFilterBinding[]; script?: string; parameters?: DashboardScriptParameter[]; executionPolicy?: DashboardExecutionPolicy }`；`readComponentPipeline(component): ComponentDatasetPipeline | undefined`；`writeComponentPipeline(component, pipeline): InsightComponent`。

- [ ] 测试：两个卡片各有一个不同 `datasetPipeline`，保存、读回、复制、刷新后仍各归各；旧 `dataSource` 和 `tabs[].dataSource` 解析后不变；旧根级输入不被错误迁移到第一个卡片。
- [ ] 实现类型、纯函数和 Java DTO 序列化；新增结构只在显式确认时写入，兼容解析不产生隐式写入。
- [ ] 运行 `npm --prefix mateclaw-dataagent-ui run test -- --run src/utils/__tests__/dashboard-schema.spec.ts` 和 `make dashboard-dataagent-test`，确认上述断言通过。

### Task 2：原“数据源”位置的添加入口与来源选择

**Files:** `PropertyPanel.vue`、新 `DatasetSourcePicker.vue`、`DatasetInputPanel.vue`、`InsightDashboardEditorView.vue`；`PropertyPanel.spec.ts`、新 `DatasetSourcePicker.spec.ts`。

**Interfaces:** `DatasetSourcePicker` 输出 `{ sourceType, datasourceId?, fileFormat? }`，选择事件只打开对应配置弹窗，不立即创建空输入。

- [ ] 测试：选中卡片时原“数据源”位置显示 `+ 添加数据集` 和搜索；不显示旧 `datasourceId/metrics/dimensions/sql` 控件，也不显示“脚本结果数据集输入”作为并列入口。未选中卡片时不向任何组件添加数据。
- [ ] 测试：来源树节点与原型一致；搜索命中 JDBC 连接名和已有指标视图；点击“接口”直接打开配置弹窗，绝不显示“已配置接口列表”。
- [ ] 实现分类选择器；JDBC 列表复用 `/v1/datasources`，Aloudata 视图复用 `/{datasourceId}/analysis-views`；搜索只过滤当前用户可见的资源节点，接口和文件格式始终可选。
- [ ] 移除展示组件旧直绑编辑区的 UI；保留 `dataSource` 只读兼容展示和历史运行时。多指标展示与历史多 Tab 渲染继续可用；原型没有规定“新 Tab 独立配置不同数据集”的交互，本计划不为新 Tab 保留另一套数据源入口，测试须覆盖旧 Tab 可读且不误改。
- [ ] 定向 Vitest 通过，并检查浏览器 DOM 中入口顺序和属性面板滚动位置。

### Task 3：JDBC SQL 草稿、预览、确定

**Files:** 新 `DatasetSourceDialog.vue`、`src/api/dataset.ts`；新 `DatasetComposerController.java`、草稿 DTO/service；`JSqlParserValidationService.java`、`JdbcDatasetAdapter.java`；新对话框测试、`JdbcDatasetAdapterTest.java`、草稿 Controller MVC 测试。

**Interfaces:** 选择 `JDBC · db1` → `[输入 SQL]` → 弹窗 `取消 / 筛选预览 / 执行记录 / 确定`；预览请求 `{sourceType:"JDBC_SQL", datasourceId, sourceConfig:{sql}, filters, limit:20}`；确定返回 `{datasetId, descriptor}`。

- [ ] 测试：选 JDBC 后先出现“输入 SQL”，不出现别名；SQL 弹窗取消后组件输入数量不变。单条只读 `SELECT/WITH` 可提交；写入语句、多个语句、空 SQL、注释里伪造的 `WHERE` 被拒绝。
- [ ] 测试：预览前 AST 识别真实查询 `WHERE`；无 `WHERE` 返回明确错误且数据库查询次数为 0；有 `WHERE` 才执行受限预览。绑定参数经过 JDBC 占位符，不把筛选值拼进 SQL 字符串；`pushdownReport.pushedFilters` 与实际条件一致。
- [ ] 后端将草稿校验、预览和确定拆开；草稿预览不创建 `DatasetEntity`，确定时复用 `DatasetCreateRequest` 的 `JDBC_SQL` 定义并校验选中连接的访问范围。
- [ ] 前端确定后才形成输入卡片并显示 `数据集别名：[table1]`；SQL 结果是惰性的可复用定义，不把预览的 20 行物化进仪表盘 Schema。
- [ ] 定向 UI/JUnit 测试、MySQL/Postgres JDBC 集成测试通过；执行记录只显示查询摘要、状态、耗时和错误，不回显连接密码或绑定值中的敏感字段。

### Task 4：Aloudata 两种来源模式

**Files:** `DatasetSourceDialog.vue`；`DataAgentDatasourceController.java`；`DatasetSourceDefinition.java`、`DatasetSourceType.java`；`AloudataAnalysisViewAdapter.java`、新 `AloudataMetricsAdapter.java`；对应 Controller/Adapter 测试。

**Interfaces:** 指标视图草稿 `{sourceType:"ALOUDATA_ANALYSIS_VIEW", datasourceId, sourceConfig:{analysisViewId, metrics, dimensions}}`；指标&维度草稿 `{sourceType:"ALOUDATA_METRICS", datasourceId, sourceConfig:{metrics, dimensions, filters}}`。两种模式都输出统一 descriptor 和 batch。

- [ ] 测试：模式入口分别出现；视图模式只能选择已有视图并可选输出指标/维度；指标&维度模式配置指标、维度和筛选；取消不形成输入。
- [ ] 实现视图详情字段读取、选择合法性校验和草稿预览；指标&维度新增类型化定义及 Adapter，复用现有 Aloudata `metrics_query` 编译/调用能力，不伪装成已有视图。确认后各自生成真实 `datasetId`。
- [ ] 测试：两种模式的当前输入都能独立预览；多选/范围条件能下推则出现在 `pushedFilters`；无权访问或 Aloudata `SM_02_0038` 明确报错并不形成卡片。
- [ ] 模拟 Aloudata 合约测试和 DataAgent 定向测试通过；真实 Aloudata 联调另列环境验收，不将模拟结果声明为真实权限 PASS。

### Task 5：接口弹窗与五类文件

**Files:** `DatasetSourceDialog.vue`、`src/api/dataset.ts`；草稿 service；`HttpApiRequestPolicy.java`、`HttpApiDatasetAdapter.java`；`FileSchemaInspector.java`、`FileDatasetAdapter.java`、`DataAgentDatasetFileController.java`；对应 UI/Java 测试。

**Interfaces:** 接口表单字段严格为 `Host、请求路径、请求方式、超时时长(ms)、请求头、请求参数`，按钮为 `取消 / 筛选预览 / 确定`；文件流程为格式选择、文件选择、字段识别、筛选预览、确定。

- [ ] 测试：点击“接口”立即打开表单；未填写 Host/路径不能预览；合法请求方式、超时、头/参数可预览；取消不登记；确定在受控定义中保存 endpoint/method/参数声明，Schema 仅保存 `datasetId`/引用。
- [ ] 后端复用 Host allowlist、HTTPS、私网/metadata 地址阻断和敏感 Header 策略；接口参数只允许声明字段映射；响应行数、超时和日志脱敏受限。测试恶意 Host、重定向、敏感头、未声明参数和超时均拒绝，且不产生数据集。
- [ ] 测试：文件树精确列出 Excel/CSV/TXT/JSON/Parquet；选择文件后上传返回 `objectId`，识别字段结构并预览；取消组件输入不变。空文件、损坏格式、重复列名、超限文件明确报错。
- [ ] 在现有 Inspector/Adapter 上补 TXT 和 Excel `.xls`（现有 `.xlsx` 保持）、CSV、JSON、Parquet 的描述与读取闭环；TXT 的分隔符/编码由文件配置表单声明，服务端按受限样本探测，不接受任意本地路径或 URL。
- [ ] 五格式文件测试与模拟 API 测试通过；文件内容没有进入请求 JSON、Dashboard Schema 或 Python 参数。

### Task 6：确认后输入卡片、字段名称、输入筛选

**Files:** `DatasetInputPanel.vue`、新 `DatasetFieldMappingDialog.vue`、`DatasetInputFilterDialog.vue`、`DatasetPreviewDialog.vue`；`DatasetExecutionServiceImpl.java`；对应 Vitest/JUnit 测试。

**Interfaces:** 卡片按添加顺序显示 `数据集 N / 来源 / 数据集别名`；操作按钮按原型中的来源专项规则显示：JDBC 在 SQL 确认后为 `[字段名称] [移除数据集]`，独立筛选/预览从其 SQL 配置入口进入；Aloudata 为 `[字段名称] [筛选预览] [移除数据集]`；接口为 `[修改字段名称] [移除数据集]`，预览从接口配置入口进入；文件为 `[字段名称] [筛选预览] [移除数据集]`。字段弹窗操作 `{source,target}[]`，筛选弹窗操作 `DatasetFilter[]`；弹窗“确定”写入当前组件输入。

- [ ] 测试：JDBC 别名仅在 SQL 确认后出现；接口别名仅在接口确认后出现；每次添加形成一张独立卡片。移除只删除当前组件引用，不调用数据集 DELETE，也不影响另一个组件引用。
- [ ] 测试：字段映射弹窗展示“原名称 / 目标名称 / 添加字段映射 / 取消 / 确定”；重复目标名、空目标名和不存在源字段被拒绝；取消回滚；确定后最终预览使用目标名。
- [ ] 测试：输入筛选弹窗展示“字段 / 过滤条件 / 添加筛选条件 / 取消 / 确定”；单选 `eq`、多选 `in`、范围 `between` 及最近 30 天可配置；筛选字段须属于当前 descriptor；取消回滚。
- [ ] 调用现有 `/v1/datasets/{id}/descriptor` 和 `/v1/datasets/preview` 做当前输入独立预览，输入筛选尽可能在 Adapter 端下推；不支持的下推在 `residualFilters` 和执行信息中如实显示，由服务端执行残余条件或拒绝不可安全执行的组合。
- [ ] 定向 UI/Java 测试通过；一个卡片的预览失败不得清空其他卡片的草稿或已确认输入。

### Task 7：筛选器绑定 → Base Script → Python

**Files:** `DatasetInputPanel.vue`、新 `PythonScriptDialog.vue`、`src/utils/script-template.ts`、`DashboardExecutionServiceImpl.java`；对应 `DatasetInputPanel.spec.ts`、`script-template.spec.ts`、`DashboardExecutionServiceTest.java`、Runner 相关测试。

**Interfaces:** `scriptFilterBindings` 以筛选器 ID、作用的输入别名、每个输入的源字段映射为执行依据；系统区域从该声明生成，用户区域由用户维护。

- [ ] 测试：数据源和输入筛选未确认前不允许进入 Python 编辑；绑定面板能选择筛选器、逐输入勾选作用范围、自动匹配和手工字段映射；若页面无筛选器，可确认空绑定后继续；最终字段名映射回各来源字段后才下推。
- [ ] 测试：筛选器 A 仅勾数据集 1 和 3，运行时数据集 2 的 `DatasetReadRequest.filters` 不含 A；多选转换为 `in`，范围转换为 `between`；服务端拒绝缺失必需字段或越权绑定。
- [ ] 测试：完成绑定后可“生成 Python Base Script”；系统生成区域展示别名、参数和字段映射并只读；用户 Join/计算区可编辑。绑定变化时显示“重新生成系统区域 / 暂不更新”，重新生成不覆盖用户区。
- [ ] 一个输入的最终预览允许无脚本；两个及以上输入在脚本为空、只有系统区域或用户逻辑无法形成最终结果时明确阻止最终预览/发布；允许符合 Runner 现有结果契约的合法脚本写法，不用单一文本关键字判断。保存脚本只存逻辑，真正执行仅发生在预览/仪表盘查询。
- [ ] 组件范围执行解析当前组件的输入白名单及绑定配置，给 Runner 注入已确认的参数映射；仍通过 `datasets.read(..., filters=...)` 向 Adapter 发请求。不允许脚本拿到数据库凭据或执行 `pip install`。
- [ ] Vitest、DataAgent 和 `make dashboard-runner-test` 通过；双组件脚本不会交叉读取或改写另一组件结果。

### Task 8：三层预览、执行反馈和组件渲染

**Files:** 新 `DatasetPreviewDialog.vue`；`InsightDashboardEditorView.vue`、`DashboardPreviewView.vue`、`src/api/insight-dashboard.ts`；`DataAgentInsightDashboardController.java`、`DashboardExecutionServiceImpl.java`、`InsightDataBindServiceImpl.java`；对应 UI/Java/E2E 测试。

**Interfaces:** 当前输入预览只运行当前来源；预处理结果预览运行当前组件全部输入与 Python；组件展示预览把最终行集送入当前卡片/KPI/图表/表格。四个反馈 Tab 为 `数据预览 / 字段结构 / 执行信息 / 处理日志`。

- [ ] 测试：输入 1 独立预览不会执行输入 2；预处理结果预览给出输入行数、输出行数和最终字段；组件展示预览实际更新当前画布组件，另一个卡片保持原值。
- [ ] 执行反馈至少包含输入数据集名、实际查询条件、已下推与残余筛选、可取得的扫描量与返回量、处理耗时、输入/输出行数、错误与处理节点；源端不返回扫描量时显示“来源未提供”，不伪造数值。
- [ ] 执行状态、日志、结果、取消沿用已有 API 的工作区隔离；浏览器关闭/刷新后可以按执行 ID 读回记录。大结果仍经 ObjectRef 受控读取有限行，不把整表放进组件 Schema。
- [ ] KPI、图表、表格及 ECharts 取最终数据集；保存、刷新、发布后的组件展示与编辑器展示一致；历史单源卡片继续通过旧 `dataSource` 路径渲染。
- [ ] UI 全量测试、构建、DataAgent/Runner 测试、Chrome E2E 和 CDP 按第 4 节验收用例全部通过；截图、DOM、AX 和 API 响应写入本轮验收记录。

## 3. 测试数据与测试环境

本地使用现有 Docker 模拟条件，不等待真实环境联调：

- JDBC：两条独立连接 `db1/db2`（本地可对应 `testdb1/testdb`），各含 `strategy_id、event_date、status、amount` 等列；准备有/无 `WHERE` SQL、只读/写入/多语句 SQL、单选/多选/范围值及至少 100 行样本。
- Aloudata：模拟服务提供一个已有指标视图及其指标、维度，另提供指标&维度查询的可预测 5 行结果；准备 `SM_02_0038`、超时、空视图和字段不存在响应。
- 接口：本地受控 `e2e-http` 提供 GET/POST 的 5 行 JSON、分页、超时、非 2xx、重定向和未声明参数场景；Host allowlist 与仅开发测试的 HTTP 例外只用于本地模拟。
- 文件：每种 Excel（`.xlsx`/`.xls`）、CSV、TXT、JSON、Parquet 各准备正常 5 行、空/损坏/重复列/超限样本；上传到本地临时对象存储。
- 仪表盘：一页至少有两个展示卡片、一张图表、一个筛选器和一个时间筛选器；另准备历史 `dataSource` 与历史 Tab 配置的 Dashboard。数据集与仪表盘 ID 用测试夹具创建并在测试后恢复/清理，不依赖固定 ID。

工程门禁命令：

```bash
npm --prefix mateclaw-dataagent-ui run test -- --run
npm --prefix mateclaw-dataagent-ui run build
make dashboard-dataagent-test
make dashboard-runner-test
make dashboard-prerequisites-contract-test
make dashboard-prerequisites-simulation
MATECLAW_E2E_ALOUDATA_MODE=simulation MATECLAW_E2E_BROWSER_CHANNEL=chrome npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line
git diff --check
```

E2E 的 JWT、工作区 ID 和测试 Dashboard ID 使用现有 state/fixture 注入；不写入计划或截图。缺少 Docker、Chrome 或服务时记为 `BLOCKED` 并写明缺项；不能用 Vitest/构建通过替代浏览器验收。

## 4. 测试工程师功能用例与预期结果

每个用例记录 `PASS / FAIL / BLOCKED / NOT_RUN`，附环境、浏览器版本、Dashboard ID、实际步骤、API 状态、截图、DOM/AX 证据与缺陷链接。测试工程师从“洞察 → 仪表盘 → 组件 → 卡片 → 属性配置”正式导航进入，不通过组件测试挂载页代替。

| ID | 操作 / 输入 | 预期结果 |
| --- | --- | --- |
| FE-01 | 选中空卡片并查看属性面板。 | 原“数据源”位置为 `+ 添加数据集` 和搜索；没有旧数据源/指标/维度/SQL 表单，且没有独立“脚本结果数据集输入”入口。 |
| FE-02 | 点击添加并展开来源树。 | 精确显示 Aloudata 两模式、JDBC 下可见连接、接口单节点、文件五格式；搜索过滤资源，不改变类别结构。 |
| FE-03 | 点击“接口”。 | 立即打开 Host/请求路径/方式/超时/请求头/请求参数弹窗；无“已配置接口列表”。 |
| FE-04 | 选 `JDBC · db1`，尚未输入 SQL。 | 只显示 `[输入 SQL]`，没有输入卡片和别名。 |
| FE-05 | JDBC SQL 弹窗输入合法带 `WHERE` 的 SELECT，点筛选预览。 | 只读检查通过，返回有界行集、字段及下推信息；预览不增加输入卡片。 |
| FE-06 | 输入不含真实 `WHERE` 的 SQL，或仅在注释中写 `WHERE`。 | 阻止预览、提示补充条件；数据库没有执行查询，组件状态不变。 |
| FE-07 | 输入 UPDATE、DELETE、DROP、多条语句或注入拼接条件。 | 后端拒绝；无预览数据、无新数据集；错误可理解且无凭据泄露。 |
| FE-08 | JDBC 弹窗点取消，然后重新配置并点确定。 | 取消不生成卡片；确定后生成“数据集 1 · JDBC · db1”，此时才显示别名，刷新后引用仍在。 |
| FE-09 | Aloudata 选择“指标视图”，选已有视图和输出字段并预览、确定。 | 独立返回视图字段/行集；确认后生成视图输入卡片，不出现 JDBC SQL。 |
| FE-10 | Aloudata 选择“指标&维度”，配置指标、维度、筛选并预览、确定。 | 查询走指标&维度模式；筛选结果正确，生成对应输入卡片，不能被记为视图查询。 |
| FE-11 | Aloudata 返回 `SM_02_0038`。 | 显示访问失败；不得形成输入卡片，不影响既有输入。 |
| FE-12 | 接口填合法 GET/POST 配置，点筛选预览和确定。 | 本地受控接口返回预期 5 行；确定后出现接口输入卡片和别名，敏感请求头不进入 Schema。 |
| FE-13 | 接口使用未允许 Host、私网/metadata 地址、敏感头或未声明参数。 | 服务端拒绝，执行记录给出脱敏原因，无网络访问和持久化数据集。 |
| FE-14 | 五种文件格式依次选文件、识别字段、预览、确定。 | 各返回预期 5 行和字段；确认后有文件输入卡片，只保存受控 `objectId` 引用。 |
| FE-15 | 上传空、损坏、重复列或超限文件。 | 字段识别/预览明确失败；没有输入卡片或大 JSON 残留。 |
| FE-16 | 在输入 1 点“字段名称”，改 `strategy_id → 策略 ID`，取消/再确定。 | 取消后名称不变；确定后最终行集显示“策略 ID”，源端筛选仍使用 `strategy_id`。 |
| FE-17 | 在输入 1 配单选、多选、最近 30 天及范围筛选，独立预览。 | 仅输入 1 重新查询；返回行数符合样本，执行信息列出实际下推/残余条件。 |
| FE-18 | 同一组件连续确认 JDBC 1、JDBC 2、Aloudata 3。 | 三卡片按添加顺序显示，可分别预览；移除输入 2 不删除真实数据集，也不影响其他卡片。 |
| FE-19 | 为筛选器选择仅作用于输入 1、3，输入 2 不勾选。 | 作用范围可读可改；执行时输入 1、3 收到参数，输入 2 不收到。 |
| FE-20 | 自动匹配字段失败，手动将筛选参数映射到另一输入的目标字段。 | 页面提示需要手工映射；确认后服务端还原为源字段并下推，返回正确结果。 |
| FE-21 | 完成绑定后生成 Base Script，并修改用户 Join 逻辑；再更改绑定。 | 系统区域只读；出现“重新生成系统区域 / 暂不更新”；重新生成后用户 Join 代码字节内容保留。 |
| FE-22 | 只确认一个输入，不写 Python；预览最终结果。 | 允许预览，最终数据集等于单输入经字段映射后的数据。 |
| FE-23 | 确认两个输入但不写用户处理脚本，尝试最终预览/发布。 | 明确提示 Python 必填；不执行 Runner，也不展示错误混合数据。 |
| FE-24 | 两输入写有效 Join，点“预览结果”，再看组件展示预览。 | 输入先按各自筛选取数，Python 后处理；最终行数、字段、卡片数字/图表与样本一致。 |
| FE-25 | 打开三类预览的反馈 Tab。 | 可见数据预览、字段结构、执行信息、处理日志；输入名、实际条件、下推/残余、输入/输出行数和耗时可读；缺失的扫描量注明来源未提供。 |
| FE-26 | 对两个卡片配置不同输入/脚本；预览其中一个。 | 只刷新目标卡片，另一个卡片数据和配置不变；保存、刷新、复制后范围不串。 |
| FE-27 | 打开历史 Aloudata/JDBC 直绑卡片和历史多 Tab 仪表盘。 | 渲染与保存读回不回归；首次使用新入口确认后，新结构只作用于被编辑卡片。 |
| FE-28 | 编辑保存、刷新、发布后从正式仪表盘入口触发查询筛选。 | 编辑器预览与发布展示数据一致；筛选参数按绑定范围下推，Python 仅在查询/预览时运行。 |
| FE-29 | 取消草稿预览执行、触发超时/数据源不可用，再恢复服务重试。 | 可取消或失败可见；执行记录可读，既有配置不丢，重试只执行一次明确操作。 |
| FE-30 | 在 1280×800、1920×1080 与窄窗口按步骤操作并用 Chrome CDP 截图/AX 检查。 | 按钮、弹窗和错误提示可见可操作；焦点进入/返回弹窗，标签与输入有关联，无面板遮挡；视觉快照与批准基线一致。 |

## 5. 验收标准与发布门槛

功能验收必须同时满足：

1. FE-01～FE-30 在本地模拟环境执行，核心路径 FE-01～FE-24、FE-26～FE-28 全部 `PASS`；无 `FAIL`。仅外部真实 Aloudata 联调可另标 `BLOCKED`，不得影响本地模拟验收结论。
2. 真实浏览器从正式导航逐步配置 JDBC、Aloudata、接口、文件及双输入 Python；每步保留截图、DOM、AX 和 API 证据。视觉基线变更由测试工程师审核差异后更新，不能以重新录制快照掩盖回归。
3. 所有新增/修改 UI 单元测试、DataAgent JUnit/集成测试和 Runner 测试通过；`vue-tsc`、Vite 构建、模拟前置条件检查、`git diff --check` 通过。测试报告列出实际命令、退出码和失败数。
4. 草稿取消/失败不产生输入；确定后 `datasetId` 与字段 descriptor 可 API 读回；组件级 Schema 保存/刷新/复制不串卡片；历史 Schema 渲染不回归。
5. SQL 无真实 `WHERE` 不执行预览，写入 SQL 和多语句被后端拒绝；接口受控、文件使用对象引用；错误和执行记录不泄露凭据。筛选作用范围及源字段映射由服务端执行并在下推报告中如实反映。
6. 单输入无 Python 可产出最终结果；多输入无用户 Python 不能产出最终结果或发布；绑定变化不覆盖用户脚本。三层预览和正式展示返回相同的组件结果。

交付时提交本轮验收记录、测试用例运行表、批准的视觉快照和新旧 Schema 对比。真实测试环境切换 Aloudata、对象存储和 API Host allowlist 时，按 `docs/superpowers/plans/2026-09-11-dashboard-overall-implementation-plan.md` 的环境配置步骤替换模拟地址并重新执行外部联调；模拟 PASS 不等同于真实环境 PASS。

## 6. 执行中的冲突处理

此前 `docs/superpowers/plans/2026-09-15-dashboard-dataset-composer-remediation.md` 写了“API/文件只选已有数据集”“JDBC 行内 SQL”“下方脚本输入”等历史实现路径，与本原型流程不一致；执行本计划时以本文件和 `原型设计.md` 为本轮卡片交互依据，同时保持其已经落地的通用 Dataset Adapter 与兼容读取能力。`design.md` 的阶段范围如与原型的“指标&维度”冲突，本计划按本轮用户“严格按原型”要求纳入该模式，并在实施记录中单独标出新增 Adapter 的测试成本。
