# 仪表盘运行时集成 Implementation Plan

> 2026-09-14 状态更新：脚本结果数据集输入已明确独立模式、结果绑定组件和读取模板；正式数据集入口、文件创建/预览和返回链路已由真实 Chrome channel E2E 覆盖。本地模拟产品闭环为 `PASS`；真实 Aloudata 授权和正式对象存储仍是后续环境 Gate。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 将统一数据集和 Python Runner 接入仪表盘编辑、输入预览、最终预览与正式异步执行。

**Architecture:** 扩展 Dashboard Schema 为版本化的数据集输入和脚本节点；后端编排 Adapter 与 Runner，前端只配置数据集别名、参数作用范围和脚本。旧 Schema 通过读取时 Adapter 转换，不要求一次性迁移。

**Tech Stack:** Java 21/Spring Boot、Vue 3/TypeScript、Pinia、Axios、Vitest、Playwright、Docker Compose。

**Spec:** `docs/策略解读/design.md` 第 7、8、11、12 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 11、12 节（DASH-S01～E2E-06）。

**当前状态（2026-09-13）：** 候选提交 `fc799a85414520a4118b36d736f01984b773255e` 在本地模拟 E2E Compose 全量 Playwright 已取得 `9 passed`（包含 JDBC+模拟 Aloudata、API+文件、ECharts 绑定、旧 Schema、错误/取消/超时/资源限制和 ObjectRef）；CDP 四页视觉复验也已完成，本地运行时开发 Gate 已通过。真实 Aloudata 结果查询列入后续环境联调，不阻塞本地实现。

**本地模拟：** 先用 `dev-support/local-simulation/` 的 MySQL/PostgreSQL、WireMock 和 MinIO 完成非 Aloudata 闭环；正式测试环境切换项统一遵循总体计划末尾清单。本次不新增身份与权限验收。

**开发验证配置：** 当前开发先使用 `make dashboard-prerequisites-simulation` 准备本地五类来源，再执行 API+文件、JDBC+模拟 Aloudata、旧 Schema、取消/重试、超时和大结果 ObjectRef 场景；模拟 Aloudata 只验证适配器与产品链路，不替代真实 ALO-X02。E2E 使用 `MATECLAW_E2E_ALOUDATA_MODE=simulation` 时，seed 会通过 E2E HTTPS WireMock 创建 `local_sales_view` 数据源/数据集；真实联调仍使用 `MATECLAW_E2E_ALOUDATA_DATASET_ID` 和 `MATECLAW_E2E_ALOUDATA_LIVE=true`。

**执行约定：** 09 的 seed、Playwright 和 cleanup 必须指向同一轮本地模拟服务与工作区；先完成非 Aloudata 场景，再运行 JDBC+模拟 Aloudata。E2E 启动入口会在本地模拟容器仍运行时 fail-fast，避免同端口下创建半套容器，并对健康探测设置单次超时；E2E 完成后执行 `docker compose -f docker-compose.test.yml down -v --remove-orphans`，再按外部前置计划恢复本地模拟。缺少 JWT/工作区等测试上下文时显式记录 `BLOCKED`，不将未执行标为通过。

**本轮复验记录（2026-09-13）：** 本地模拟 E2E Compose 全量 `9 passed (36.2s)`；覆盖 JDBC+模拟 Aloudata、API+文件、ECharts 绑定、旧 Schema、错误/取消/超时/资源限制和 ObjectRef。随后 DataAgent `152/152`、当前工作树 Runner `21/21`、UI `27/27` 和生产构建均通过。本地开发 Gate 已通过；真实 Aloudata 结果查询作为后续环境联调证据单独跟踪。

**结果绑定聚焦复验（2026-09-13）：** `dataset-result.spec.ts` 与 `dashboard-schema.spec.ts` 单独执行为 `2 files / 4 tests passed`，直接验证脚本行集到 Table/ECharts 的映射和 `scriptBindings` 兼容保留。

