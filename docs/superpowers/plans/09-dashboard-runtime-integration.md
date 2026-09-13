# 仪表盘运行时集成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 将统一数据集和 Python Runner 接入仪表盘编辑、输入预览、最终预览与正式异步执行。

**Architecture:** 扩展 Dashboard Schema 为版本化的数据集输入和脚本节点；后端编排 Adapter 与 Runner，前端只配置数据集别名、参数作用范围和脚本。旧 Schema 通过读取时 Adapter 转换，不要求一次性迁移。

**Tech Stack:** Java 21/Spring Boot、Vue 3/TypeScript、Pinia、Axios、Vitest、Playwright、Docker Compose。

**Spec:** `docs/策略解读/design.md` 第 7、8、11、12 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 11、12 节（DASH-S01～E2E-06）。

**当前状态（2026-09-13）：** API+文件及控制面场景已通过真实 Compose 验证；JDBC+Aloudata 结果查询和真实 LLM Agent 对话 E2E 仍未完成，最终 Gate 保持部分通过。

**本地模拟：** 先用 `dev-support/local-simulation/` 的 MySQL/PostgreSQL、WireMock 和 MinIO 完成非 Aloudata 闭环；正式测试环境切换项统一遵循总体计划末尾清单。本次不新增身份与权限验收。

## Global Constraints

- 产品侧不配置参数到数据集字段的手动绑定。
- 输入预览与最终 PyDataset 预览是两个独立动作。
- 平台不生成或自动执行 AI SQL/Python；用户脚本必须先校验和预览。

---

### Task 1: 后端编排 API

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/DatasetExecutionService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DatasetExecutionServiceImpl.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentDatasetController.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentInsightDashboardController.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/InsightDashboardSchemaDTO.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DatasetExecutionServiceTest.java`

**Interfaces:**
- Produces: `GET /v1/datasets/{id}/descriptor`、`POST /v1/datasets/preview`、`POST /v1/insight/dashboards/{id}/executions`、status/cancel/log endpoints。

- [x] **Step 1-3（基础版）**：已完成统一 Descriptor/Preview 编排、Adapter 选择和工作区读取权限复用。
- [x] **Step 4（基础版验证）**：`DatasetExecutionServiceTest`、`DashboardExecutionServiceTest` 与 Runner 任务链路定向测试通过，DataAgent 模块 Docker Maven 编译通过。
- [x] **执行基础版**：已增加 Dashboard 执行创建、状态查询、取消接口；执行从保存的 Schema 读取脚本/输入别名，并使用包含 `/dataagent/api` context path 的 `mateclaw-dataagent:18089` 内部回调地址，支持通过 `MATECLAW_DATASET_READ_BASE_URL` 覆盖。
- [x] **最终预览基础版**：Runner 结果与日志分离，编辑器增加最终结果预览按钮并轮询执行状态；小结果以受限 JSON 展示，超限明确失败，大结果返回 `outputRef`。
- [x] **执行持久化基础版**：新增 `dataagent_dashboard_execution`（MySQL/PostgreSQL）记录，保存工作区、发起用户、状态、参数、日志、结果、ObjectRef 和错误；状态查询会向 Runner 刷新并回写，Runner 不可用时返回最后已知状态。
- [x] **ObjectRef 结果读取基础版**：新增 execution result API，DataAgent 在服务端打开并限制读取 Parquet 行集，页面可展示大结果预览行，不向浏览器暴露存储凭据；结构化行结果已映射为预览表格。
- [x] **最终结果绑定基础版**：用户确认后可将脚本行结果映射为当前组件的 Table 或 ECharts 数据结构；无选中组件时不覆盖画布。
- [x] **旧 Schema 读取适配基础版**：编辑器和预览页共用 `migrateInsightDashboardSchema`，旧 `components` 结构读取时转换为单页，新 Schema 的脚本字段保持不丢失。
- [x] **正式运行时绑定基础版**：保存 `scriptBindings` 后，预览页执行脚本并把小结果或 `outputRef` 结果映射到绑定的 Table/ECharts 组件；旧 Schema 无绑定时不触发 Runner。
- [x] **筛选参数与脚本执行参数交互（基础版）**：预览页将当前维度筛选按同名参数映射，将唯一 `date_range` 参数映射为时间范围对象；后端同时兼容既有的两元素日期列表和该对象形式，并继续执行严格类型校验。未声明筛选不透传，多日期范围参数不猜测。
- [x] **取消/重试展示（基础版）**：最终预览面板保留当前 executionId，运行中显示“取消执行”，取消后显示可重新提交的“重试”；取消请求与轮询互斥，不把取消误报为成功。
- [x] **真实 Compose 验收（部分）**：API+文件双源和旧 Schema 兼容场景各 1 次 Playwright 通过；JDBC+Aloudata 因真实指标视图查询返回 `SM_02_0038` 保持 `BLOCKED`。当前用例不再使用 `test.skip`，缺少授权时显式失败并保留阻塞原因。证据见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`。

