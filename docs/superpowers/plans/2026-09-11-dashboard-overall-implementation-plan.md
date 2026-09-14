# 通用洞察仪表盘总体 Implementation Plan

> **2026-09-14 前端产品闭环更正：** 本地模拟数据和接口只替代外部依赖，不降低前端完成标准。当前 07/09 仍存在旧/新绑定模型割裂、文件上传与多个按钮为空实现、HTTP/API 手填内部 ID、主题样式未统一、E2E 绕过前端创建链路等问题；数据集正式入口和数据源新建类型选择已在本轮补齐，但总体计划仍不能表述为“前端页面已完整提供”。详细问题和复现步骤已直接记录到 [07 数据源与数据集管理入口](07-dataset-management-ui.md#前端产品闭环缺口2026-09-14) 和 [09 仪表盘运行时集成](09-dashboard-runtime-integration.md#前端产品闭环缺口2026-09-14)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在兼容现有 Aloudata 仪表盘和 Agent Python 的前提下，交付 JDBC、Aloudata 指标视图、HTTP/API、文件数据源与 Python 多源预处理闭环。

**Architecture:** DataAgent 负责目录、权限、查询和任务治理，所有来源通过带 `DatasetAccessContext` 的 `DatasetSourceAdapter` 输出统一 `DatasetInputDescriptor`/`DatasetBatch`。Python Runner 只接收输入目录和任务级读取能力，不接触连接凭据；脚本读取当前返回受限批次，脚本大结果通过受控 `outputRef/dataRef` 传输。输入侧的 ObjectRef 批读保留为后续扩展，不把尚未实现的远程批读写成当前能力；仪表盘运行时只消费数据集契约，不直接感知数据库、Aloudata 或文件协议。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、JDBC/JSqlParser、Vue 3/TypeScript、Python 3、FastAPI、Polars/Pandas、Arrow/Parquet、MinIO/S3、Docker Compose。

**Spec:** `docs/策略解读/design.md`、`docs/superpowers/specs/2026-09-11-query-parameter-pushdown-design.md`、`docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md`

**当前状态（2026-09-13）：** 当前范围的本地实现、自动化验证和候选提交验收已完成；本地开发 Gate 已通过。真实 Aloudata 结果查询授权、正式 API/对象存储等属于后续环境联调 Gate，不阻塞本地开发和代码交付。候选实现及计划更新已提交并推送到 `origin/feature/dev_fu`，当前本地与远端 SHA 一致。平台内 AI 自动生成 SQL/Python 不纳入本期；身份与权限完善也不纳入本次范围。

**产品闭环状态更正（2026-09-14，当前提交 05bda5c7）：** 本地模拟环境的正式前端入口、文件上传/统一预览、脚本结果绑定、双源运行时和错误态已通过真实 Chrome channel Playwright 验证；G3 在“本地模拟”范围为 `PASS`。真实 Aloudata 授权、正式对象存储替换以及完整主题/键盘专项仍是外部或后续 Gate，不能据此宣称生产环境完成。

| 前端验收阶段 | 状态 | 结论 |
| --- | --- | --- |
| 登录并进入配置 | `LOCAL-SIMULATION-PASS` | 使用本地 JWT/工作区上下文进入配置中心，并由 Chrome channel E2E 验证入口 |
| 数据连接管理 | `PARTIAL` | JDBC/Aloudata 连接管理和来源选择已验证；HTTP/API 登记定义与正式文件对象管理仍依赖已有登记/外部存储 |
| 五种来源数据集创建 | `LOCAL-SIMULATION-PASS` | `/datasets`、`/datasets/new`、`/datasets/:id/edit` 覆盖文件上传、统一预览；真实入口用例已通过 |
| SQL、指标视图及来源参数配置 | `PARTIAL` | 五种来源控件已从正式入口可达，HTTP/API 改为登记定义选择；定义登记管理和真实连接联调仍待完善 |
| 仪表盘选择与绑定 | `PARTIAL` | 已明确直接指标/脚本输入模式并新增结果绑定组件选择器；完整创建、应用、保存、回显现场链路仍待验收 |
| 预览和运行时消费 | `LOCAL-SIMULATION-PASS` | 双源、ObjectRef、大结果、ECharts、错误/取消/超时/资源限制均由真实本地栈验证 |
| 项目风格、主题和可访问性 | `PARTIAL` | 关键页面已通过 Chrome AX/CUA 基础验收；数据集页主题统一、键盘遍历和完整对比度审计仍待补齐 |

**外部条件清单：** [2026-09-13 外部前置条件与测试支撑计划](2026-09-13-dashboard-external-prerequisites.md)；执行 00–09 前必须按该清单收集并验证外部系统、账号、数据和证据条件。

## 当前开发验证基线（本地模拟）

**本轮前置复验（2026-09-13）：** 已在当前工作树执行 `make dashboard-prerequisites-simulation`、`bash scripts/verify-dashboard-design.sh` 和 `./scripts/verify-dashboard-external-prerequisites.sh --local`，分别取得模拟依赖健康、`DESIGN-PASS` 和 `EXTERNAL-PREREQUISITES-LOCAL-PASS`。Aloudata Adapter 外部探测也通过 Docker Maven 入口完成；该探测使用本地 WireMock，不代表真实租户授权。

在真实外部条件尚未提供前，00–09 的开发和自动化验证统一使用 `dev-support/local-simulation/`，不再等待真实 Aloudata、数据库、对象存储或 API。启动后执行：

```bash
./dev-support/local-simulation/scripts/start.sh
make dashboard-prerequisites-simulation
```

| 能力 | 本地模拟资源 | 默认地址/标识 | 适用子计划 |
| --- | --- | --- | --- |
| JDBC 表/SQL | MySQL、PostgreSQL + `orders/customers` 脱敏数据 | `127.0.0.1:13306`、`127.0.0.1:15432` | 01、03、07、09 |
| HTTP/API | WireMock + OpenAPI | HTTPS `127.0.0.1:18443/orders`、`api/orders-openapi.yaml` | 04、07、09 |
| Aloudata 指标视图 | WireMock tree/detail/query/metrics | `local_sales_view`、`datasourceId=9001`、`tenantId=local-tenant` | 02、07、09 |
| 文件/ObjectRef | MinIO + CSV/JSON/Parquet/XLSX + manifest | bucket `mateclaw-sim`、对象键 `files/<name>` | 05、06、07、08、09 |
| Python Runner | 项目固定 Dockerfile + `runner_internal` internal 网络 | `/health` 返回 `status=UP` | 08、09 |

本地模拟只证明契约、映射、读取、过滤、资源限制和执行链路；不得将其证据写成真实外部系统验收。切换真实环境时，按外部前置计划和本文末尾的正式环境改造清单替换连接地址、Secret、视图和对象存储配置。

### 当前工作树回归结果

在本地模拟环境启动后，当前工作树已复跑 Runner `21 passed`、UI `26 passed`、UI production build、DataAgent Docker Maven 全量测试（退出码 0）、独立 E2E Compose 全量 Playwright `9 passed`（含 JDBC+模拟 Aloudata、API+文件、ECharts 绑定、旧 Schema、错误/取消/超时/资源限制和 ObjectRef）、设计门禁和前置条件检查；独立 E2E Compose 的 UI 容器已增加并验证 HTTP healthcheck，统一入口会等待 DataAgent actuator 与 UI 健康后再进入 seed/Playwright。结果详见 [dashboard-mvp-acceptance.md](../evidence/dashboard-mvp-acceptance.md)。这些结果是开发基线，不代表真实 Aloudata 或正式测试环境 Gate 已关闭。

**最新复验（2026-09-13）：** 清理本地模拟栈后，独立 E2E Compose 使用同一轮 seed 和全部 Dashboard ID 重跑全量 Playwright，取得 `9 passed (36.2s)`；覆盖新增 ECharts 绑定预览。结束后已恢复本地模拟栈，fixture、MinIO 和 Runner 健康检查再次通过。

**模块回归复验（2026-09-13）：** 候选提交 `fc799a85414520a4118b36d736f01984b773255e` 上的基线为 DataAgent `152/152`、Python Runner `20/20`、UI `24/24`；当前工作树追加多别名 Join、HTTP/API 来源配置和 Aloudata 只读视图选择回归后，Runner 为 `21/21`、UI 为 `27/27`，并完成 UI production build；结果已写入总验收记录。

同轮补充结果绑定聚焦复验：`dataset-result.spec.ts`、`dashboard-schema.spec.ts` 共 `4 tests passed`，覆盖脚本结果到 Table/ECharts 的映射及旧 Schema 的 `scriptBindings` 保留。

证据边界：候选提交已在临时 E2E Compose 预览页采集到 ECharts `canvasCount=1` 的运行时证据（截图 `/tmp/mateclaw-dashboard-cdp/dashboard-echarts-preview.png`）；该证据只覆盖本地模拟 Aloudata，不替代真实 Aloudata 授权验收。

**视觉验收复验（2026-09-13）：** 已通过 CDP 实际检查列表、编辑器和最终结果页，AX 树与 5 行结果证据已写入 09 子计划和总验收记录；候选 SHA 生成后仍需在同一 SHA 重采集。

**视觉验收入口固化（2026-09-13）：** 新增并实跑 `mateclaw-dataagent-ui/e2e/cdp-dashboard-visual-check.mjs`，统一生成列表、编辑器、Table 结果和 ECharts 结果四页截图及 AX 摘要；脚本仅使用调用方 Token，不持久化凭据。候选提交已完成该复验。

**候选 SHA 验收（2026-09-13）：** `fc799a85414520a4118b36d736f01984b773255e` 在独立 E2E Compose 中完成完整 Playwright `9 passed (34.8s)`；CDP 四页均成功，AX 节点数为列表 `567`、编辑器 `500`、Table 结果 `617`、ECharts 结果 `88`，ECharts 结果页 `canvasCount=1` 且标题可见。证据截图目录为 `/tmp/mateclaw-dashboard-cdp`。该候选 SHA 的真实 Aloudata 结果查询仍因 `SM_02_0038` 保持 `BLOCKED`。

**CDP 命令入口（2026-09-13）：** UI 新增 `npm run test:e2e:cdp`，并验证缺少认证变量时 fail-fast；计划命令不再依赖开发者手工拼接 Node 路径。

**本轮 Google Chrome CDP 视觉验收（2026-09-13）：** 使用当前工作树 HEAD `0c1b066f6f5059fcf8930294c111707e8d39c91f`、本地模拟服务和 Google Chrome 交互标签页逐项执行 VIS-UI01～VIS-UI08。当前通过 `VIS-UI01、VIS-UI03、VIS-UI05、VIS-UI07`；`VIS-UI06` 的跨工作区权限拒绝子项为 `NOT_RUN`；`VIS-UI02、VIS-UI04、VIS-UI08` 失败：文件/HTTP/JDBC Descriptor 显示 `0 个字段` 但输入预览有字段，双源 Table DOM 有行但可视容器高度为 `0`，大结果没有明确 `outputRef`/受限预览边界提示。截图和复现步骤见 [dashboard-mvp-acceptance.md](../evidence/dashboard-mvp-acceptance.md) 的“本轮 Google Chrome CDP 视觉验收”。因此不能把 VIS-UI01～VIS-UI08 记为全部 PASS。

**当前工作树复验补充（2026-09-14）：** 当前服务可访问，CDP 脚本已重新生成 `/tmp/mateclaw-dashboard-cdp-20260914/` 四页截图；真实 Google Chrome `9222` 页面可通过 CDP 看到洞察仪表盘列表。由于 CUA 返回 `Unable to load browser request-header policy`，未能完成 CUA 点击链路。本轮真实 Playwright 9 用例执行在 `dashboard-jdbc-aloudata.png` 快照处发现 `1158 pixels (ratio 0.01)` 差异；双源 5 行及 `120.5` 功能断言已到达，但视觉基线仍应记为待处理。详见统一验收记录的 2026-09-14 补充。

### 本地模拟驱动的实施顺序

后续开发任务统一按以下顺序执行，避免子计划各自创建一套不可复现的外部依赖：

1. 启动或复用模拟环境：`./dev-support/local-simulation/scripts/start.sh`；每轮开始先运行 `make dashboard-prerequisites-simulation`。若切换到 E2E Compose，先执行 `dev-support/local-simulation/scripts/cleanup.sh`；`scripts/e2e/start-dashboard-mvp.sh` 会检测同端口模拟容器并 fail-fast，E2E 结束后再恢复模拟环境。
2. 先完成 01 目录契约，再按 02～06 的 Adapter 顺序运行定向测试；JDBC 使用 MySQL/PostgreSQL，HTTP/API 使用 HTTPS WireMock，文件/ObjectRef 使用 MinIO，Aloudata 使用 `local_sales_view` 模拟视图。
3. 08 Runner 只通过任务输入别名读取上述数据集，验证过滤条件、资源限制、取消和大结果 ObjectRef；不得把连接凭据或大 JSON 放入 Runner 请求。
4. 07 管理 UI 使用同一批模拟数据源/对象创建数据集，09 再执行 API+文件、JDBC+模拟 Aloudata、旧 Schema、异常重试和大结果闭环。
5. 每个子计划的结果写入统一验收记录，并明确标注 `LOCAL-SIMULATION`、`EXTERNAL-BLOCKED` 或 `CANDIDATE-SHA`；本地通过不能关闭真实 Aloudata Gate。E2E seed 后使用 `scripts/e2e/export-dashboard-mvp-env.sh` 从同一 state 文件批量导出 Dashboard ID，避免人工复制遗漏；该脚本不处理认证值。

子计划只负责自身的定向实现和证据，不重复部署依赖。若模拟服务未启动，任务应先执行前置检查并停止，不以 mock、跳过测试或历史结果代替。

## Global Constraints

- 首期数据源：JDBC、Aloudata 指标视图、HTTP/API、文件；不含 JS、湖仓目录、流式数据源和 Trino。
- Aloudata 只读已有指标视图；连接保存默认 `tenantId`。无运行时筛选时可使用指标视图结果接口；带运行时筛选时必须先验证该接口是否原生支持，若不支持，则从视图详情构造等价的 Semantic `metrics/query` 请求并合并受限条件，禁止假装已下推。
- SQL 只用于 JDBC；条件经 AST 校验和 PreparedStatement 绑定，禁止字符串拼接。
- HTTP/API 只访问已登记地址；文件只从受控对象存储读取。
- Python 下推条件只能由 `datasets.read(..., filters=...)` 表达；DataFrame 后置过滤不下推。
- 新 Runner 不支持运行时 `pip install`；旧 `LocalCodeExecutorService` 不改行为。
- 不把数据库凭据或未受控 URL 交给 Runner；输入读取由 DataAgent 返回受限批次，脚本大结果通过有 TTL、task 约束的 `dataRef/outputRef` 传递。现有 workspace/访问上下文仅保持兼容，不在本次完善身份与权限模型。
- `datasets.read` 通过仅允许当前任务输入别名的短期读取令牌回调 DataAgent；Runner 不能凭 `datasetId` 任意读取目录中的其他数据集。

## 交付与提交边界

各子计划中的“提交”步骤仅表示建议的 Git 交付节点，不代表实现步骤。本轮已按用户确认范围生成候选提交 `fc799a85414520a4118b36d736f01984b773255e`，并在同一 SHA 上完成模块、E2E 和 CDP 验收；后续计划更新已在本地提交，远端同步状态必须以实际 `git ls-remote` 复核为准。真实 Aloudata 授权未满足前，不得将候选 SHA 标记为正式环境全量完成。

## 后续环境联调执行顺序（不阻塞本地开发）

以下步骤仅在进入目标测试/生产环境联调时执行。它们补充真实外部证据，不回退或暂停已经通过本地模拟环境验证的实现。

1. **非 Aloudata 场景复验**：使用当前已有测试上下文，不设置 `MATECLAW_E2E_ALOUDATA_DATASET_ID`，执行 `seed-dashboard-mvp.sh`、API+文件/旧 Schema/错误兼容 Playwright 用例以及 `verify-dashboard-mvp-cleanup.sh`；身份与权限完善不纳入本次复验。
2. **Aloudata 场景联调**：获得当前认证上下文可查询的指标视图后，设置 `MATECLAW_E2E_ALOUDATA_DATASET_ID` 与 `ALOU_DATA_*` 外部测试变量，执行 ALO-X01/ALO-X02 和 JDBC+Aloudata Playwright 用例；记录基线结果、筛选结果和远端下推证据。
3. **候选 SHA 环境复验**：用户确认提交范围后，在同一 SHA 上重跑后端、Runner、UI、Compose 和两条双源 E2E；真实 Aloudata/API/对象存储仅替换连接配置并追加环境证据，不要求重新实现本地已通过的能力。

### 下一轮执行前置条件与命令矩阵

以下变量只从当前 shell、CI Secret 或加密配置注入，不写入计划、日志或提交：

| 场景 | 必需变量 | 执行入口 | 通过条件 |
| --- | --- | --- | --- |
| 非 Aloudata E2E | 当前测试上下文（不新增 JWT/角色矩阵） | `scripts/e2e/seed-dashboard-mvp.sh` → `npm --prefix mateclaw-dataagent-ui run test:e2e` → `scripts/e2e/verify-dashboard-mvp-cleanup.sh` | API+文件、旧 Schema、错误/取消/超时/资源限制场景通过；权限专项延期 |
| Aloudata Adapter（后续联调） | `ALOU_DATA_EXTERNAL_TEST=true`、产品/语义服务地址、`ALOU_DATA_TENANT_ID`、`ALOU_DATA_AUTH_TYPE`、`ALOU_DATA_AUTH_VALUE`、`ALOU_DATA_TEST_DATASOURCE_ID`、`ALOU_DATA_TEST_VIEW_NAME`、筛选字段和值 | `mvn -f mateclaw-dataagent/pom.xml -Dtest=AloudataAnalysisViewExternalIT test` | 目录/详情、至少 5 行结果和一个远端筛选结果均取得真实响应；缺变量或无授权时记录 `EXTERNAL-BLOCKED`，不影响本地开发 Gate。 |
| 本地 CDP 视觉验收 | 同一轮 E2E 的 `MATECLAW_E2E_TOKEN`、`MATECLAW_E2E_WORKSPACE_ID`、`MATECLAW_E2E_DASHBOARD_ID`、`MATECLAW_E2E_ECHARTS_DASHBOARD_ID` | `cd mateclaw-dataagent-ui && npm run test:e2e:cdp` | 列表、编辑器、Table 结果和 ECharts 结果页均生成截图和 AX 摘要；结果只能标记为工作树/本地模拟，候选 SHA 需重跑。 |
| 双源最终 Gate | 上述两组变量及 `MATECLAW_E2E_ALOUDATA_DATASET_ID` | 同一候选 SHA 重跑两条双源 Playwright 场景 | JDBC+Aloudata 与 API+文件均通过，且报告包含实际查询参数/下推证据 |

未满足变量时，入口必须 fail-fast 或显式 `EXTERNAL-BLOCKED`；不得用空值、mock、`test.skip` 或历史 PASS 代替真实联调证据。本地模拟入口仍必须保持绿色，作为当前开发验收依据。

---

## 本轮一致性审查结论

- 读取时序统一为“Runner 启动 → 脚本调用 `datasets.read` → DataAgent 查询 → 返回受限 `DatasetBatch`”；脚本大结果再通过受控 `outputRef/dataRef` 上传。输入侧远程 ObjectRef 批读不在当前实现范围，待专门的读取接口和 SDK 迭代后再启用。
- Aloudata `analysisView/query` 当前登记参数不能证明支持临时筛选；必须以真实能力验证决定直查还是编译为 Semantic `metrics/query`，无法等价转换就拒绝，不伪造下推。
- `DatasetSourceAdapter` 强制接收 `DatasetAccessContext`；短期令牌只授权当前任务的输入别名。
- ObjectRef 基础设施前移到文件 Adapter 之前，避免文件上传计划引用尚未实现的存储能力。
- 新增独立的管理入口子计划，补齐“Adapter 已实现但用户无法创建/选择数据集”的产品闭环。
- 前端引入最小 Vitest/Test Utils 验证，不再把单纯 `build` 当成功能测试。
- 第一阶段 Runner 固定为 Polars/Pandas/PyArrow，不安装或依赖 DuckDB。

## 当前实施进度（2026-09-12）

> **测试计数口径**：本节早期复验记录中的 `140/140`、`146/146`、`147/147` 为历史工作树快照；候选 SHA 基线为 `152/152`，当前 DataAgent 全量基线已更新为最新 `153/153`（0 failures/errors/skips）。

- G0：已新增 `scripts/verify-dashboard-design.sh`，可重复校验首期范围、JDBC-only SQL 边界、`datasets.read` 时序、Runner 依赖边界及 01–09 子计划测试矩阵引用；当前检查通过。
- 外部前置本地化：已新增 `dev-support/local-simulation/`，用 Docker Compose 提供 MySQL 8.4、PostgreSQL 15.6、MinIO、WireMock（HTTP/HTTPS 临时 PKCS12）和项目固定 Dockerfile 构建的 Python Runner；数据库样例、对象 bucket、10 行脱敏文件 fixture、Aloudata/HTTP 模拟接口、Runner 健康检查及 start/check/cleanup 入口已通过本机验证；`start.sh` 会等待长期服务 healthcheck 后再 seed，`check.sh` 校验 fixture manifest 的行数、文件集合、字节数和 SHA-256；本地 Aloudata Adapter 外部探测已通过；根目录统一前置条件检查脚本已支持 `--local`/`--simulation`/`--external` 并对缺少真实变量 fail-fast。真实 Aloudata 目前还缺结果查询授权，正式对象存储和身份权限仍按外部条件/后续专项处理。
- G1：JDBC、Aloudata、HTTP/API、文件 Adapter 和 ObjectRef 已有基础实现与定向测试；JDBC 编译器已用 AST 判断多语句，支持字符串字面量分号和尾部注释，并保持 PreparedStatement 绑定；文件 Schema 探测/批读四种格式定向测试通过，新增文件 `in`/`between` 过滤、Schema 版本拒绝、流关闭、空/损坏文件、字节超限和 XLSX 压缩比/条目数/解压后大小限制测试；Parquet 已使用 Avro requested projection 与 Parquet FilterPredicate 对基础比较谓词实际下推，并在 `PushdownReport` 中区分 pushed/residual filters；HTTP 已覆盖 page/offset/cursor 分页、按 offset/size 计算页码、最大页数限制、非页对齐偏移拒绝、5xx/429 分类、结果路径错误、幂等一次重试、响应字节、行数上限、重定向拒绝和每次网络尝试前的 DNS 重绑定再校验，生产 RestTemplate 已配置连接/读取超时；数据集管理已增加五种类型化来源定义，创建/更新服务支持新 `sourceDefinition`，兼容旧 JDBC 请求，并对名称执行 NFKC、空值拒绝和工作区内唯一性校验；HTTP/API 的 `apiDefinitionId` 会从数据源 `connectionParams.apiDefinitions` 解析并固化为受控来源配置，不接受数据集请求直接注入 URL/Header；创建/更新服务已统一委托 `DatasourceManageService.checkDatasourceReadable`，不会因仅能查到数据源记录就绕过工作区/授权校验；数据源编辑响应已对嵌套连接参数递归移除密码、Token、认证值和敏感 Header；管理 UI 已接入来源类型选择、JDBC SQL 编辑、Aloudata 只读指标视图目录选择，以及 HTTP/API 已登记定义 ID、文件对象 ID/格式输入，页面不接受任意 URL、Header 或本地路径；数据集目录迁移已用真实 MySQL 8.4 和 PostgreSQL 15.6 从 V217 连续验证至 V220；数据集全部管理路由、数据源全部管理路由和文件上传路由均已通过真实 `MockMvc + DataAgentWorkspaceInterceptor` 行为矩阵；统一执行服务的 Adapter 选择与读取测试已参数化覆盖五种来源，DataAgent 当前全量 `152/152` 个测试在 Docker Desktop/Testcontainers 配置下通过。为支持内部 Runner 读取，Aloudata 端点服务已增加关键端点代码级兜底，API 客户端已在参数校验前注入连接级认证上下文，Adapter 已兼容来源定义中的 `analysisViewId` 与历史 `viewName`；认证注入、端点兜底、内部 Runner 回退和字段兼容回归均已由 Docker Maven 定向测试覆盖。Aloudata 真实目录/详情已通过探测，结果查询 `SM_02_0038` 作为后续环境联调项记录，不影响本地 Gate。
- G2：Python Runner、`datasets.read` 受控回调、短期读取令牌、任务输入目录和 Docker 非 root 基线已完成定向验证；脚本 `result` 与日志分离，小结果受限 JSON，大结果惰性转换为 Parquet 并通过 DataAgent 内部令牌接口返回 `outputRef`；输入别名已与统一契约严格一致，内部读取控制器已有任务令牌/别名安全测试，Runner status/cancel、不可用/过期令牌和旧 Local `requirement` 兼容回归已补齐；Runner 任务新增内存和单文件大小限制，并由 Unix 子进程 `rlimit` 执行，用户脚本子进程仅继承最小运行时环境和任务级 SDK 变量；Python SDK 已在请求前校验过滤字段/操作符，兼容 DataAgent `R.ok(...)` 响应包装并识别业务错误，Runner 全量测试当前 `20/20` 通过；Compose 中 Runner/MinIO 健康检查及 Runner `/health` 已通过，临时 Docker `--internal` 网络已验证脚本外部 DNS/HTTPS 访问失败；DataAgent 镜像已构建并在带测试凭据的 Compose 中启动，容器内访问 Runner `/health`、MinIO readiness 均返回 HTTP 200，Flyway 已完成到 v220；本轮 DataAgent 全量测试 `152/152` 通过（0 failures/errors/skips），执行进程注入 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` 并禁用 Ryuk 以适配 Docker Desktop 容器网络；证据见 `docs/superpowers/evidence/runner-network-isolation-2026-09-12.md` 和 `docs/superpowers/evidence/dataagent-compose-internal-connectivity-2026-09-12.md`。
- G2 追加复验（2026-09-12）：在当前工作树重新执行 Docker Maven DataAgent、Runner pytest、UI 测试/构建及设计门禁校验，分别取得 `146/146`、`17/17`、`24/24`、构建成功和 `DESIGN-PASS`；另补充统一执行服务五类来源实际 `read` 路由参数化测试。详细命令与边界见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`。该结果未生成候选 SHA。
- G2 最新本地回归（2026-09-13）：Python SDK 补齐默认过滤角色并接收任务参数，随后通过 App→Executor→DatasetClient 集成回归，Runner 全量更新为 `20/20`；非 Aloudata Playwright `7 passed`，API+文件断言实际 `PAID` 结果，真实 Aloudata 用例作为后续环境联调项显式记录。
- 当前工作树复验（2026-09-13）：`make dashboard-verify-local` 聚合门禁完整通过，包含模拟依赖检查、DataAgent `153/153`（0 failures/errors/skips）、Runner `21 passed`、UI `27/27`、UI 生产构建和 `DESIGN-PASS`。Runner 测试通过 Make 目标固定在 `mateclaw-python-runner` 子项目环境执行；仓库根目录直接使用 `uv run pytest` 会错误收集客户端项目并缺少其依赖，不作为有效门禁命令。
- G2 当前工作树追加回归（2026-09-13）：Runner 新增两个别名 Join 测试，使用本地 HTTP 数据面返回 `orders` 与 `customers`，脚本通过 `datasets.read` 获取后执行 Pandas Join，断言合并结果；`make dashboard-runner-test` 更新为 `21 passed`。
- G2 最新回归（2026-09-12）：在统一执行服务新增“未知来源无 Adapter 时明确拒绝”边界测试后，Docker Maven DataAgent 全量结果更新为 `147/147`（0 failures/errors/skips）；该数字 supersede 前述 `146/146` 记录。
- G1 HTTP/API TLS 补强（2026-09-12 14:15）：E2E WireMock 已改为启动时生成 PKCS12 HTTPS 8443 fixture，DataAgent 通过只读 truststore 建立 TLS，HTTP 策略仅在显式 TLS 测试模式下允许固定 `e2e-http:8443` 私网主机，seed 脚本登记 `https://e2e-http:8443`，移除 `ALLOW_INSECURE` 例外；独立 fixture 与完整依赖栈健康检查均已通过，证据见验收记录。
- G3：已完成 DataAgent 的统一 Descriptor/Preview 编排基础接口，以及前端 Descriptor/Preview API、统一读取类型和输入别名校验工具；仪表盘 Schema 1.1、数据集输入面板和后端执行基础接口已接入，可选择数据集、设置别名、查看字段、执行输入预览、插入 `datasets.read` 草稿模板、配置参数作用范围、创建/查询/取消 Runner 任务、查询日志并展示受限最终结果或 `outputRef`；execution 元数据已增加 MySQL/PostgreSQL 持久化，DataAgent 已能受控读取 ObjectRef 结果行并由页面映射为预览表格；Runner 回调地址已修正为 Compose 中带 `/dataagent/api` context path 的 DataAgent 内部地址，并将 `mateclaw-dataagent` 服务加入基础 Compose；脚本结果经用户确认后可映射到当前 Table/ECharts 组件并持久化为 `scriptBindings`，预览页会对有绑定的新 Schema 执行脚本并覆盖对应组件数据，旧 Schema 无绑定时不触发 Runner；前后端已补齐参数契约校验（前端输入别名/参数定义，后端声明参数的未知、必填、类型检查），预览页已将声明参数与同名维度筛选/唯一日期范围对象映射后传入脚本执行，后端兼容日期范围对象和旧两元素列表；编辑器和预览页已共用旧 Schema 读取适配器；最终预览已补充运行中取消、失败后的重试入口，当前工作树 UI `25/25` 个 Vitest 用例和生产构建通过；已补齐独立 E2E Compose、MySQL/WireMock/CSV fixture、seed/cleanup 脚本和验收记录模板；本轮真实 Compose + JWT 已验证 API+文件、大结果 ObjectRef 各 1 passed，错误/兼容组 5 passed（含旧 Schema、失败/重试、取消/重试、超时/重试和资源限制/重试），并验证 UI 容器独立依赖卷下 API+文件与大结果 ObjectRef 2 passed；大结果验证 `inline=false`/`outputRef` 和 10 行受限预览；E2E seed/cleanup 已调整为允许在无 Aloudata 授权时独立运行非 Aloudata 场景；Aloudata 场景等待授权指标视图，作为后续环境联调项，不影响本地 G3 开发验收。
- 当前不把“Java 编译通过”或“前端 build 通过”标记为 G3 完成；它们仅是契约层的阶段性验证证据。

---

## 子计划与依赖

执行 00–09 前置条件统一参见 [2026-09-13 外部前置条件与测试支撑计划](2026-09-13-dashboard-external-prerequisites.md)。该文件不是新的功能 Gate，而是外部环境、账号、接口、样本数据、Secrets、观测和验收证据的收集入口；缺少条件时按其中的 `EXT-*` 编号记录阻塞。

### 计划层级说明

本文件是总体实施计划。`G0`～`G3` 是阶段性验收门禁（Gate），表格中的 `00`～`09` 是各 Gate 对应的子计划文件，不是与 Gate 平级的单个开发任务。每个子计划内部再按 `Task` 划分工作包，并用 `Step` 表示执行步骤；只有 `Task`/`Step` 才是具体执行项。

| Gate | 子计划 | 依赖 | 测试用例 | 视觉验收 | 独立验收产物 |
| --- | --- | --- | --- | --- |
| G0 | [00 设计冻结](00-design-freeze-and-acceptance.md) | 无 | DOC-U01～DOC-U04 | N/A（由 07/09 统一验收） | 范围、需求与测试矩阵 |
| G1 | [01 Dataset Catalog](01-dataset-catalog-contract.md) | G0 | CAT-U01～CAT-DB02 | N/A（由 07/09 统一验收） | 统一契约、持久化、目录 API |
| G1 | [02 Aloudata Adapter](02-aloudata-analysis-view-adapter.md) | 01 | ALO-U01～U08、C01～C02、X01～X02 | N/A（由 07/09 统一验收） | 只读视图目录、详情、结果 |
| G1 | [03 JDBC Adapter](03-jdbc-adapter.md) | 01 | JDBC-U01～JDBC-I05 | N/A（由 07/09 统一验收） | SQL 校验、绑定和下推报告 |
| G1 | [04 HTTP/API Adapter](04-http-api-adapter.md) | 01 | HTTP-U01～HTTP-U11 | N/A（由 07/09 统一验收） | 白名单请求、参数透传和分页 |
| G1 | [05 文件 Adapter](05-file-adapter.md) | 01、06 | FILE-U01～FILE-U09 | N/A（由 07/09 统一验收） | 上传、Schema、受控批读 |
| G1 | [06 ObjectRef 传输](06-object-ref-transport.md) | 01 | OBJ-U01～U03、I01～I06 | N/A（由 07/09 统一验收） | Arrow/Parquet 引用与 TTL |
| G1 | [07 数据源与数据集管理](07-dataset-management-ui.md) | 01–05 | MGMT-C01～MGMT-UI05 | VIS-UI01～VIS-UI02 | 四类来源可配置、可选取、可预览 |
| G2 | [08 Python Runner](08-python-runner-and-sdk.md) | 02–06 | PY-U01～U05、R01～R06、J01～J03 | N/A（由 07/09 统一验收） | 固定镜像、SDK、隔离任务 |
| G3 | [09 仪表盘集成](09-dashboard-runtime-integration.md) | 07、08 | DASH-S01～E2E-06 | VIS-UI03～VIS-UI08 | 编辑、预览、异步执行、兼容 |

02–04 与 06 在 01 完成后可以并行；05 复用 06 的对象存储基础设施；07 在 Adapter API 稳定后统一补齐管理入口；08 等四个 Adapter 实现和传输契约稳定后开始；09 不得绕过 08 直接读取数据源。

## 基线准备

执行代码计划前，在隔离 worktree 中运行：

```bash
mvn -pl mateclaw-plugin-api,mateclaw-server install -Dmaven.test.skip=true
mvn -N install -DskipTests -q
mvn -f mateclaw-sdk/pom.xml install -Dmaven.test.skip=true
make dashboard-dataagent-test
make dashboard-runner-test
make dashboard-ui-test
make dashboard-ui-build
make dashboard-prerequisites-contract-test
# 或直接执行完整本地模拟门禁：
make dashboard-verify-local
```

任一基线失败时先记录为既有失败并停止，不把失败归因于新实现。

## 总体测试策略

- Unit/Controller 测试用于快速验证契约、校验、映射和状态机；数据库、MinIO、文件格式与网络协议使用 Testcontainers 或真实 fixture，不以纯 mock 替代。
- Aloudata 单元测试使用受控响应，本地开发 Gate 使用模拟 ALO；正式环境联调时再执行 ALO-X01、ALO-X02，结果接口不可用或没有已授权视图记录 `EXTERNAL-BLOCKED`。本次不新增身份、角色和跨工作区权限验收；已有权限拒绝用例只做兼容回归。
- UI 使用 Vitest/Vue Test Utils 验证数据模型和交互，再通过 production build；构建成功不等于功能用例通过。
- E2E 以同一候选 SHA 运行 JDBC+Aloudata、HTTP/API+文件两条双源场景，记录任务 ID、下推报告、页面结果和错误路径。
- 所有必选测试禁止 `@Disabled`、`Assume`、`pytest.skip`、`test.skip`；测试矩阵详见 `2026-09-11-dashboard-mvp-test-and-acceptance.md`。
- 视觉验收统一通过 CDP 连接当前候选 SHA 的真实 UI 页面执行；必须保留 viewport、页面 URL、CDP 操作摘要、截图和对应任务/查询证据。静态代码、组件单测、build、历史截图或接口响应不能替代视觉验收。
- 视觉验收范围为 07/09 子计划的页面交互；00～06、08 无页面交互，验收记录明确标注 `N/A（由 07/09 统一验收）`，不重复伪造视觉证据。具体用例为 VIS-UI01～VIS-UI08。

## Gate 验收

非 Aloudata 和模拟 Aloudata 场景可以先独立执行和记录证据，并据此关闭本地开发 Gate；获得真实 Aloudata 已授权视图后，再执行 JDBC+Aloudata 双源环境联调并关闭正式环境 Gate。身份与权限完善不作为本次 Gate；本地模拟通过不等同于正式环境联调通过。

### G0：设计冻结

- 能力矩阵覆盖四类来源；术语、错误码和首期排除项只有一个定义。
- `design.md` 不再出现产品侧字段手动绑定或首期支持 JS/Trino 的矛盾描述。
- DOC-U01～DOC-U04 全部通过，每条需求都能追踪到测试 ID。

### G1：统一读取

- 五种来源类型由四个 Adapter 实现承载（JDBC 表与 JDBC SQL 共用 JDBC Adapter），均通过同一个带 `DatasetAccessContext` 的 `DatasetSourceAdapter.describe/read` 契约。
- 权限拒绝、空结果、超时、资源超限具有不同错误码。
- JDBC 实际 SQL、API 实际参数、文件实际读取范围可审计。
- CAT、JDBC、HTTP、FILE、OBJ、MGMT 及本地模拟 ALO 对应自动测试全部通过；真实 ALO-X01 目录/详情已取得响应，ALO-X02 的 `SM_02_0038` 作为后续环境联调记录。

### G2：Python 处理

- 两个不同来源能通过别名读取并在 Runner 内 Join。
- DataAgent 与 Runner 之间不传大 JSON，不泄露凭据，任务可取消且超时会终止子进程。
- 篡改 `datasetId`、跨任务复用读取令牌或读取未声明别名均被拒绝。
- 旧 Agent Python 回归测试保持通过。
- PY-U01～U05、PY-R01～R06、PY-J01～J03 全部通过，Runner 镜像和进程隔离满足验收标准。

### G3：产品闭环

- 用户能选择数据集、分配别名、查看字段、编写脚本并分别预览输入/最终结果。
- 页面参数进入脚本后由 `datasets.read` 显式用于字段条件。
- 发布后异步任务支持状态、日志、取消、重试；旧 Dashboard Schema 可继续读取。
- DASH-S01～DASH-UI05 与 E2E-01～E2E-06 已绑定候选 SHA `fc799a85414520a4118b36d736f01984b773255e` 的本地模拟证据；真实 Aloudata 场景作为后续环境联调项。
- 候选 SHA 已通过 CDP 采集列表、编辑器、Table 和 ECharts 结果页；截图、任务 ID、查询/下推报告均关联同一轮 seed。真实 Aloudata 双源页面待授权后补采。

## 完成定义

**2026-09-14 双源视觉快照修复：** 修复编辑器右侧属性面板被固定高度和 `overflow:hidden` 裁剪的问题，改为可滚动的属性区域并让输入面板按内容高度布局；同时将 E2E 断言更新为当前可访问名称“脚本结果数据集输入”，并等待每个输入的字段描述异步完成后再采集快照。真实本地模拟栈全量 11 条 Playwright E2E（错误/兼容 5、双源/大结果/ECharts 4、数据集入口 2）`11 passed`；双源 JDBC+Aloudata 仍实际返回 5 行并包含 `120.5`，历史 `1158 pixels (ratio 0.01)` 快照差异已在同一环境更新基线并以非更新模式复跑通过。更新后的快照属于当前渲染契约，不将像素差异误判为查询失败。

**2026-09-14 可访问性补充：** 修复组件库只能拖拽、无法用键盘添加组件的问题；条目支持 Enter/Space，具备按钮语义和可访问名称。新增回归后 UI `10 files / 37 tests` 通过，当前本地闭环的可访问性覆盖从表单控件扩展到组件创建入口。

当前本地实现可在候选提交同时通过本次范围内测试矩阵必选用例、后端测试、UI test/build、Runner 测试、CDP 视觉验收和 Docker Compose 模拟双源预览后标记开发完成。正式环境发布前另需补齐 Aloudata 外部验证、真实双源预览和对象存储配置替换；这些是后续环境联调 Gate。身份与权限完善不作为本次完成条件；文档、mock、历史截图、不同 SHA 或跳过的测试均不算对应环境的完成证据。

**2026-09-14 继续实施补充：** 修复 `DatasetInputPanel` 在 Descriptor 空 Schema 异步探测期间误显示“0 个字段”的问题，改为“字段探测中…”并保留刷新入口；新增中间态回归测试。该修复不改变 Dataset 契约或读取时序，只校正页面状态表达。Chrome CUA 已复验数据集管理、新建数据集来源选择和取消返回链路；当前真实工作区没有可授权数据集，Descriptor 中间态现场截图待模拟数据集注入后补采。

**2026-09-14 提交后回归补充：** 当前提交 `00c772d8c315b941b2e0171fd26f53fd12b19416` 已完成 UI 回归 `9 files / 35 tests passed`、production build、`git diff --check` 和 `DESIGN-PASS`。本轮未新增产品闭环状态，既有 `PARTIAL`、`NOT_RUN` 和真实 Aloudata `EXTERNAL-BLOCKED` 结论保持不变。Chrome CUA 重试仍返回 `Unable to load browser request-header policy`，因此不将本轮静态回归或非交互截图写成新的交互视觉 PASS；待 CUA 服务恢复后，按 VIS-UI01～VIS-UI08 补采当前提交证据。

**2026-09-14 来源兼容约束补充：** 数据集编辑器新增数据源类型校验：Aloudata 连接仅允许指标视图来源，JDBC 连接才允许表/标准 SQL；切换连接时自动纠正不兼容的来源类型，非法组合无法保存。新增回归覆盖 Aloudata 上误选 JDBC SQL 的禁用和保存保护。该修复只收紧来源契约，不改变 HTTP/API、文件来源及旧 Schema 兼容路径。

**2026-09-14 服务端边界补充：** `DatasetManageServiceImpl` 同步增加来源与连接类型校验，直接调用创建/更新 API 时也会拒绝 JDBC 绑定 Aloudata 或指标视图绑定 JDBC；新增服务层回归测试。前后端共同约束，不能依赖页面禁用作为唯一保护。

**2026-09-14 产品入口 E2E 补充：** 新增“主导航→数据集管理→新建文件数据集→上传 CSV→创建/同步→进入预览→清理”真实后端用例，补齐 FE-CLOSE-07 的正式创建链路覆盖。首次执行因缺少 Playwright `chromium_headless_shell` 未运行，随后切换系统 Chrome channel 并注入 JWT/工作区上下文完成执行。

**2026-09-14 统一预览流程补充：** 修复创建文件/API/Aloudata/JDBC SQL 数据集后仍无条件调用旧 JDBC `/sync` 的问题，改为创建后通过统一 `DatasetSourceAdapter` 描述与预览，页面可直接进入预览态。新增用例在真实 E2E 栈中 `2 passed`，并断言文件结果包含 `120.5` 与 `east`；全量 E2E 的既有双源编辑器用例另有“未选中组件”失败，保持单独记录，不归因于本修复。

**2026-09-14 当前提交 CDP 视觉补充：** 通过用户 Chrome `9222` CDP 打开 `/datasets/new` 并选择 `E2E Aloudata Simulation`，页面自动切换为“Aloudata 指标视图”，`JDBC_TABLE` 与 `JDBC_SQL` 选项均为禁用状态。截图 `/tmp/mateclaw-cdp-aloudata-compatibility.png`；CUA 服务仍不可用，但本次操作确实连接并控制了用户 Chrome 的 CDP 页面。

## 投入正式测试环境前的改造清单

本地 `dev-support/local-simulation/` 只用于开发和自动化验证；切换到正式测试环境前，必须逐项替换以下配置，并保留可回滚的配置版本。

### 1. Aloudata 连接

- 在 DataAgent 数据源管理中新增或更新 Aloudata 数据源连接，不把地址写入 Dashboard Schema；
- 将产品层地址替换为测试环境的 HTTPS 地址，将语义层地址替换为测试环境地址（内部可信链路可为 HTTP，跨网络/生产必须为 HTTPS），并确认容器 DNS、代理、CA/证书链和出站白名单；
- 配置连接级默认 `tenantId`，不把租户 ID 拼接到数据源名称，也不假设每个租户都有名称；
- 按测试环境要求注入认证方式和值（例如 UID），认证值只能来自 Secret/加密配置，不进入 Git、日志、截图或前端响应；
- 选择并固化一个已授权的指标视图，确认目录、详情、无筛选结果和一个维度/时间筛选结果均可查询；
- 重新执行 `ALO-X01`、`ALO-X02`，记录脱敏状态码、视图名摘要、字段摘要、行数变化和远端请求/下推报告。

### 2. 数据库和 API

- 将 JDBC 数据源的 host、port、database/schema、只读用户名和密码替换为测试环境 Secret；
- 仅迁移脱敏的 `orders/customers` 测试数据和固化 SQL，禁止复用本地数据库账号；
- 将 HTTP/API 数据源的登记定义替换为测试环境 HTTPS endpoint、认证注入、分页和结果路径；数据集仍只能引用 `apiDefinitionId`，不能直接填 URL/Header；
- 更新测试环境的超时、allowlist、DNS/代理和重试策略，并执行 HTTP/API 负例验证。

### 3. 文件与对象存储

- 将 `S3/MinIO endpoint` 从本地 MinIO 地址替换为测试环境对象存储地址，更新 region、bucket、prefix 和 TLS 配置；
- 通过 Secret 注入测试环境 access key/secret key，使用独立测试 bucket 和最小读写权限；
- 重新上传 CSV/JSON/Parquet/XLSX 样本并生成新的对象 ID、SHA-256、Schema 版本和 TTL；不得把本地文件路径或签名 URL 写入 Dashboard Schema；
- 将 Runner/DataAgent 的 ObjectRef 读取地址切换为测试环境内部网络地址，验证对象过期、清理和大结果读取；
- 本地 Compose 的 volume、bucket 和初始化账号只用于开发，不能带入测试或生产环境。

### 4. Runner、网络和观测

- 将 `mateclaw-python-runner` 镜像替换为测试环境固定版本，确认预装 pandas/polars/pyarrow，不启用运行时 `pip install`；
- 配置 DataAgent↔Runner、Runner↔对象存储的内部 DNS/端口和网络白名单，禁止脚本任意访问外网或 Docker Socket；
- 更新健康检查、日志保留、任务超时、内存/文件大小限制和清理策略；
- 用同一测试环境配置执行 API+文件、JDBC+Aloudata 两条双源场景，并保存脱敏任务状态、结果引用和下推证据。

### 5. 本次明确不做的切换

- 不在本次切换中新增 JWT、角色矩阵、workspace 授权或跨租户权限模型；
- 身份与权限专项另行立项，当前仅保持已有访问上下文和兼容回归；
- 不把本地 WireMock 的 Aloudata 响应当作正式环境验收证据。