**视觉证据边界：** 候选 SHA 已通过 Table 和 ECharts 的 CDP/Canvas 页面证据；VIS-UI04 的本地模拟部分已完成，真实 Aloudata 授权场景在后续环境联调时补采。

## 前端产品闭环缺口（2026-09-14）

> 2026-09-14 实施进展：数据集已具备正式路由和配置中心入口；属性面板与脚本输入已增加明确的模式说明，用户可区分“直接指标绑定”和“脚本结果数据集输入”；`e2e/dataset-management-entry.spec.ts` 已覆盖配置中心→数据集→新建→文件来源→取消返回及洞察→新建仪表盘→组件→数据集→脚本→保存绑定的正式前端路径。本地模拟 Compose/JWT 环境已执行通过，正式外部数据源联调仍是后续 Gate。

| 编号 | 级别 | 问题 | 复现步骤 | 预期结果 | 当前结果 |
| --- | --- | --- | --- | --- | --- |
| FE-CLOSE-03 | P0 | 仪表盘存在割裂的两套数据模型 | 进入“洞察 → 编辑仪表盘”，选中卡片或图表，查看属性面板的数据源下拉，再向下查找脚本数据集输入 | 清楚区分“直接指标绑定”和“脚本结果绑定”，或统一为一种数据集心智；可直接选择已创建的 JDBC/API/文件数据集 | 已明确两种模式：直接指标绑定与脚本结果数据集输入；脚本面板显示目标组件、输入别名和 `datasets.read`，可发现并保存，已由本地模拟 E2E 验证 |
| FE-CLOSE-07 | P0 | E2E 绕过正式用户创建链路 | 查看 `mateclaw-dataagent-ui/e2e/` 的测试准备和起始页面 | 浏览器从主导航完成“配置 → 创建连接 → 创建数据集 → 仪表盘选择 → 脚本处理 → 预览” | 本地模拟已由真实 Chrome channel E2E 覆盖洞察入口新建仪表盘、添加表格、选择 E2E HTTP 数据集、填写脚本并保存绑定；正式外部数据源联调仍待环境条件 |
| FE-CLOSE-10 | P1 | 数据模式、创建入口和绑定结果不够可发现 | 在右侧属性栏选择数据并应用脚本结果，然后重新进入页面 | 明确显示当前模式、输入数据集、目标组件绑定，可创建、修改和解除绑定 | 已补充模式说明和“结果绑定组件”选择器，并在已有 `scriptBindings` 时回显目标；新建组件默认目标、创建与保存绑定已由本地模拟真实 E2E 验证 |

验收必须新增一条不通过业务 API seed 代替页面操作的产品 E2E。Seed 只允许准备 MySQL、WireMock、MinIO 等外部模拟 fixture；数据源、数据集、绑定和仪表盘配置必须由正式前端完成。

**ECharts 本地运行时复验（2026-09-13）：** 已将 ECharts 绑定 Dashboard 固化到 `seed-dashboard-mvp.sh`，导出 `MATECLAW_E2E_ECHARTS_DASHBOARD_ID`，并在候选 SHA 的完整 Playwright 中验证从列表进入预览页实际检测到 `chartWidgets=1`、`canvasCount=1`。

**本轮 CDP 视觉复验（2026-09-13）：** 在候选 SHA 的同一轮 E2E seed 和系统 Chrome 下，通过 Chrome DevTools Protocol 实际采集仪表盘列表、编辑器和最终结果页：AX 树分别为列表 567、编辑器 500、Table 结果 617、ECharts 结果 88 个节点；编辑器可见“脚本数据集输入”“插入读取模板”“最终结果预览”，Table 结果页为 5 行且包含 `120.5`，ECharts 结果页 `canvasCount=1` 且标题可见。截图保存到 `/tmp/mateclaw-dashboard-cdp`。

CDP 入口追加 ECharts 结果页：同一轮 seed 下采集 `dashboard-echarts-preview.png`，AX 节点 `88`，`canvasCount=1` 且图表标题可见；入口现在要求同时注入 `MATECLAW_E2E_ECHARTS_DASHBOARD_ID`。