### Task 2: 前端数据集和脚本体验

**Files:**
- Modify: `mateclaw-dataagent-ui/package.json`
- Modify: `mateclaw-dataagent-ui/package-lock.json`
- Create: `mateclaw-dataagent-ui/playwright.config.ts`
- Modify: `mateclaw-dataagent-ui/src/types/index.ts`
- Modify: `mateclaw-dataagent-ui/src/api/dataset.ts`
- Modify: `mateclaw-dataagent-ui/src/api/insight-dashboard.ts`
- Create: `mateclaw-dataagent-ui/src/utils/dataset-inputs.ts`
- Test: `mateclaw-dataagent-ui/src/utils/__tests__/dataset-inputs.spec.ts`
- Test: `mateclaw-dataagent-ui/src/views/insight/components/__tests__/PropertyPanel.spec.ts`
- Test: `mateclaw-dataagent-ui/e2e/dashboard-multi-source.spec.ts`
- Test: `mateclaw-dataagent-ui/e2e/dashboard-errors-and-compatibility.spec.ts`
- Modify: `mateclaw-dataagent-ui/src/views/insight/components/PropertyPanel.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/InsightDashboardEditorView.vue`
- Modify: `mateclaw-dataagent-ui/src/views/insight/DashboardPreviewView.vue`

**Interfaces:**
- Consumes: Task 1 APIs；produces versioned `datasetInputs,script,parameters,executionPolicy` in Dashboard Schema。

- [x] **Step 1-3（契约基础版）**：已增加统一输入/过滤/批次类型、Descriptor/Preview API 和别名校验工具。
- [x] **Step 3（第一段）**：Schema 1.1 增加 `datasetInputs/script/parameters/executionPolicy`，旧 Schema 读取时保留兼容；编辑器已接入数据集输入面板，可选择数据集、设置别名、查看 Descriptor 字段、输入预览，并插入 `datasets.read` 脚本草稿模板。
- [x] **第一段交互**：输入预览和 `datasets.read` 脚本草稿模板已接入；前端 API 已增加执行创建、状态查询、取消封装。
- [x] **第一段交互**：输入预览、最终结果预览和 `datasets.read` 脚本草稿模板已接入；前端 API 已增加执行创建、状态查询、取消和日志封装。
- [x] **Vitest 基础版**：已加入 Vitest + Vue Test Utils + jsdom，别名校验和 `DatasetInputPanel` 交互测试通过（6 tests）；`PropertyPanel.spec.ts` 补齐授权字段可见性和组件预览 API 分流测试（2 tests）。
- [x] **参数基础版**：编辑器可配置参数名称、类型和 `dashboard/page/component` 作用范围；不提供字段绑定。
- [x] **参数校验**：前端在执行前校验输入别名、数据集选择、参数名称、类型和作用范围；DataAgent 对已声明参数执行严格的未知参数、必填参数和类型校验，未声明参数的旧 Schema 保持兼容透传。
- [x] **异步状态基础展示**：最终预览已覆盖运行中、取消、失败和重试入口，当前 24 个 Vitest 用例和 production build 通过。
- [x] **真实 DataAgent/Runner 验收（部分）**：真实 Compose + JWT 已验证 API+文件双源和大结果 ObjectRef 各 `1 passed`，后者确认 `inline=false`、`outputRef` 和 10 行受限预览；错误/兼容组当前 `5 passed`（含旧 Schema、脚本失败/重试、取消/重试、超时/重试和资源限制/重试）。Aloudata 结果路径单独执行时明确报 `BLOCKED`，仍等待外部授权，不能宣称全部 E2E 关闭。
- [x] **Step 4: 确保参数 UI 只配置作用范围；脚本通过 `datasets.read` 选择字段**。
- [x] **Step 5: 运行 UI test 和 build**，Expected: PASS（当前 24 个 Vitest 用例通过，生产构建通过）。
- [x] **Step 6（测试入口）**：已加入 `playwright.config.ts`、两组 E2E 文件（覆盖 JDBC+Aloudata、API+文件、旧 Schema、错误、取消、超时和资源超限）和 `test:e2e` 脚本；用例只接受真实 DataAgent/Runner，通过 `MATECLAW_E2E_TOKEN`、`MATECLAW_E2E_WORKSPACE_ID` 和已 seed 的 Dashboard ID 注入环境。完整 seed 最多产生 8 个 Dashboard，其中 JDBC+Aloudata 1 个可选，其余 7 个可在无 Aloudata 授权时独立运行；不使用 route mock。API+文件脚本对 API 与文件都显式传入 `status=PAID` 过滤，WireMock mapping 强制校验 query 参数，确保 E2E-02 能证明参数透传和文件过滤。Vite 开发端口和 DataAgent 代理目标支持 `VITE_DEV_PORT`、`VITE_DATAAGENT_PROXY_TARGET` 覆盖，默认值保持 `5174`/`http://localhost:18089` 不变。Aloudata 用例缺少授权时显式报 `BLOCKED`，不使用 `test.skip`。
- [ ] **Step 7: 统一交付节点（待用户确认）**：`feat: add dashboard dataset script workflow`。

