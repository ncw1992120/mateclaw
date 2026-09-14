# Aloudata 指标视图 Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 只读接入已有 Aloudata 指标视图，并将目录、详情、结果和错误转换为统一数据集契约。

**Architecture:** 复用 `AloudataApiClient`、`AloudataEndpointService` 和数据库中的端点配置；新增专用 Service 与 Adapter。AnyMetrics 负责目录/详情。无临时筛选时优先调用指标视图结果接口；有临时筛选时先探测部署版本能力，接口不能接收筛选条件时，由服务端把指标视图定义转换为等价的 Semantic `metrics/query` 并合并受限条件。MateClaw 不调用视图写接口。

**Tech Stack:** Java 21、Spring RestTemplate、JUnit 5、MockRestServiceServer。

**Spec:** `docs/策略解读/design.md` 第 4.1 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 4 节（ALO-U01～U08、ALO-C01～C02、ALO-X01～X02）。

**当前状态（2026-09-13）：** 本地目录、详情、结果 Adapter 和自动化测试已完成并通过；本地模拟开发 Gate 已关闭。ALO-X02 的真实结果查询因当前认证上下文返回 `SM_02_0038`，列入后续环境联调，不阻塞本地实现。

**本地模拟：** 日常开发使用 `dev-support/local-simulation/` 的 WireMock 脱敏响应；正式测试前再替换产品层/语义层地址、连接级 `tenantId` 和 Secret 管理的认证值。产品层必须使用 HTTPS；语义层按部署网络可使用 HTTP 或 HTTPS，HTTP 仅限明确受控的内部可信链路。

**开发验证配置：** 使用 `.env.aloudata-simulation.example` 的 `local-tenant`、`local_sales_view`、`datasourceId=9001` 和 `region=east`；通过 WireMock 的 tree/detail/query/metrics 响应验证目录、详情、结果和筛选编译，不将该结果计入真实 ALO-X02。

**本地 Adapter 联调结果（2026-09-13）：** 已使用上述模拟变量运行 `AloudataAnalysisViewExternalIT`，目录、详情、模拟基线结果和 `region=east` 远端筛选均通过；该结果作为当前本地开发验证。真实 ALO-X02 另行作为环境联调项跟踪。

**执行约定：** 先运行 `make dashboard-prerequisites-simulation`，再执行 Adapter 定向测试；真实地址、认证值和视图授权仅在外部环境变量齐备后联调。

## Global Constraints

- 数据源连接中的默认 `tenantId` 是唯一查询上下文。
- `SM_02_0038` 映射为 `VIEW_ACCESS_DENIED`，不能变成空结果。
- 真实认证值只从加密配置读取，不写测试、日志或文档。
- 只有远端实际收到并执行的条件才标记为 `pushedFilters`；不支持的条件必须拒绝或标记为 `residualFilters`，不能静默在 JVM 中全量过滤。

---

### Task 1: 目录与详情读取

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/AloudataAnalysisViewSummary.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/AloudataAnalysisViewDetail.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/AloudataAnalysisViewService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/AloudataAnalysisViewServiceImpl.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentDatasourceController.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/AloudataAnalysisViewServiceTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/AloudataAnalysisViewControllerTest.java`

**Interfaces:**
- Produces: `listTree(Long datasourceId)`、`getByName(Long datasourceId,String viewName)`；对外提供 `GET /v1/datasources/{id}/analysis-views` 和 `GET /v1/datasources/{id}/analysis-views/{viewName}`。
- Consumes: `analysis_view_tree`、`analysis_view_query_by_name` endpoint definitions。

- [x] **Step 1: 写失败测试**：验证目录树、指标/维度、`timeConstraint`、`filters`、`resultFilters` 映射；响应不得泄露认证 Header。
- [x] **Step 2: 运行**：测试先因 Service 类型不存在而失败。
- [x] **Step 3: 实现最小 Service**：通过 `callWithParams` 调用现有端点，不复制 URL 拼接和认证逻辑。
- [x] **Step 4: 增加只返回已授权视图且不泄露认证配置的只读 Controller，并重跑测试**，Expected: PASS；Service 测试和 Controller 契约测试均通过（当前 Controller 测试覆盖委托、只读工作区注解及完整数据源路由安全矩阵）。
- [x] **Step 5: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

### Task 2: 结果读取与错误分类

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/AloudataAnalysisViewResult.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/AloudataMetricQueryRequest.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/AloudataAnalysisViewQueryCompiler.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/AloudataAnalysisViewAdapter.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/AloudataAnalysisViewAdapterTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/AloudataAnalysisViewExternalIT.java`