**可复用入口：** 在 `mateclaw-dataagent-ui` 目录执行 `npm run test:e2e:cdp`（脚本为 `e2e/cdp-dashboard-visual-check.mjs`），将列表、编辑器和最终结果页的 CDP 截图/AX 树采集固化；认证仍由调用方环境注入，默认截图目录为 `/tmp/mateclaw-dashboard-cdp`。

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
- [x] **异步状态基础展示**：最终预览已覆盖运行中、取消、失败和重试入口，当前工作树 27 个 Vitest 用例和 production build 通过。
- [x] **真实 DataAgent/Runner 验收（本地模拟范围）**：同一轮 E2E Compose、JWT、seed 状态和系统 Chrome channel 执行全量 Playwright，`9 passed`；覆盖 JDBC+模拟 Aloudata、API+文件、ECharts 绑定、大结果 ObjectRef、旧 Schema、脚本失败/重试、取消/重试、超时/重试和资源限制/重试。该结果证明本地模拟闭环，不替代真实 Aloudata 授权验收。
- [x] **Step 4: 确保参数 UI 只配置作用范围；脚本通过 `datasets.read` 选择字段**。
- [x] **Step 5: 运行 UI test 和 build**，Expected: PASS（当前工作树 27 个 Vitest 用例通过，生产构建通过）。
- [x] **Step 6（测试入口）**：已加入 `playwright.config.ts`、两组 E2E 文件（覆盖 JDBC+Aloudata、API+文件、ECharts 绑定、旧 Schema、错误、取消、超时和资源超限）和 `test:e2e` 脚本；用例只接受真实 DataAgent/Runner，通过 `MATECLAW_E2E_TOKEN`、`MATECLAW_E2E_WORKSPACE_ID` 和已 seed 的 Dashboard ID 注入环境。完整 seed 产生 9 个 Dashboard，其中 JDBC+Aloudata 可选择真实数据集或 `MATECLAW_E2E_ALOUDATA_MODE=simulation` 自动创建的 WireMock 数据集，其余 8 个可在无 Aloudata 授权时独立运行；不使用 route mock。API+文件脚本对 API 与文件都显式传入 `status=PAID` 过滤，WireMock mapping 强制校验 query 参数，确保 E2E-02 能证明参数透传和文件过滤。Vite 开发端口和 DataAgent 代理目标支持 `VITE_DEV_PORT`、`VITE_DATAAGENT_PROXY_TARGET` 覆盖，默认值保持 `5174`/`http://localhost:18089` 不变。Aloudata 用例缺少授权时显式报 `BLOCKED`，不使用 `test.skip`。
- [x] **Step 7: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

### Task 3: 真实闭环验收

**Files:**
- Modify: `docker-compose.yml`
- Create: `docker-compose.test.yml`
- Create: `mateclaw-dataagent/src/test/resources/e2e/mysql/init.sql`
- Create: `mateclaw-dataagent/src/test/resources/e2e/wiremock/mappings/orders.json`
- Create: `mateclaw-dataagent-ui/e2e/fixtures/orders.csv`
- Create: `scripts/e2e/start-dashboard-mvp.sh`, `scripts/e2e/seed-dashboard-mvp.sh`, `scripts/e2e/export-dashboard-mvp-env.sh`, `mateclaw-dataagent-ui/e2e/cdp-dashboard-visual-check.mjs`
- Create: `scripts/e2e/verify-dashboard-mvp-cleanup.sh`
- Create: `docs/superpowers/evidence/dashboard-mvp-acceptance.md`

**Interfaces:**
- Consumes: G0 验收矩阵；produces each requirement's current commit SHA and evidence。