### Task 3: 真实闭环验收

**Files:**
- Modify: `docker-compose.yml`
- Create: `docker-compose.test.yml`
- Create: `mateclaw-dataagent/src/test/resources/e2e/mysql/init.sql`
- Create: `mateclaw-dataagent/src/test/resources/e2e/wiremock/mappings/orders.json`
- Create: `mateclaw-dataagent-ui/e2e/fixtures/orders.csv`
- Create: `scripts/e2e/seed-dashboard-mvp.sh`
- Create: `scripts/e2e/verify-dashboard-mvp-cleanup.sh`
- Create: `docs/superpowers/evidence/dashboard-mvp-acceptance.md`

**Interfaces:**
- Consumes: G0 验收矩阵；produces each requirement's current commit SHA and evidence。

- [x] **Step 1: 启动 Docker Compose，并验证 DataAgent、MinIO、Runner 健康状态**：独立 `docker-compose.test.yml`、MySQL 初始化数据、HTTPS 8443 WireMock fixture 和前端测试容器已启动并通过健康检查；DataAgent 使用 WireMock 只读 truststore 建立 TLS；显式配置 Flyway baseline 和关闭迁移占位符替换以兼容空 E2E 数据库中的 fixture 表；证据见 `docs/superpowers/evidence/dataagent-compose-internal-connectivity-2026-09-12.md` 和 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`。
- [x] **Step 2a: 完成 API+文件双源预览并记录过滤/透传报告**：已通过真实栈验证并记录；不提供 Aloudata 数据集 ID 时可由独立 seed/cleanup 重复执行，脚本对 API 与文件均显式传入 `status=PAID` 过滤。
- [ ] **Step 2b: 完成 JDBC+Aloudata 双源预览并记录过滤/下推报告**：需要已授权 Aloudata 指标视图，当前因 `SM_02_0038` 保持 `BLOCKED`，证据见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`。只有取得真实结果和远端筛选证据后才能勾选。
- [x] **Step 3a: 完成 DataAgent/Runner 控制面与旧工具通道复验**：真实 Compose + Playwright 已验证任务取消、脚本错误/重试、脚本超时/重试、资源超限/重试、旧 Schema、API+文件和大结果 ObjectRef；Runner 控制面已补齐后台异常到 `FAILED` 的终态收敛，并以定向 E2E 复验通过；cleanup 已增加跨工作区读取 HTTP 403 的真实权限验证。大结果用例确认 DataAgent 结果接口返回 `inline=false`/`outputRef` 并限制为 10 行预览。旧执行器与 `PythonAnalysisTool` 的 `ToolCallback` 已在真实 `python3` 容器中完成兼容复验。
- [ ] **Step 3b: 完成真实 LLM Agent 对话 E2E**：当前尚未覆盖，需要真实 Agent/模型配置和同一候选 SHA 的对话链路证据；不得用 ToolCallback 单测替代。
- [ ] **Step 4: 运行完整验证；证据必须绑定同一候选 SHA**：本轮未创建提交或候选 SHA，因此只能记录工作树级验证，不能将此项标记完成。

