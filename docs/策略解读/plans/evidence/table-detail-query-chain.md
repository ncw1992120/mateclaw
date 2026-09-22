# 表格详情页查询链路 — 联调与验收证据（任务 8 / 9）

- 日期：2026-09-22
- 分支：`feature/dev_fu`
- 执行环境：macOS 沙箱（JDK 21 + Maven 3.9.13 离线仓；Node 22；无 Chrome CDP 9222）

## 任务 8：本地模拟环境和跨端联调

### 已执行（PASS）

| 阶段 | 命令 / 操作 | 结果 |
| --- | --- | --- |
| 后端构建 | `mvn -o -f mateclaw-dataagent/pom.xml -DskipTests package` | PASS（jar 507,569,636 bytes） |
| 后端启动 | `bash docs/策略解读/restart-dataagent-backend.sh`（Aloudata 本地 mock + 开发库） | PASS，health UP（约 8s） |
| 健康检查 | `GET /dataagent/api/actuator/health` | PASS `{"status":"UP"}` |
| 登录链路 | `GET /v1/auth/pubkey` → RSA-OAEP-SHA256 加密 `base64(ts:pwd)` → `POST /v1/auth/login` | PASS，token 签发 |
| 查询计划预览·越权数据集 | `POST /v1/datasets/query-plan/preview`（datasetId=999999） | PASS：404 `数据集不存在`（稳定信封，无堆栈） |
| 查询计划预览·页大小超限 | pageSize=501 | PASS：400 `pagination.pageSize must be between 1 and 500` |
| 查询计划预览·真实数据集 | datasetId=2102326395299090433（ALOUDATA_ANALYSIS_VIEW `table_ab`），queryConfig 草稿来自 descriptor | PASS：code 200；plan 产出 columns=[metric_time, attribution_plan_id, …]，filters=[metric_time in [A,B]]，orders=[attribution_plan_id desc]，pushdown={filters:true,sort:true,pagination:true}；pushdownReport 如实记录 pushedFilters/projection/limit |
| 结果集预览·未知执行 | `POST /v1/insight/dashboards/executions/unknown-exec/result/preview` | PASS：400 稳定信封（无堆栈） |
| 联调脚本 | `node dev-support/local-simulation/scripts/repro/table-detail-query-chain.mjs` | PASS，exit=0，failures=0，requestId=chain-1790096565929 |

修复的联调发现（本轮真实价值）：

1. Jackson 请求体校验异常（如 pageSize 越界、缺 dashboardId）原先映射 500 —— 已在 `DataAgentGlobalExceptionHandler` 增加 `HttpMessageNotReadableException` → 400 稳定信封。
2. `QueryPlanException`（QUERY_CONTEXT_INVALID 等）原先 500 —— 已映射 400 + 稳定错误码前缀。

### 新增模拟资产

- `dev-support/local-simulation/wiremock/mappings/aloudata-metrics-query-sort-page.json`（排序+分页+总数场景，覆盖日期边界/重复维度/total）
- `dev-support/local-simulation/wiremock/mappings/table-detail-query-api.json`（HTTP 源有界残余场景 fixture）
- `dev-support/local-simulation/scripts/repro/table-detail-query-chain.mjs`（跨端联调脚本，阶段化输出 + 退出码）

### NOT_RUN / BLOCKED

| 项 | 状态 | 原因 |
| --- | --- | --- |
| Aloudata + JDBC 双源真实 Join 执行 | NOT_RUN | 需要在开发库预置两个类型齐备的数据集 fixture 并创建隔离仪表盘；本轮以真实 Aloudata 分析视图数据集完成单源计划+Adapter 下推验证，Join 编排由 QueryPlannerTest/ScriptTaskPreparationServiceTest 单测覆盖 |
| HTTP/File 数据集模拟服务联调 | NOT_RUN | WireMock 服务未在沙箱内长驻（进程生命周期受限）；Fixture 与契约测试（QueryPushdownContractTest）覆盖排序残余语义 |

## 任务 9：端到端和 CDP 视觉验收

| 项 | 状态 | 原因 |
| --- | --- | --- |
| Playwright e2e（e2e/table-detail-query.spec.ts） | NOT_RUN | 沙箱无浏览器二进制且无隔离 Playwright 运行环境；用例与 fixture 依赖已写入仓库，需在本地（UI 5174 + DataAgent 18089 + MATECLAW_E2E_DASHBOARD_ID）执行 |
| Chrome CDP 9222 视觉验收（cdp-table-detail-query-visual-check.mjs） | BLOCKED | `connectOverCDP('http://127.0.0.1:9222')` 连接失败：当前环境无以调试端口启动的 Chrome（实测 `nc -z 127.0.0.1 9222` 不通）。脚本已就绪，在用户 Chrome 开启调试端口后运行即可，连接失败会显式输出 BLOCKED 并以退出码 2 结束 |

视觉快照基线（`e2e/table-detail-query.spec.ts-snapshots/`）需在首次真实运行 Playwright 时建立，未以放宽阈值方式伪造通过。

## 结论

- 后端新链路（Planner → Adapter → prepared input → 结果集分页预览）已在本地模拟环境**真实 HTTP 验证通过**。
- 浏览器侧（Playwright / CDP）验证 BLOCKED/NOT_RUN，均给出可复现的运行命令与前置条件，未以静态阅读替代真实验证。