**Interfaces:**
- Produces: `DatasetSourceAdapter.describe/read` for `ALOUDATA_ANALYSIS_VIEW`，调用时必须传入 `DatasetAccessContext`。
- Consumes: `analysis_view_query_data`、`metrics_query`；filters 只能映射到视图 Schema 声明的时间、维度或指标结果条件。
- External test configuration: `ALOU_DATA_EXTERNAL_TEST=true`、`ALOU_DATA_PRODUCT_BASE_URL`、`ALOU_DATA_SEMANTIC_BASE_URL`、`ALOU_DATA_TENANT_ID`、`ALOU_DATA_AUTH_TYPE`、`ALOU_DATA_AUTH_VALUE`、`ALOU_DATA_TEST_DATASOURCE_ID`、`ALOU_DATA_TEST_VIEW_NAME`、`ALOU_DATA_TEST_FILTER_COLUMN`、`ALOU_DATA_TEST_FILTER_VALUE`。外部探测通过环境变量注入临时认证上下文；生产 Adapter 仍只从数据源加密配置读取认证值，任何日志和文档不得记录认证值。任一变量缺失时测试必须 fail fast，不能 skip。

- [x] **Step 1: 写失败测试**：覆盖无筛选直查、带筛选时由视图定义编译 `metrics/query`、不支持的筛选、空结果、`SM_02_0038`、HTTP 超时、服务 5xx 和超过最大行数。
- [x] **Step 2: 运行**：测试先因 Adapter 和编译器不存在而失败。
- [x] **Step 3: 实现查询编译、结果列到 `DatasetColumn`/`DatasetBatch` 的转换和强制 `pageSize` 上限**；无筛选结果接口将行偏移换算为 Aloudata 的零基 `pageIndex`，非 `pageSize` 对齐的偏移显式拒绝，避免把行偏移误当页号或静默错位。
- [x] **Step 4: 重跑 Adapter 测试**，Expected: PASS；当前 9/9 通过，包含 `SM_02_0038` 权限拒绝映射、页号转换和非对齐偏移拒绝回归用例。新增 `AloudataAnalysisViewExternalIT` 作为显式外部探测入口，缺少环境变量时会直接失败而不是跳过。
- [ ] **Step 5（后续环境联调，不阻塞本地开发）：使用已授权视图做只读联调**：目录和详情已在真实环境取得响应，但当前认证上下文对目录中探测到的视图均返回 `SM_02_0038`（视图访问拒绝），因此 5 行结果和维度筛选待外部授权后补验。证据记录见 `docs/superpowers/evidence/aloudata-analysis-view-live-probe-2026-09-12.md`。获得已授权视图后再验证远端筛选生效，凭据继续只从环境或加密配置读取，不进入命令历史。该项只影响真实 JDBC+Aloudata 环境证据，不影响本地模拟、API+文件、旧 Schema 和错误路径验收。
- [x] **Step 6: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`；真实 ALO-X02 作为后续环境联调项保留。

## 视觉验收（CDP）

本子计划只提供后端指标视图目录、详情和结果 Adapter，没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。视图选择器和结果页面由 VIS-UI01、VIS-UI02、VIS-UI08 通过 CDP 复核；ALO-X01/ALO-X02 的真实服务证据仍按本计划验收标准执行。

本轮关联结果（2026-09-13）：VIS-UI01 的 Aloudata 数据集选择和认证值脱敏在本地模拟通过；VIS-UI02 因 Descriptor 显示 `0 个字段` 保持 `FAIL`；真实 Aloudata 结果查询因 `SM_02_0038` 保持 `EXTERNAL-BLOCKED`。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=AloudataAnalysisViewServiceTest,AloudataAnalysisViewControllerTest,AloudataAnalysisViewAdapterTest test
ALOU_DATA_EXTERNAL_TEST=true \
  mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=AloudataAnalysisViewExternalIT test
```

预期：mock 测试覆盖 ALO-U01～ALO-C02；外部测试必须实际执行 ALO-X01、ALO-X02，缺少已授权视图或连接配置时以 FAIL/BLOCKED 记录，不能通过跳过测试得到绿色结果。

## 验收标准

- 目录、详情、结果和错误映射自动测试全部通过。
- `SM_02_0038` 只映射为 `VIEW_ACCESS_DENIED`，不会变为空结果或重试成功。
- 至少一个已授权指标视图的维度或时间筛选经真实 Semantic 请求证明在远端生效；否则 Aloudata Gate 未通过。
- 测试输出、应用日志和验收证据均不包含认证值。