```bash
mvn -f mateclaw-dataagent/pom.xml test
uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q
npm --prefix mateclaw-dataagent-ui test -- --run
npm --prefix mateclaw-dataagent-ui run build
docker compose config --quiet
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d \
  e2e-mysql e2e-http minio mateclaw-dataagent python-runner mateclaw-dataagent-ui
curl --fail http://127.0.0.1:18089/dataagent/api/actuator/health
curl --fail http://127.0.0.1:18090/health
npm --prefix mateclaw-dataagent-ui exec -- playwright install chromium
scripts/e2e/seed-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui exec -- playwright test
scripts/e2e/verify-dashboard-mvp-cleanup.sh
```

Expected: 所有测试、构建和 Playwright 用例退出码为 0，两个健康检查返回健康状态；双源场景的请求、响应、下推报告、trace/video 和页面截图记录到证据文档。

> HTTP/API E2E 测试服务必须提供可验证的 HTTPS 端点，并满足生产 `HttpApiRequestPolicy` 的公开地址/allowlist 校验；不能直接把 Docker 私网 WireMock 地址当作生产策略的例外。若使用本地测试服务，必须通过独立测试证书和显式测试配置完成 TLS 验证，测试配置不得进入默认或生产 profile。

- [ ] **Step 5: 统一交付节点（待用户确认）**：`test: verify dashboard mvp workflow`。

## 单元与服务测试预期结果

- `DatasetExecutionServiceTest` 覆盖 DASH-S01～DASH-S06：单源不误启多源任务，多源别名正确注册，参数按作用范围传递，权限在 Runner 创建前拒绝，取消状态机幂等，旧 Schema 正常转换。
- `dataset-inputs.spec.ts` 覆盖 DASH-UI01、DASH-UI04、DASH-UI05：别名校验、参数模型和脚本模板稳定。
- `PropertyPanel.spec.ts` 覆盖 DASH-UI02 及组件输入预览 API；`DatasetInputPanel.spec.ts` 覆盖 DASH-UI03 的最终执行 API、加载/失败/取消/重试状态，两者共同保证输入预览与最终预览不混用。

## 验收标准

- DASH-S01～DASH-UI05 自动测试全部通过且无跳过。
- E2E-01～E2E-06 由 Playwright 使用真实 DataAgent、MinIO、Runner、JDBC、Aloudata、HTTP 测试服务和文件对象执行；不得拦截或 mock `/dataagent/api/**`。
- JDBC+Aloudata、HTTP/API+文件两条双源场景都必须在页面展示正确结果，并保留每个来源的过滤/透传报告。
- 权限、超时、资源超限、取消、旧 Dashboard 和旧 Agent Python 均有候选 SHA 绑定证据。
- mock、历史截图、不同 SHA、单纯进程退出码为 0 都不能替代真实 E2E 验收。

## 视觉验收（CDP）

使用 CDP 连接当前候选 SHA 的真实仪表盘 UI，执行 VIS-UI03～VIS-UI08；最低使用 `Page.captureScreenshot`、`Runtime.evaluate` 和 `Accessibility.getFullAXTree`，每个场景必须保存截图，并将页面、任务、查询和下推证据关联到同一候选 SHA。

- 编辑器：输入别名、参数作用域和脚本配置层级清晰；非法输入在提交前显示可读错误，脚本模板使用配置的别名。
- 输入预览：覆盖初始/空态、加载、成功和失败；字段、类型、行数限制和错误提示可读，输入预览与最终预览视觉上可区分。
- 最终预览：成功结果正确映射到 Table/ECharts；大结果明确显示 `outputRef` 和受限预览边界，不把大结果完整注入页面。
- 执行状态：运行中、取消、失败、重试状态的按钮和提示互斥；取消后不得显示成功，失败后可以重新提交且 loading 正确收敛。
- 错误与兼容：权限拒绝、超时、资源超限和 `SM_02_0038` 显示明确错误分类；旧 Schema 可打开且无脚本绑定时不误触发 Runner。
- 双源页面：JDBC+Aloudata、HTTP/API+文件均展示正确结果、来源/过滤关系和下推报告；截图中的结果必须能由任务 ID 和查询证据复核。

预期：VIS-UI03～VIS-UI08 全部 PASS；每条截图记录 `candidateSha、url、viewport、browser、timestamp、cdpActions、screenshotPath`，并与 E2E-01～E2E-06 或对应 UI 用例关联。Aloudata 未授权时，相关场景只能记录 `BLOCKED`，不能以截图或空结果代替通过。