- [x] **Step 1: 启动 Docker Compose，并验证 DataAgent、MinIO、Runner 健康状态**：独立 `docker-compose.test.yml`、MySQL 初始化数据、HTTPS 8443 WireMock fixture 和前端测试容器已启动并通过健康检查；DataAgent 使用 WireMock 只读 truststore 建立 TLS；显式配置 Flyway baseline 和关闭迁移占位符替换以兼容空 E2E 数据库中的 fixture 表；证据见 `docs/superpowers/evidence/dataagent-compose-internal-connectivity-2026-09-12.md` 和 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`。
- [x] **Step 2a: 完成 API+文件双源预览并记录过滤/透传报告**：已通过真实栈验证并记录；不提供 Aloudata 数据集 ID 时可由独立 seed/cleanup 重复执行，脚本对 API 与文件均显式传入 `status=PAID` 过滤。
- [x] **Step 2b（本地模拟）**：使用 `MATECLAW_E2E_ALOUDATA_MODE=simulation`、E2E HTTPS WireMock 的 `local_sales_view` 和 JDBC fixture 完成页面双源预览；脚本草稿与结果表均验证 `region=east` 的 5 行结果，Playwright `1 passed`。该项只关闭本地开发验证，不关闭真实 ALO-X02；真实环境仍因 `SM_02_0038` 保持 `BLOCKED`，需取得远端筛选/下推证据后再关闭。
- [x] **Step 3a: 完成 DataAgent/Runner 控制面与旧工具通道复验**：真实 Compose + Playwright 已验证任务取消、脚本错误/重试、脚本超时/重试、资源超限/重试、旧 Schema、API+文件和大结果 ObjectRef；Runner 控制面已补齐后台异常到 `FAILED` 的终态收敛，并以定向 E2E 复验通过；cleanup 已增加跨工作区读取 HTTP 403 的真实权限验证。大结果用例确认 DataAgent 结果接口返回 `inline=false`/`outputRef` 并限制为 10 行预览。旧执行器与 `PythonAnalysisTool` 的 `ToolCallback` 已在真实 `python3` 容器中完成兼容复验。
- [x] **Step 3b（范围决策）：平台内 AI 生成不纳入本期**：本项目不负责自动生成 SQL/Python，用户可在外部使用大模型生成草稿后粘贴；本期只验证用户脚本的校验、预览、执行和旧 `ToolCallback` 兼容，不要求真实 LLM/Agent 对话环境。
- [x] **Step 4: 运行完整本地验证；证据必须绑定同一候选 SHA**：`fc799a85414520a4118b36d736f01984b773255e` 上 Playwright `9 passed`，CDP 列表/编辑器/Table/ECharts 四页均成功；真实 Aloudata 结果查询另列为后续 `EXTERNAL-BLOCKED` 联调项。

```bash
make dashboard-dataagent-test
uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q
npm --prefix mateclaw-dataagent-ui test -- --run
npm --prefix mateclaw-dataagent-ui run build
docker compose config --quiet
scripts/e2e/start-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui exec -- playwright install chromium
scripts/e2e/seed-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui run test:e2e
scripts/e2e/verify-dashboard-mvp-cleanup.sh
```

每轮 E2E 开始前应先执行 `scripts/e2e/start-dashboard-mvp.sh`。该入口会清理专属 MySQL/MinIO/UI 依赖卷、构建并启动测试栈，等待 DataAgent actuator 和 UI `5174` healthcheck 均可用；随后运行 seed，并可用 `eval "$(MATECLAW_E2E_WORKSPACE_ID=1 scripts/e2e/export-dashboard-mvp-env.sh)"` 一次性导出同一 state 文件的全部 Dashboard ID，再运行 Playwright，避免手工漏配导致入口 fail-fast。该导出脚本只输出资源 ID，不读取或持久化认证值。UI healthcheck 只证明 HTTP 首页可访问，页面行为仍必须由 Playwright 断言。

Expected: 所有测试、构建和 Playwright 用例退出码为 0，两个健康检查返回健康状态；双源场景的请求、响应、下推报告、trace/video 和页面截图记录到证据文档。

> HTTP/API E2E 测试服务必须提供可验证的 HTTPS 端点，并满足生产 `HttpApiRequestPolicy` 的公开地址/allowlist 校验；不能直接把 Docker 私网 WireMock 地址当作生产策略的例外。若使用本地测试服务，必须通过独立测试证书和显式测试配置完成 TLS 验证，测试配置不得进入默认或生产 profile。

- [x] **Step 5: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

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

**2026-09-14 双源快照问题关闭：** 失败原因是右侧属性面板布局裁剪及字段描述异步完成时机，不是 JDBC/Aloudata 查询结果错误。修复 `.editor-property` 溢出策略和输入描述等待后，JDBC+Aloudata 场景实际断言 5 行、包含 `120.5`，快照更新后非更新模式通过；API+文件、大结果 ObjectRef、ECharts 及错误/兼容场景一并纳入本轮 11 条真实 E2E 回归并全部通过。CUA 请求头策略故障仍独立记录，不用静态截图替代交互视觉证据。

**2026-09-14 正式创建链路补充：** 新增组件后自动设置脚本结果默认目标，避免脚本面板出现“未选择目标组件”而无法形成绑定。真实 Chrome channel 用例已覆盖新建仪表盘、添加表格、选择 E2E HTTP 数据集、填写 `datasets.read` 草稿、保存并确认绑定输入；本轮相关 E2E 总计 `12 passed`。

**2026-09-14 CDP 复验：** 用户 Chrome 的新建仪表盘页面拖入“数据表格”后，脚本结果面板即时显示默认目标组件；未保存的临时仪表盘随后已清理，不影响 seed 数据。

**2026-09-14 提交后复验：** 当前提交 `58d52abdb505b5d611d9e9e4a5b28fda88599538` 通过 Google Chrome CDP `9222` 重新打开双源 Dashboard；编辑器 AX 树 `532` 节点，脚本面板、默认目标组件和输入别名均可见。截图 `/tmp/mateclaw-cdp-current-dashboard-list.png`、`/tmp/mateclaw-cdp-current-dashboard-editor.png`。

**2026-09-14 当前提交 E2E 回归：** 当前提交 `f22648bccb381f72767a847fb0df4df3405cc53f` 在本地模拟栈以系统 Chrome channel 执行三组真实 E2E，共 `12 passed`；覆盖双源快照、结果数据、正式创建/绑定和错误/兼容重试链路。

使用 CDP 连接当前候选 SHA 的真实仪表盘 UI，执行 VIS-UI03～VIS-UI08；最低使用 `Page.captureScreenshot`、`Runtime.evaluate` 和 `Accessibility.getFullAXTree`，每个场景必须保存截图，并将页面、任务、查询和下推证据关联到同一候选 SHA。

- 编辑器：输入别名、参数作用域和脚本配置层级清晰；非法输入在提交前显示可读错误，脚本模板使用配置的别名。
- 输入预览：覆盖初始/空态、加载、成功和失败；字段、类型、行数限制和错误提示可读，输入预览与最终预览视觉上可区分。
- 最终预览：成功结果正确映射到 Table/ECharts；大结果明确显示 `outputRef` 和受限预览边界，不把大结果完整注入页面。
- 执行状态：运行中、取消、失败、重试状态的按钮和提示互斥；取消后不得显示成功，失败后可以重新提交且 loading 正确收敛。
- 错误与兼容：权限拒绝、超时、资源超限和 `SM_02_0038` 显示明确错误分类；旧 Schema 可打开且无脚本绑定时不误触发 Runner。
- 双源页面：JDBC+Aloudata、HTTP/API+文件均展示正确结果、来源/过滤关系和下推报告；截图中的结果必须能由任务 ID 和查询证据复核。

预期：VIS-UI03～VIS-UI08 全部 PASS；每条截图记录 `candidateSha、url、viewport、browser、timestamp、cdpActions、screenshotPath`，并与 E2E-01～E2E-06 或对应 UI 用例关联。Aloudata 未授权时，相关场景只能记录 `BLOCKED`，不能以截图或空结果代替通过。

**本轮实测结果（2026-09-13，历史问题已修复）：** `VIS-UI03、VIS-UI04、VIS-UI05、VIS-UI07、VIS-UI08` 的本地模拟证据均已通过；`VIS-UI06` 的跨工作区权限拒绝仍为 `NOT_RUN`，真实 Aloudata `SM_02_0038` 继续 `EXTERNAL-BLOCKED`。双源 Table 最小高度、受限结果 `outputRef` 和快照基线均已修复，详见统一验收记录。

**当前工作树 CDP 复验补充（2026-09-14）：** 当前修复后的非交互 CDP 脚本已生成四页截图；用户 Chrome 的 `9222` 页面可见 9 个看板。全量真实 Playwright 9 用例取得双源 JDBC+Aloudata 的 5 行和 `120.5`，视觉快照差异 `1158 pixels (ratio 0.01)` 已通过重新生成当前渲染基线并以非更新模式复跑关闭。CUA 后续复验受本地 DataAgent 未监听 `18089` 影响，需服务恢复后继续；不以失效登录页替代产品通过证据。

**2026-09-14 主题快照修复与回归补充：** 数据源配置表单基础容器、头部、卡片、标题、标签和辅助文案已改用主题令牌，新增 `light`、`warm`、`eye-care`、`dark` 四主题真实 Chrome channel 用例；同时覆盖 Chrome `:-webkit-autofill` 的背景、文字和光标样式。修正 Playwright 设备配置覆盖浏览器 channel 的问题，并校正双源结果断言读取位置；提交 `4ae6e7ac5db3a9a2c32349bf67b503668949cca6` 的 UI 单测为 `37 passed`、完整真实 E2E 为 `15 passed`，未使用跳过机制。CDP dark 主题截图 `/tmp/mateclaw-cdp-datasource-form-dark-autofill-final.png`，现场视觉与主题令牌一致。非 Chrome 浏览器原生控件外观及真实 Aloudata 授权仍不关闭对应外部 Gate。

**2026-09-14 Descriptor Chrome CDP 复验补充：** 在 Google Chrome CDP `9222` 的双源编辑器中点击两个输入的“查看字段”，实际显示 JDBC `5 个字段`、Aloudata `3 个字段`，并显示脚本目标组件 `e2e-table`；VIS-UI02 历史的 Descriptor `0 个字段` 与预览字段不一致问题在本地模拟环境已复验关闭。截图 `/tmp/mateclaw-cdp-dashboard-descriptors-current.png`；真实 Aloudata 结果授权仍为外部 Gate。

**提交后回归补充（2026-09-14）：** 当前提交 `00c772d8c315b941b2e0171fd26f53fd12b19416` 的 UI 全量 `9 files / 35 tests passed`、production build 和 `DESIGN-PASS` 均通过；既有 Playwright 快照基线修复结论保持不变。Chrome CUA 重试返回 `Unable to load browser request-header policy`，当前不新增交互视觉证据。

### 本轮逐用例验收记录（2026-09-13）

验收环境：当前工作树 HEAD `0c1b066f6f5059fcf8930294c111707e8d39c91f`、本地模拟 E2E 服务、工作区 `1`、Google Chrome viewport `1440x736`。持久化 CDP 截图位于 `/tmp/mateclaw-dashboard-cdp-current`，包括 `dashboard-list.png`、`dashboard-editor.png`、`dashboard-preview.png`、`dashboard-echarts-preview.png`；同轮 CDP AX 节点数为 `567/500/617/88`（列表/编辑器/Table/ECharts）。

| 用例 | 结果 | 页面操作与实际结果 |
| --- | --- | --- |
| VIS-UI03 | `PASS`（本地模拟） | 编辑双源 Dashboard，将输入别名改为 `bad alias`，提交前显示别名规则错误；添加参数后显示参数名、类型 `string`、作用域“仪表盘”；点击“插入读取模板”后脚本使用当前合法别名。 |
| VIS-UI04 | `PASS（本地模拟，修复后）` | 编辑器“最终结果预览”显示 5 行双源结果；大结果显示 10 行受限表格和 `outputRef` 边界提示；ECharts 预览页 `canvasCount=1` 且标题可见。 |
| VIS-UI05 | `PASS`（本地模拟） | 取消场景运行中显示“取消执行”，取消后显示“执行已取消，可点击‘重试’重新运行”；失败、超时场景分别显示 traceback/`task timed out` 和“重试”，资源超限显示 `OUTPUT_LIMIT` 和“重试”；未观察到 loading 与成功态残留。 |
| VIS-UI06 | `PARTIAL` | 脚本失败、超时、资源超限页面错误分类清晰并可重试；真实 Aloudata `SM_02_0038` 因无授权保持 `EXTERNAL-BLOCKED`。跨工作区权限拒绝页面因当前账号只有一个 `Default` 工作区，未在 Chrome 中执行，记为 `NOT_RUN`。 |
| VIS-UI07 | `PASS`（本地模拟） | 旧 Schema Dashboard 可打开，页面显示“该仪表盘暂无组件，请先编辑添加组件”，提供“返回/去编辑”，未因无脚本绑定误触发 Runner。 |
| VIS-UI08 | `PASS（本地模拟，修复后）` | JDBC+Aloudata、HTTP/API+文件在编辑器和仪表盘预览页均展示可见 Table 行；`.table-wrapper` 最小高度修复后截图不再空白，ECharts 结果另有 canvas 证据。 |

#### VIS-UI04、VIS-UI08 问题复现

1. 启动本地模拟服务并使用工作区 `1` 的 `admin` 登录。
2. 在 `洞察` 中分别打开 `E2E JDBC + Aloudata Dashboard` 和 `E2E API + File Dashboard` 的“预览”。
3. 等待结果加载，用 DOM 检查 `.el-table__body` 或 `table`，确认存在 4/5 行；读取对应元素的 `getBoundingClientRect().height`，结果为 `0`，页面截图只显示空白结果卡片。
4. 返回编辑器打开 `E2E Large Result Dashboard`，点击“最终结果预览”，确认页面只展示 10 行截断数据，没有 `outputRef` 或受限预览边界提示。

预期：Table 行、列名、双源结果和来源/过滤关系在画布中可见；大结果明确展示 `outputRef` 和受限预览边界。实际：Table 用户不可见，且大结果边界未向用户说明。

定位线索：`DataTableWidget.vue` 的 `.table-wrapper` 使用 `flex: 1`，内部 Element Plus 表格使用 `height="100%"`；预览父容器未提供有效计算高度时，二者最终没有可见高度。该线索仅供后续修复 agent 定位，本计划未修改业务实现。

## 2026-09-14 Descriptor 空 Schema 中间态修复

- `DatasetInputPanel.vue` 不再在 Descriptor 空 Schema 的异步探测阶段显示“0 个字段”，改为“字段探测中…”并保留刷新入口；受控输入预览完成后再展示推断出的字段。
- `DatasetInputPanel.spec.ts` 新增中间态回归用例，验证空 Schema 与未完成预览期间的文案不误导；定向测试 `8/8` 通过。
- Chrome CUA 已复验数据集管理及新建数据集路由可见；当前工作区没有可授权数据集，Descriptor 中间态无法通过现场数据触发，需在模拟数据集注入后补做交互截图。

## 2026-09-14 脚本结果绑定目标显式选择

- `DatasetInputPanel.vue` 新增“结果绑定组件”选择器，候选项来自当前页面的 KPI、图表和表格组件；执行结果不再隐式依赖属性面板当前选中项。
- 编辑器加载已有 `scriptBindings` 时回显首个绑定目标；用户确认“应用到组件”后写入既有 `scriptBindings`，保持旧 Schema 兼容。
- CUA 编辑器 AX 树已看到目标选择器和占位提示；当前工作区没有可选数据组件，因此仅验证入口可见性，具体选项选择待注入模拟组件后补验。
