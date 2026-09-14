# Dashboard MVP 真实验收记录

状态：`PARTIAL-PASS / LOCAL-UI-PASS / ALOUDATA-EXTERNAL-BLOCKED`

本记录只接受同一候选 SHA 下的真实 DataAgent、Python Runner、MinIO、JDBC、HTTP/API、文件和已授权 Aloudata 环境证据；MockMvc、单元测试和 Playwright 用例枚举不能替代真实验收。

## 已准备

### 2026-09-13 候选 SHA 验收（`fc799a85414520a4118b36d736f01984b773255e`）

- 独立 E2E Compose 使用同一轮 seed、JWT、系统 Chrome 和本候选 SHA，完整 Playwright `9 passed (34.8s)`；覆盖 JDBC+模拟 Aloudata、API+文件、ECharts、旧 Schema、错误/取消/超时/资源限制和 ObjectRef。
- CDP 视觉入口 `npm run test:e2e:cdp` 成功生成列表、编辑器、Table 结果和 ECharts 结果四页截图；AX 节点数为 `567/500/617/88`，ECharts 结果页 `canvasCount=1`、标题可见。截图目录：`/tmp/mateclaw-dashboard-cdp`。
- 同一候选 SHA 的 DataAgent `152/152`、Runner `20/20`、UI `24/24`、production build、设计门禁和本地前置条件门禁均通过。该候选只关闭本地模拟验收，不关闭真实 Aloudata ALO-X02。

### 2026-09-13 本地 E2E 复验（当前工作树）

- 使用 `docker-compose.test.yml` 启动 MySQL、MinIO、HTTPS WireMock、固定 Python Runner、DataAgent 和 UI；本轮实际 UI 地址为 `http://127.0.0.1:15174`，DataAgent 地址为 `http://127.0.0.1:18189/dataagent/api`。
- 使用本地默认测试账号完成 seed，创建 API+文件、旧 Schema、脚本失败、取消、超时、资源限制和大结果 ObjectRef 场景；未设置 Aloudata 真实授权变量。
- 首次闭环暴露 Python `Filter` 缺少统一契约要求的 `role`，导致 DataAgent 返回 400；随后发现 Runner 构造 `DatasetClient` 时丢弃任务 `parameters`，导致脚本无法读取页面参数。两项均按 TDD 增加回归测试并修复：`Filter` 默认发送 `role=dimension`、支持显式 `measure`，且 `datasets.params` 收到任务参数；Runner 全量现为 `19 passed`。
- 重建 Runner/DataAgent 后，非 Aloudata Playwright 用例 `7 passed`（API+文件、旧 Schema、脚本失败/重试、取消/重试、超时/重试、资源限制/重试、大结果 ObjectRef）；API+文件单独复验 `1 passed`。
- JDBC+Aloudata 用例仍按设计记录为 `BLOCKED`，原因是缺少真实可查询指标视图授权；没有使用 skip 隐藏该阻塞。

### 2026-09-13 参数注入回归

- `TaskExecutor` 单测验证任务参数从 Runner 请求注入 `DatasetClient.params`，脚本可通过 `datasets.params.require(...)` 获取页面/任务参数；参数仍通过任务环境传递，不包含连接凭据。

### 2026-09-13 回归门禁复验

- Docker Maven DataAgent 全量测试退出码 `0`（日志仅含既有依赖告警）。
- Runner `20 passed`；UI Vitest `8 files / 24 tests passed`；UI production build、`DESIGN-PASS`、`EXTERNAL-PREREQUISITES-SIMULATION-PASS` 和 `git diff --check` 均通过。
- 设计样例一致性补强：明确 `datasets` 是 Runner 注入的任务客户端，并在 `verify-dashboard-design.sh` 中拒绝误写成模块导入；设计门禁再次 `DESIGN-PASS`。
- 同步修正 08 子计划标准脚本样例，移除不存在的 `filters.gte/lte` 工厂函数，统一使用 `mateclaw.filters.Filter` 和 `datasets.params`。
- 同步修正 `query-parameter-pushdown-design.md` 的同一示例，并将规格文件纳入样例导入门禁。

- `docker-compose.test.yml`：隔离的 MySQL、MinIO、WireMock、DataAgent、Python Runner 和 UI 测试服务。
- `mateclaw-dataagent/src/test/resources/e2e/mysql/init.sql`：可重复的订单测试数据。
- `mateclaw-dataagent/src/test/resources/e2e/wiremock/mappings/orders.json`：API 测试响应。
- `mateclaw-dataagent-ui/e2e/fixtures/orders.csv`：文件数据集 fixture。
- `scripts/e2e/seed-dashboard-mvp.sh`：通过真实 API 创建数据源、数据集和仪表盘；要求注入真实 JWT、工作区 ID；只有要创建 JDBC+Aloudata 场景时才需要已授权 Aloudata 数据集 ID。
- `scripts/e2e/verify-dashboard-mvp-cleanup.sh`：删除并验证仪表盘、数据集和数据源；当前公共 API 尚无文件对象删除端点，因此会明确报告保留的对象 ID。

## 当前验收状态与尚未取得的证据

> 说明：下方历史记录保留当时的失败与阻塞证据；其中本轮已修复的 UI 问题以“最新验收覆盖”结论为准，不再沿用旧截图或旧字段计数。

### 最新验收覆盖（2026-09-13，当前工作树）

- 使用独立 E2E Compose、同一轮 seed、真实本地 JWT 和系统 Chrome channel 完成 Playwright 全量：`9 passed (27.9s)`；无 route mock、无 skip。
- 使用 Chrome CDP 视觉脚本 `npm --prefix mateclaw-dataagent-ui run test:e2e:cdp` 完成四页采集：列表、编辑器、Table 结果、ECharts 预览；结果目录为 `/tmp/mateclaw-dashboard-cdp`，Table 结果 `rows=5` 且包含 `120.5`，ECharts `canvasCount=1` 且标题可见。
- 通过 Chrome CDP 手工复验：JDBC 输入显示 `5 个字段`，输入预览为结构化表格；HTTP 输入声明 Schema 后显示 `5 个字段`；文件输入在预览后自动补齐为 `5 个字段` 并显示结构化表格；大结果显示 10 行受限预览及 ObjectRef 提示。
- VIS-UI02、VIS-UI04、VIS-UI08 记录的本地 UI 问题已关闭：字段描述不再长期显示 0、输入结果不再使用原始 JSON 大块展示、Table 结果容器有可见最小高度并支持滚动；E2E 截图基线已按实际修复后的 Chrome 渲染重新生成。
- 本轮对应实现：`DatasetInputPanel.vue` 的字段/预览兜底与 ObjectRef 提示、`DataTableWidget.vue` 的最小高度约束、HTTP E2E API 定义的声明式 Schema；UI 单测为 `8 files / 29 tests passed`，production build 通过。
- VIS-UI06（跨工作区/权限拒绝）已有真实 Compose API `403` 证据；真实 Aloudata ALO-X02 仍为外部授权阻塞，不以本地 WireMock 模拟结果替代。

| 用例 | 状态 | 缺失证据 |
| --- | --- | --- |
| Compose 健康检查 | `PASS` | DataAgent actuator `UP`；Runner `/health` `UP`；MinIO/MySQL/WireMock/UI 均 healthy/可访问；DataAgent→Runner 内部访问成功 |
| JDBC + Aloudata 双源 | `BLOCKED` | 需要当前认证上下文下可查询的 Aloudata 指标视图数据集 |
| API + 文件双源 | `PASS` | 候选 SHA `fc799a85` 的真实 Compose + DataAgent + Python Runner + MinIO + WireMock；Playwright 全量中的该场景通过，脚本执行无错误并生成截图 |
| 旧 Schema 兼容 | `PASS` | 当前真实 Compose + JWT + Chrome Playwright 1 passed；旧 Schema 可读且编辑器标题/内容已加载，重新固定截图基线 |
| 脚本错误展示与重试 | `PASS` | 真实 Compose + Playwright 通过，脚本失败信息显示在 `.execution-alert`，并保留“重试”入口 |
| 脚本取消与重试 | `PASS` | 真实 Compose + Playwright 通过，30 秒受控任务可取消，页面显示“执行已取消”并保留“重试”入口 |
| 脚本超时与重试 | `PASS` | 真实 Compose + Playwright 通过，1 秒执行策略终止 5 秒脚本，页面显示超时错误并保留“重试”入口 |
| 脚本资源超限与重试 | `PASS` | 真实 Compose + Playwright 通过，受控 stdout 超过 `maxOutputBytes` 返回 `OUTPUT_LIMIT`，页面保留“重试”入口 |
| Runner 大结果 ObjectRef | `PASS` | 真实 Compose + 宿主 Chrome + JWT 执行通过；执行结果接口返回 `data.inline=false` 和 `data.outputRef`，DataAgent 受控读取 ObjectRef 后页面展示 10 行预览；Playwright 1 passed |
| 跨工作区仪表盘读取拒绝 | `PASS` | 真实 Compose API 携带其他工作区 `X-Workspace-Id` 读取已 seed 仪表盘，返回 HTTP 403；cleanup 在权限检查通过后才删除资源 |

HTTP/API 测试遵守生产 HTTPS/公开地址与 allowlist 校验；测试 Compose 的 WireMock 在启动时生成短期 PKCS12 证书并监听 `https://e2e-http:8443`，DataAgent 通过只读测试 truststore 建立 TLS。没有启用 `MATECLAW_DATASET_HTTP_ALLOW_INSECURE_TEST_ENDPOINT` 例外。

## 执行记录（2026-09-12，工作树候选）

- `docker compose -f docker-compose.test.yml ps`：DataAgent、Runner、MinIO、MySQL、WireMock healthy，UI 可访问。
- `docker exec mateclaw-e2e-dataagent wget -qO- http://python-runner:8080/health`：`{"status":"UP"}`。
- 真实 seed：创建 JDBC SQL、HTTP API、FILE 数据集及七条仪表盘（双源、旧 Schema、错误、取消、超时、资源超限）；脚本中的文件来源使用受控 MinIO 对象引用。
- 历史基线命令 `MATECLAW_E2E_BROWSER_CHANNEL=chrome npx playwright test --grep-invert 'JDBC + Aloudata' --reporter=line`：`5 passed`（API+文件、旧 Schema、脚本错误/重试、脚本取消/重试、脚本超时/重试）。
- 真实 Compose + JWT 定向复验：API+文件 `1 passed`，大结果 ObjectRef `1 passed`；前者截图对动态数据集描述面板做 mask，后者断言统一响应 `data.inline=false`、`data.outputRef` 及 10 行预览。大结果结果接口已设置仪表盘预览上限 10 行。
- 真实 Compose + JWT 全量错误/兼容复验：`5 passed`（旧 Schema、脚本错误/重试、取消/重试、超时/重试、stdout 资源限制/重试）；旧 Schema 截图基线已在等待标题加载后重新固定。
- UI Compose 依赖隔离复验：`docker-compose.test.yml` 使用独立 `e2e_ui_node_modules` 卷后，UI 容器内 `npm ci` 完成并由容器自身 Vite 提供页面；同一容器 UI 地址执行 API+文件与大结果 ObjectRef，Playwright `2 passed`。
- 追加资源超限场景后，真实 `npx playwright test e2e/dashboard-errors-and-compatibility.spec.ts`：`3 passed, 2 failed`；资源超限、取消、超时通过，旧 Schema 截图出现小幅基线差异，脚本错误场景出现 `timeout of 60000ms exceeded`，因此不能把本轮整组结果记为通过。
- 重建包含 Runner 控制面异常收敛修复的镜像后，定向真实 `npx playwright test e2e/dashboard-errors-and-compatibility.spec.ts --grep 'script execution error|stdout resource'`：`2 passed`（脚本异常稳定返回失败、资源超限返回 `OUTPUT_LIMIT`）；随后 cleanup 校验通过并销毁 Compose 专用卷。
- cleanup 脚本新增并执行跨工作区读取验证：以真实 JWT 携带 `X-Workspace-Id: 999999999` 读取已 seed 仪表盘，服务返回 HTTP `403` 后才继续删除，证明资源归属检查未被全局 admin 的粗粒度拦截器绕过。
- JDBC+Aloudata 单独执行：`1 failed`，失败信息为 `BLOCKED: 需要已授权且可查询的 Aloudata 指标视图 ID`；这是外部授权阻塞，不是跳过或代码成功证据。
- 补充 ObjectRef 预览上限和 Aloudata 连接上下文回归后，使用 Docker Maven JDK 21 和 Testcontainers 重新执行：`140 tests, 0 failures, 0 errors, 0 skipped`。
- 前端 Vitest/build：`24 passed`、生产构建通过；Python Runner：`20 passed`（通过 Python 3.13/uv 环境运行，宿主默认 Python 3.8 不满足项目类型标注要求），新增后台 executor 异常必须收敛为 `FAILED`、SDK 在网络请求前拒绝非法过滤条件、兼容 DataAgent `R.ok(...)` 包装响应、识别业务错误以及任务参数端到端注入的回归测试；本轮补充了 Playwright 本机 Chrome channel 兼容配置。
- 旧 Agent Python 兼容回归：Docker Maven JDK 21 定向执行 `LocalCodeExecutorCompatibilityTest` 通过；该测试验证旧 `requirements`/`pip` 调用契约仍保留，但使用受控 `/bin/sh` 解释器，不等价于真实 Python 解释器闭环，因此同候选 SHA 的真实 Agent 调用仍待补充。
- 旧执行器真实解释器复验：同一测试在临时 JDK 21 + Python 3 容器中以 `MATECLAW_LEGACY_REAL_PYTHON=true` 运行，日志确认 `python=python3`，测试通过且旧 requirements 流程仍执行；这证明 LocalCodeExecutor 的真实 Python 边界，但尚未覆盖通过 Agent/Tool 通道触发的端到端调用。
- 旧 Agent/Tool 通道复验：新增 `PythonAnalysisToolIntegrationTest`，验证 `python_analysis` 注册、`execute_python` 参数解析、stdin 传递和结果格式；默认模式通过，临时 JDK 21 + Python 3 容器真实模式也通过，日志确认工具回调使用 `python=python3`。该证据覆盖 ToolCallback 通道，不包含真实 LLM 推理，因此不宣称完整 Agent 对话 E2E。

本轮联调修复：FILE 数据集允许 `datasource_id=NULL`（V220）；文件输入描述不再向不可空 Map 传递 null；Runner 允许 Compose 内部 `mateclaw-dataagent` 回调地址；E2E seed 脚本改用真实多行 Python 脚本字符串。

追加启动验证（2026-09-12）：首次重新启动隔离栈时发现测试 Compose 未显式传递 Flyway baseline/placeholder 配置，DataAgent 在空历史但已有 `orders` fixture 的 MySQL 上退出；已补充 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`、`SPRING_FLYWAY_BASELINE_VERSION=0` 和 `SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT=false`。清理专用卷后重启，Flyway 从 version 0 连续迁移至当前版本，DataAgent actuator、Runner 内部 `/health`、MinIO、MySQL、WireMock 和 UI 均恢复可访问。该修复已通过当前 Compose 健康检查，但尚未替代需要真实 JWT/Aloudata ID 的完整 Playwright 验收。

再次启动验证（2026-09-12）：隔离 Compose 栈重新启动成功，`docker compose ps` 显示 DataAgent、Runner、MinIO、MySQL、WireMock healthy，UI 已启动；容器内 Runner `/health` 和 DataAgent actuator 均返回 `{"status":"UP"}`。

E2E seed/cleanup 可独立运行非 Aloudata 场景：未提供 `MATECLAW_E2E_ALOUDATA_DATASET_ID` 时跳过创建 JDBC+Aloudata 仪表盘；cleanup 改为使用 API+文件仪表盘作为跨工作区权限验证对象，仍执行真实 HTTP 403 检查。API+文件、旧 Schema 和错误路径仍按真实环境变量执行。JDBC+Aloudata 用例本身仍在缺少授权时显式报 `BLOCKED`，不使用 `test.skip`。

E2E-02 过滤覆盖：API+文件脚本通过 `datasets.read(..., filters=[{"field":"status","operator":"eq","value":"PAID"}])` 读取两个来源；WireMock mapping 要求收到 `status=PAID`，CSV fixture 中的 `CANCELLED` 行由文件 Adapter 过滤掉。该约束已通过 JSON、Shell、Compose 和设计门禁校验，待注入真实 JWT 后由 Playwright 记录最终页面结果和透传报告。

追加 fixture 验证（2026-09-12）：HTTPS WireMock 请求不带 `status` 返回 HTTP 404，带 `status=PAID` 返回 HTTP 200 且两行结果全部为 `PAID`；验证输出为 `without_status=404 with_status=200 / WIREMOCK_FILTER_PASS`。这只验证 fixture 的请求约束，完整 DataAgent→Runner→页面链路仍需 JWT E2E。

追加入口门禁复验（2026-09-12）：`bash -n scripts/e2e/seed-dashboard-mvp.sh scripts/e2e/verify-dashboard-mvp-cleanup.sh scripts/verify-dashboard-design.sh` 通过；在未设置 `MATECLAW_E2E_TOKEN`、工作区及 Dashboard ID 的当前环境执行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --list` 时，两个 Playwright 文件均按设计 fail-fast 报“缺少真实 E2E 环境变量 MATECLAW_E2E_TOKEN”，最终列出 `0 tests`。该结果是凭据缺失阻塞，不是测试通过，也不使用 route mock 或 `test.skip` 掩盖阻塞。

追加全量回归（2026-09-12）：当前工作树并行执行 Docker Maven DataAgent 全量测试、Runner pytest 和 UI Vitest，分别返回退出码 0；Runner 输出 `17 passed`，UI 输出 `8 files / 24 tests passed`，DataAgent 无失败/错误（Docker Maven 日志仅含既有 SLF4J、多 Hadoop 及 legacy pip 兼容告警）。

追加构建门禁（2026-09-12）：`npm --prefix mateclaw-dataagent-ui run build` 成功，随后 `bash scripts/verify-dashboard-design.sh` 输出 `DESIGN-PASS`，`git diff --check` 无输出；构建仅保留既有 Rollup chunk size 与 PURE 注释告警。

追加计划一致性复核（2026-09-12）：01/02/03/07/08/09 子计划中未执行的提交项已统一标注为“统一交付节点（待用户确认）”；Aloudata 外部联调、双源 E2E 和候选 SHA 验收仍保持未完成状态，未被误标为通过。

追加 09 子计划状态拆分（2026-09-12）：将原双源预览步骤拆为 `Step 2a`（API+文件，已通过）和 `Step 2b`（JDBC+Aloudata，因 `SM_02_0038` 阻塞），避免已完成场景被整体未完成状态掩盖，同时不提前关闭双源最终 Gate。

追加 Aloudata 测试配置一致性复核（2026-09-12）：明确外部探测的地址、租户和认证上下文通过 `ALOU_DATA_*` 环境变量注入；生产 Adapter 仍从加密数据源配置读取认证值。测试断言和状态摘要不输出认证值，缺少变量时继续 fail-fast。

追加统一读取路由回归（2026-09-12）：`DatasetExecutionServiceTest` 新增按 `DatasetSourceType` 参数化的 `preview/read` 测试，五类首期来源均通过同一个 `DatasetSourceAdapter.read` 契约；Docker Maven 全量结果为 `146 tests, 0 failures, 0 errors, 0 skipped`。

追加统一路由边界回归（2026-09-12）：新增“目录来源无已注册 Adapter 时返回 `INVALID_REQUEST`”测试；随后重跑 Docker Maven DataAgent 全量，结果为 `147 tests, 0 failures, 0 errors, 0 skipped`，该结果 supersede 前述 146 条记录。

追加测试矩阵映射（2026-09-12）：为统一读取与缺失 Adapter 边界测试补充稳定 ID `CAT-U06`、`CAT-U07` 及 `@DisplayName`，并写入测试矩阵和 01 子计划；定向 `DatasetExecutionServiceTest` 已通过，测试数量未增加，当前全量基线仍为 `147/147`。

追加计划入口复核（2026-09-12）：01 子计划的正式测试命令已加入 `DatasetExecutionServiceTest`，预期结果明确包含 `CAT-U06/CAT-U07`，避免统一读取回归被目录契约命令遗漏。

追加 09 子计划状态拆分（2026-09-12）：将原 Step 3 拆为 `Step 3a`（DataAgent/Runner 控制面和旧 `ToolCallback` 兼容复验，已完成）与 `Step 3b`（真实 LLM Agent 对话 E2E，仍待真实模型环境），避免把工具通道测试误当作模型对话验收。

追加来源数量术语复核（2026-09-12）：总体计划明确首期包含五种来源类型、四个 Adapter 实现；其中 JDBC 表与 JDBC SQL 共用 JDBC Adapter，统一契约参数化测试仍覆盖全部五种 `DatasetSourceType`。

追加未完成项审计（2026-09-12）：扫描全部子计划后，除统一交付节点（待用户确认）外，剩余未勾选项仅为 Aloudata 已授权视图联调、JDBC+Aloudata 双源预览、真实 LLM Agent 对话 E2E 及必须绑定候选 SHA 的最终验证；未发现仍可在当前本地环境独立完成而遗漏的实现步骤。

追加计划门禁增强（2026-09-12）：`scripts/verify-dashboard-design.sh` 现同时检查总体计划和 00–09 全部子计划存在且引用测试矩阵；`bash -n` 与 `DESIGN-PASS` 均通过。

追加矩阵 ID 门禁（2026-09-12）：设计门禁新增对 `CAT-U06`（五类来源统一读取）和 `CAT-U07`（无 Adapter 明确拒绝）的必检，防止关键统一执行边界从测试矩阵中回退。

追加来源术语复核（2026-09-12）：01 子计划目标已明确“四类来源、五种来源类型”，其中 JDBC 表与 JDBC SQL 为两个独立来源类型但共用 JDBC Adapter，和总体计划及统一路由测试保持一致。

HTTP 策略补强（2026-09-12）：新增仅固定 `e2e-http:8443` 的 TLS 测试模式；focused `HttpApiRequestPolicyTest` 通过，DataAgent 全量测试命令退出码为 0。明文 HTTP 测试例外仍只由原独立开关控制，Compose 未启用。

策略构造器收敛复验（2026-09-12）：移除为绕开 Spring 冲突而引入的 varargs 构造器，改为普通双布尔构造器与字段配置注入；`HttpApiRequestPolicyTest` focused 测试及 DataAgent 全量测试均退出码为 0。

TLS/明文隔离回归（2026-09-12）：`HttpApiRequestPolicyTest` 额外断言 TLS 测试模式下 `http://e2e-http:8080` 仍被拒绝；focused 测试退出码为 0。

TLS fixture 端口隔离回归（2026-09-12）：同一 TLS 测试模式下 `https://e2e-http:443` 被拒绝，仅 `https://e2e-http:8443` 可通过；focused 测试和 DataAgent 全量测试均退出码为 0。

复现命令：

```bash
docker compose -f docker-compose.test.yml down -v --remove-orphans
docker compose -f docker-compose.test.yml up -d \
  e2e-mysql e2e-http minio python-runner mateclaw-dataagent mateclaw-dataagent-ui
# 等待 mateclaw-e2e-ui 的 5174 端口可访问后再执行 seed 和 Playwright
scripts/e2e/seed-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui run test:e2e
scripts/e2e/verify-dashboard-mvp-cleanup.sh
```

候选 SHA、任务 ID、来源请求/响应、下推报告、页面截图和 trace 应在实际运行后追加，未运行前不得填写 PASS。

## 工作树复验（2026-09-12）

- 总体计划基线命令在 Docker Maven JDK 21 下通过：`mvn -pl mateclaw-plugin-api,mateclaw-server install -Dmaven.test.skip=true`、`mvn -N install -DskipTests` 和 `mvn -f mateclaw-sdk/pom.xml install -Dmaven.test.skip=true` 均返回成功；该证据仅证明当前工作树的构建前置条件可用，不代表已生成候选提交 SHA。
- `bash scripts/verify-dashboard-design.sh`：`DESIGN-PASS`。
- Docker Maven JDK 21 执行 DataAgent 全量测试：`140 tests, 0 failures, 0 errors, 0 skipped`。
- `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q`：历史记录为 `20 passed`；当前标准入口已增加双别名 Join 回归，最新结果为 `21 passed`。
- `docker build -t mateclaw-python-runner:plan-verify mateclaw-python-runner` 构建通过；以镜像实际启动解释器 `/app/.venv/bin/python` 导入 `pandas`、`polars`、`pyarrow`、`fastapi` 成功，容器 UID 为 `10001`，并确认 `uv.lock` 存在。该检查未修改生产镜像标签。
- `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests/test_executor.py -q`：`7 passed`，其中包含 stdout 资源上限终态验证。
- Aloudata Adapter 定向测试：`9 passed`，覆盖 `SM_02_0038` 到 `ACCESS_DENIED` 的映射、行偏移到零基 `pageIndex` 以及非 `pageSize` 对齐偏移拒绝；真实 Aloudata 结果查询仍按 `SM_02_0038` 保持 `BLOCKED`。
- Dataset Catalog 迁移集成测试：真实 MySQL 8.4、PostgreSQL 15.6 容器均从模拟 V217 数据集表迁移到 V220 通过；旧记录默认 `JDBC_TABLE`，`FILE` 数据集的 `datasource_id=NULL` 可写入。MySQL 仅输出 Flyway 版本支持范围警告。
- UI 依赖按 `package-lock.json` 恢复缺失的 Rollup macOS arm64 optional 包后，Vitest：`24 passed`。
- 这次复验仍属于工作树证据，没有生成候选提交 SHA；因此不改变“同一候选 SHA”验收条件，也不解除 Aloudata 结果查询和真实 LLM Agent 对话 E2E 的剩余项。资源超限、取消、超时、旧 Schema 和跨工作区读取拒绝已分别有真实 Compose 证据。

## 追加工作树复验（2026-09-12 14:08，当前工作树）

以下命令均在当前工作树重新执行并取得退出码 0；结果仍属于工作树证据，未生成候选提交 SHA：

- Docker Maven JDK 21：`mvn -f mateclaw-dataagent/pom.xml test -q`，`140 tests, 0 failures, 0 errors, 0 skipped`。
- Python Runner：`uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q`，`20 passed`（1 个上游弃用警告）。
- UI 单元测试：`npm --prefix mateclaw-dataagent-ui test -- --run`，`8 files / 24 tests passed`。

结果绑定聚焦复验（2026-09-13）：单独执行 `npx vitest run src/utils/__tests__/dataset-result.spec.ts src/utils/__tests__/dashboard-schema.spec.ts`（工作目录 `mateclaw-dataagent-ui`），得到 `2 files / 4 tests passed`。其中明确覆盖脚本行集到 Table 的列/行映射、到 ECharts 的分类轴/数值序列映射，以及 `scriptBindings` Schema 保留；因此 Table/ECharts 绑定不是仅由 E2E 页面截图推断。

证据边界：本轮 CDP 页面采集验证了 Table 结果页；ECharts 目前只有上述映射单测证据，尚未取得候选 SHA 的图表组件 CDP/Canvas 证据，VIS-UI04 的 ECharts 子项仍保持待复验。

补充 ECharts 本地运行时证据（2026-09-13）：临时 E2E Compose 的真实预览页实际检测到 `chartWidgets=1`、`canvasCount=1`，并生成 `/tmp/mateclaw-dashboard-cdp/dashboard-echarts-binding.png`。该证据属于当前工作树/本地模拟环境，不替代候选 SHA 视觉验收。
- UI 生产构建：`npm --prefix mateclaw-dataagent-ui run build`，`vue-tsc --noEmit && vite build` 成功；仅有 Rollup 注释和 chunk 大小提示。
- 设计门禁与差异校验：`bash scripts/verify-dashboard-design.sh && git diff --check`，返回 `DESIGN-PASS` 且无 diff 校验错误。

本次复验没有改变 G3 的门禁结论：API+文件等非 Aloudata 场景已有证据；JDBC+Aloudata 结果查询仍需授权指标视图，最终验收仍需在同一候选 SHA 上重跑完整矩阵。

## HTTP/API TLS fixture 复验（2026-09-12 14:15，当前工作树）

- `docker compose -f docker-compose.test.yml config --quiet`：通过。
- `e2e-http` 容器自生成 PKCS12 服务证书和 truststore，健康状态为 `healthy`。
- `curl -k https://127.0.0.1:18443/__admin/health`：返回 WireMock `status: healthy`。
- 完整依赖栈启动后，`e2e-http`、Runner、DataAgent 均为 `healthy`；DataAgent actuator 返回 `{"status":"UP"}`，并可在容器内读取 `e2e-http` truststore 条目。
- `scripts/verify-dashboard-design.sh` 已增加 HTTPS fixture、8443 端口和禁止 insecure exception 的回归检查，当前返回 `DESIGN-PASS`。

该复验只证明 HTTPS fixture、信任链和 DataAgent 启动边界；尚未替代需要 JWT 的完整 HTTP/API+文件 Playwright 流程，也没有生成候选 SHA。

追加依赖描述复核（2026-09-12）：总体计划依赖关系已明确“等四个 Adapter 实现”，与五种来源类型（JDBC 表与 JDBC SQL 共用一个实现）保持一致。

追加测试计数口径复核（2026-09-12）：总体计划当前实施进度已注明 `140/140`、`146/146` 仅为历史快照，当前 DataAgent 全量验收基线为 `147/147`。

追加外部条件清单（2026-09-13）：新增 `2026-09-13-dashboard-external-prerequisites.md`，覆盖 Aloudata 接口/视图授权、JDBC 连接与样本、HTTPS API、四种文件、对象存储、JWT/工作区、Runner/Docker、观测证据和候选 SHA；总体计划已回链，认证值均使用占位符。

追加 Adapter 术语复核（2026-09-12）：01 子计划已将“所有 Adapter”表述统一为“四个 Adapter 实现，覆盖五种来源类型”，并保持 `PushdownReport` 对每种来源如实填写。

## 本地外部依赖模拟复验（2026-09-13）

- `docker compose -f dev-support/local-simulation/docker-compose.yml --env-file dev-support/local-simulation/.env.example config --quiet`：通过。
- `bash -n dev-support/local-simulation/scripts/*.sh`：通过。
- `./dev-support/local-simulation/scripts/start.sh`：成功启动 MySQL 8.4、PostgreSQL 15.6、MinIO、WireMock，并完成 MinIO bucket 初始化和 CSV/JSON fixture 上传。
- `uv run --project mateclaw-python-runner python dev-support/local-simulation/scripts/generate-fixtures.py`：从 canonical CSV 生成可打开的 `orders.parquet` 和 `orders.xlsx`，`file`/`unzip -t` 校验通过；随后 MinIO seed 已上传四种格式 fixture。
- `./dev-support/local-simulation/scripts/check.sh`：通过；MySQL/PostgreSQL/MinIO/WireMock HTTP+HTTPS 健康检查、WireMock 全量/`status=PAID` 响应断言、MySQL/PostgreSQL 各 10 行 fixture 断言均通过。
- MinIO 对象键复验：`files/orders.csv`、`files/orders.json`、`files/orders.parquet`、`files/orders.xlsx` 均存在；不会生成不稳定的 `files/seed/...` 前缀。
- `files/fixtures-manifest.json` 校验：四种样本的文件大小和 SHA-256 均匹配，Schema、`status=PAID` 预期 3 行及 `id` Join 键均已登记。
- HTTPS fixture 复验：`https://127.0.0.1:18443/orders?status=PAID` 返回 3 行，`analysisView/query` 返回 3 行；临时证书只用于本地测试，正式环境必须替换为受信任证书链。
- 根目录前置条件入口 `./scripts/verify-dashboard-external-prerequisites.sh --local`：通过；实际列出 MinIO 四个对象并检查 Runner；`--external` 在未注入真实 Aloudata 条件时按设计返回 `BLOCKED`/退出码 3。
- Make 入口 `make dashboard-prerequisites-local`：通过；`make -n dashboard-prerequisites-external` 可展开真实环境检查命令。
- 本地 Aloudata 闭环入口 `make dashboard-prerequisites-simulation`：通过；固定 `local-tenant`、`local_sales_view`、`region=east` 和 `datasourceId=9001` 仅用于 WireMock。
- OpenAPI fixture `dev-support/local-simulation/api/orders-openapi.yaml`：通过格式/operationId/`status` 参数存在性检查，作为本地 `apiDefinitionId` 契约样例。
- 本地 Runner 复验：复用 `mateclaw-python-runner/Dockerfile` 构建，容器 `/health` 返回 `{"status":"UP"}`；在 `runner_internal` internal 网络中访问 `https://example.com` 时 DNS 解析失败。
- 本地 Aloudata Adapter 外部探测：`AloudataAnalysisViewExternalIT` 使用 `.env.aloudata-simulation.example` 在 WireMock 模拟租户上通过（目录、详情、模拟基线和 `region=east` 筛选结果变化）；不计入真实 Aloudata ALO-X02 验收。
- MySQL/ PostgreSQL 均查询到 10 行订单，其中 3 行为 `PAID`；样例包含 null、Decimal、日期、中文、多种状态和无匹配 Join 键。
- WireMock `/orders`、`/anymetrics/api/v1/analysisview/treeList`、`queryByName`、`analysisView/query`、`metrics/query` 均返回预期脱敏响应。
- `./dev-support/local-simulation/scripts/cleanup.sh` 可删除本地模拟容器、网络和数据卷。

该证据仅覆盖本地模拟依赖，不替代真实 Aloudata 结果权限、正式测试环境对象存储、真实 API 所有者接口或身份权限专项。

范围说明（2026-09-13）：当前 DataAgent Adapter 到 Runner 的输入读取返回受限 `DatasetBatch`；`ObjectRef/Parquet` 已用于脚本大结果上传和页面受限预览。输入侧远程 `ObjectRef` 批读尚未实现，不计入本阶段 PASS，后续需增加专用读取接口、令牌绑定和 SDK 批读取器后再验收。

## 本地模拟基线回归（当前工作树）

- `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q`：`20 passed`。
- `npm --prefix mateclaw-dataagent-ui test -- --run`：历史记录为 8 个测试文件、24 个测试通过；当前新增来源选项、HTTP/API 来源配置及 Aloudata 只读视图回归后为 8 个测试文件、27 个测试通过。
- `npm --prefix mateclaw-dataagent-ui run build`：类型检查和 Vite production build 通过；仅有既有 Rollup chunk 大小提示。
- Docker Maven DataAgent 全量测试（Testcontainers 使用 Docker Desktop socket）：命令退出码 0；日志仅含既有 SLF4J/Mockito/本地旧执行器 pip 兼容告警，无失败测试。
- `make dashboard-prerequisites-simulation`、`bash scripts/verify-dashboard-design.sh`、`git diff --check`：均通过。

追加全量回归（2026-09-13）：使用 Docker Maven JDK 21 和 Docker Desktop Testcontainers 执行 `mvn -f mateclaw-dataagent/pom.xml test -q`，命令退出码为 `0`；本次未发现失败、错误或跳过测试。日志中的 SLF4J、Parquet/Hadoop、Mockito agent 及旧 Local 执行器 `pip` 兼容告警均为既有告警，不改变测试结果。

本地 manifest 校验补强（2026-09-13）：`check.sh` 新增 `fixtures-manifest.json` 的 rowCount、文件集合、字节数、SHA-256、`status=PAID` 预期 3 行契约校验，并通过 MinIO `mc stat --json` 核对四个已上传对象的大小；当前模拟环境检查输出 `fixture manifest checksum/schema contract passed` 和 `MinIO object size contract passed`。

非 Aloudata Compose E2E 复验（2026-09-13）：在清理 `docker-compose.test.yml` 专属容器和卷后，重新构建并启动 DataAgent、Runner、MinIO、WireMock、UI；通过同一轮登录 token 和 seed 状态执行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --grep-invert ...`，7 个 API+文件、ObjectRef、旧 Schema、错误/取消/超时/资源限制场景通过。JDBC+Aloudata 用例按设计显式报告 `BLOCKED`（未提供已授权视图），不计入非 Aloudata 通过数。首次 seed 的 400 根因为残留 E2E MySQL volume 导致数据集名称唯一约束，清理测试专属卷后 seed 成功；本地模拟卷未被删除，已在 E2E 完成后恢复并通过健康检查。

E2E 启动入口补强（2026-09-13）：新增 `scripts/e2e/start-dashboard-mvp.sh`，自动执行测试栈 `down -v --remove-orphans`、`up -d --build`，并轮询 DataAgent `/actuator/health` 与 UI `5174` 端口；脚本实际执行成功并输出 `Dashboard MVP E2E services are ready`。E2E 清理后本地模拟环境已恢复并通过 `check.sh`。

E2E UI 健康检查补强（2026-09-13）：`docker-compose.test.yml` 为 `mateclaw-dataagent-ui` 增加 Node HTTP healthcheck（访问容器内 `127.0.0.1:5174/`，每 5 秒检查，最多 36 次）。执行 `scripts/e2e/start-dashboard-mvp.sh` 后，`docker compose -f docker-compose.test.yml ps` 显示 `mateclaw-e2e-ui ... Up (healthy)`；随后按脚本约定清理 E2E 专属容器/卷并恢复本地模拟环境，`check.sh` 继续通过。该检查使 Compose 状态与前端真实可访问性一致，但不替代 Playwright 页面断言。

E2E Aloudata 模拟入口补强（2026-09-13）：`seed-dashboard-mvp.sh` 新增 `MATECLAW_E2E_ALOUDATA_MODE=simulation`，通过 E2E HTTPS WireMock 创建 `local-tenant`、`local_sales_view` 对应的数据源和 `ALOUDATA_ANALYSIS_VIEW` 数据集，并将 `region=east` 过滤写入 `datasets.read`，用于验证 JDBC+Aloudata 的编排和过滤下推路径；新增 tree/detail/analysis-result/metrics-query WireMock mappings。使用 `MATECLAW_E2E_WORKSPACE_ID=1`、本地 `admin/admin123` 登录执行 seed 成功并生成双源 Dashboard。由于当前工作机缺少 Playwright Chromium（`Executable doesn't exist ... chrome-headless-shell`），本次只记录 seed/后端准备成功，未将页面 E2E 标为 PASS；真实 Aloudata 仍需独立授权验收。

E2E Aloudata 模拟链路调试（2026-09-13）：安装/启用系统 Chrome channel 后，页面用例可启动并真实调用 DataAgent，但先后发现两处内部契约问题：数据库端点 JSON 损坏时关键端点缺少代码级兜底；`AloudataApiClient.callWithParams` 校验前未注入连接级认证 Header。已分别补充核心端点默认配置（tree/detail/result/metrics）和 `tenant-id/auth-type/auth-value` 自动注入，并通过 Docker Maven JDK 21 离线打包、E2E Compose 重建验证。修复后的页面用例尚未重新取得 PASS（仍需下一轮在新镜像上重跑）；本轮失败结果保留为缺陷定位证据，不改变 G3 部分通过结论。

E2E Aloudata 模拟链路继续调试（2026-09-13）：重新构建后，WireMock 请求已进入 `analysis_view_query_by_name`，但发现数据集来源定义固化字段为 `analysisViewId`，Adapter 仅读取历史 `viewName`，导致回退到数据集展示名并返回 404。Adapter 已兼容优先读取 `analysisViewId`、再读取 `viewName`；本轮已清理 E2E 栈并恢复本地模拟环境，页面 PASS 需在下一轮新镜像中复验。

Aloudata Adapter 回归测试（2026-09-13）：Docker Maven JDK 21 离线执行 `-Dtest=AloudataApiClientTest,AloudataAnalysisViewAdapterTest,AloudataEndpointServiceTest test -q` 通过；新增覆盖连接级认证 Header 注入、`analysisViewId` 来源定义、数据库端点配置损坏时核心端点兜底，以及无 Web `UserContext` 的 Runner 内部读取回退。该结果证明 Adapter 单元契约已闭合，不等价于页面 E2E PASS。

本地模拟数据与启动脚本补强（2026-09-13）：canonical 订单 fixture 已扩展为 10 行，并重新生成 Parquet/XLSX、manifest 及 MinIO 对象；MySQL/PostgreSQL 各 10 行、`PAID` 3 行的检查通过。`start.sh` 改为轮询五个长期服务的 healthcheck 后再执行 MinIO seed，规避一次性 TLS/MinIO init 容器导致的 `up --wait` 误失败；连续执行 `bash -n .../start.sh && start.sh && check.sh` 通过。

JDBC+模拟 Aloudata 页面闭环（2026-09-13）：使用最新 DataAgent 镜像、E2E HTTPS WireMock `local_sales_view`、MySQL fixture、系统 Chrome channel（`MATECLAW_E2E_BROWSER_CHANNEL=chrome`）和同一轮 seed 状态执行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --grep 'JDBC.*Aloudata'`，结果 `1 passed`。页面真实调用 DataAgent，脚本通过 `datasets.read` 读取 JDBC 与 Aloudata 输入，验证 `region=east` 条件下的 5 行结果和金额 `120.5`；更新了该用例的结果断言并生成稳定截图基线。该证据仅证明本地模拟编排、过滤参数和页面展示链路，不替代真实 Aloudata ALO-X02 授权及远端下推证据；E2E 专属容器清理后本地模拟环境已恢复并通过 `check.sh`。

E2E 启动入口稳定性补强（2026-09-13）：`scripts/e2e/start-dashboard-mvp.sh` 新增本地模拟容器占用同端口时的 fail-fast（退出码 `2`）和 API/UI 健康探测的 `connect-timeout/max-time`，避免端口冲突产生半套容器或单次 `curl` 阻塞超过总体 deadline；`bash -n` 和设计/外部前置条件门禁通过。该改动只改善测试入口，不改变生产服务行为。

本地模拟 E2E 全量矩阵（2026-09-13）：清理本地模拟栈后，以干净的 `docker-compose.test.yml`、同一轮 `seed-dashboard-mvp.sh`、本地 JWT 和系统 Chrome channel 执行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line`，结果为 `8 passed (45.3s)`。通过项包括：JDBC+模拟 Aloudata 双源、API+文件双源、旧 Schema、脚本失败/重试、取消/重试、超时/重试、资源限制/重试和大结果 ObjectRef；无 route mock、无 skip。E2E 专属容器/卷随后已清理，本地模拟环境恢复并再次通过 `check.sh`。该证据绑定当前工作树而非候选 Git SHA，真实 Aloudata ALO-X02 和真实 LLM Agent 对话仍不计入 PASS。

DataAgent 最新全量回归（2026-09-13）：通过 Docker Maven JDK 21 执行 `mvn -o -f mateclaw-dataagent/pom.xml test -q`，挂载 Docker Desktop socket，并设置 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`、`TESTCONTAINERS_RYUK_DISABLED=true`；命令退出码 `0`，Surefire 汇总 `153 tests, 0 failures, 0 errors, 0 skipped`。首次未设置宿主地址覆盖时的 Ryuk/MinIO 连接失败仅属于 Maven 容器网络配置问题，不计入代码失败；修正测试运行参数后所有 Testcontainers 用例通过。

标准 Make 入口复验（2026-09-13）：直接执行 `make dashboard-dataagent-test`，确认 Makefile 封装的 Docker Desktop/Testcontainers 参数可复现上述全量结果，命令退出码 `0`；无需执行者手工拼接 Docker Socket、Maven 缓存或宿主地址参数。

本轮完整 E2E 复验（2026-09-13）：按 `start-dashboard-mvp.sh` 清理并启动独立 E2E Compose，使用同一轮 seed、`MATECLAW_E2E_ALOUDATA_MODE=simulation`、本地 JWT 和系统 Chrome channel 执行全量 Playwright。首次运行因遗漏注入错误/取消/超时/资源场景的 Dashboard ID 而 fail-fast；补齐同一 state 文件中的全部 ID 后，`npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line` 得到 `8 passed (46.0s)`。该配置问题已定位并保留为入口证据，不是功能失败；E2E 容器清理后本地模拟栈恢复，`check.sh` 通过。

E2E 环境导出入口补强（2026-09-13）：新增 `scripts/e2e/export-dashboard-mvp-env.sh`，只从 seed state 文件导出 9 个 Dashboard ID，不读取或持久化 Token/密码；已用当前 state 验证输出恰好 9 行并通过 `bash -n`。09 子计划已补充 `eval "$(.../export-dashboard-mvp-env.sh)"` 用法，避免再次因手工遗漏变量触发入口失败。

E2E 环境导出安全复验（2026-09-13）：导出值统一经过 jq `@sh` quoting 后再生成 `export` 命令，避免异常 state 内容形成 shell 语句；语法、9 行输出、设计门禁和本地前置条件检查均通过。

ECharts E2E 固化（2026-09-13）：seed 新增 `E2E ECharts Binding Dashboard`，state/export 新增 `MATECLAW_E2E_ECHARTS_DASHBOARD_ID`；用例从列表进入真实预览页，断言 `.chart-widget=1`、`.chart-container canvas=1` 并保留页面快照。修改后在同一轮 E2E Compose、JWT、系统 Chrome 下运行全量 Playwright，结果为 `9 passed (36.2s)`，其中新增 ECharts 用例通过。

CDP 视觉复验（2026-09-13）：在同一轮 E2E seed、JWT 和系统 Chrome 下，使用 `Accessibility.getFullAXTree` 与 `Page.captureScreenshot` 采集仪表盘列表、编辑器和最终结果页。AX 节点数分别为 516、389、617；最终结果页真实表格 5 行、包含金额 `120.5`，并确认“脚本数据集输入”“输入预览”“最终结果预览”等控件存在。截图路径为 `/tmp/mateclaw-dashboard-cdp.png`、`/tmp/mateclaw-dashboard-editor-cdp.png`、`/tmp/mateclaw-dashboard-preview-cdp.png`。该结果为当前工作树本地模拟视觉证据，不替代候选 SHA 验收。

计划一致性门禁补强（2026-09-13）：发现总体计划 G3 摘要曾将工作树证据误写为候选 SHA、将 VIS-UI01～VIS-UI08 误写为全部通过；已改为明确“当前工作树部分视觉证据、候选 SHA 验收待提交”，并在 `verify-dashboard-design.sh` 增加一致性断言。设计门禁复验通过。

可复用 CDP 脚本复验（2026-09-13）：新增并实际执行 `mateclaw-dataagent-ui/e2e/cdp-dashboard-visual-check.mjs`。首次执行发现列表标题定位过宽（标题和副标题同时匹配），已收窄为精确 heading；修复后脚本在 E2E Compose、同一轮 seed、JWT 和系统 Chrome 下成功生成 `dashboard-list.png`、`dashboard-editor.png`、`dashboard-preview.png`，AX 节点数为 516、500、617，最终结果 5 行且包含 `120.5`。E2E 栈随后清理，本地模拟环境恢复并通过 `check.sh`。

CDP 脚本最新复验（2026-09-13）：入口已增加 ECharts Dashboard，在候选 SHA `fc799a85` 上成功生成 `dashboard-echarts-preview.png`；AX 节点数为 `567/500/617/88`（列表/编辑器/Table 结果/ECharts 结果），ECharts 结果页 `canvasCount=1`、图表标题可见。该证据绑定候选 SHA 和本地模拟环境。

真实 Aloudata 外部 Gate 预检（2026-09-13）：当前 shell 未注入 `ALOU_DATA_*`，执行外部前置条件入口以退出码 `3` fail-fast，未发起网络请求；真实 ALO-X01/X02 继续保持 `BLOCKED`，不以本地模拟结果替代。

范围更正（2026-09-13）：根据已确认的产品范围，平台不负责 AI 自动生成或直接执行 SQL/Python；此前记录中的“真实 LLM Agent 对话 E2E”属于超出本期范围的历史审计项，已从 09 Step 3b 和外部前置条件中移除。本期仅验收用户提供脚本的校验、预览、执行及旧 `ToolCallback` 兼容。

CDP npm 入口补强（2026-09-13）：`mateclaw-dataagent-ui/package.json` 新增 `npm run test:e2e:cdp`；已验证 npm 脚本可发现、package JSON 可解析，缺少 Token 时按设计 fail-fast（`CDP-FAILFAST-PASS`）。

设计门禁覆盖补强（2026-09-13）：`verify-dashboard-design.sh` 现同时检查 CDP 脚本文件和 `test:e2e:cdp` npm 命令存在，避免计划引用失效入口；门禁复验输出 `DESIGN-PASS`。

当前工作树模块回归（2026-09-13）：`make dashboard-dataagent-test` 退出码 `0`，Surefire 汇总 `153 tests, 0 failures, 0 errors, 0 skipped`；`uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q` 为 `21 passed`；`npm --prefix mateclaw-dataagent-ui test -- --run` 为 `8 files / 27 tests passed`；随后 `npm --prefix mateclaw-dataagent-ui run build` 成功。仅有既有 SLF4J、pytest 弃用提示和 Rollup chunk 大小告警，不影响退出码。

Runner 多别名 Join 回归（2026-09-13）：当前工作树 `make dashboard-runner-test` 为 `21 passed`；新增执行器测试通过本地 HTTP 数据面返回 `orders` 与 `customers` 两个别名，用户脚本分别调用 `datasets.read` 后使用 Pandas Join，最终结果与预期一致。该测试补强 G2 的多源脚本处理证据，不替代双源 Playwright 或真实 Aloudata Gate。
追加本轮本地前置复验（2026-09-13）：`make dashboard-prerequisites-simulation`、`bash scripts/verify-dashboard-design.sh` 和 `./scripts/verify-dashboard-external-prerequisites.sh --local` 均通过；本地 WireMock 环境下通过 Docker Maven 执行 `AloudataAnalysisViewExternalIT`，结果为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。该测试仅证明本地模拟 Adapter 契约，不关闭真实 Aloudata ALO-X02。

统一本地门禁复验（2026-09-13）：在当前工作树执行 `make dashboard-verify-local`，依次完成模拟前置条件、DataAgent Docker Maven 全量 `153 tests, 0 failures, 0 errors, 0 skipped`、Runner `21 passed`、UI `8 files / 27 tests passed`、UI production build 和 `DESIGN-PASS`；命令整体退出码为 `0`。构建输出仅含既有 Rollup chunk 大小提示，测试仅含既有 SLF4J、pytest-asyncio、Parquet/Hadoop、Mockito agent 和旧 Local 执行器 `pip` 兼容告警。该证据绑定当前工作树，不替代真实 Aloudata ALO-X02。

HTTP/API 模拟契约补强（2026-09-13）：本地 `check.sh` 同时请求 WireMock HTTP/HTTPS `/orders?status=PAID`，两条路径均返回预期 `id=1004`；配合 `orders-openapi.yaml` 的 operationId、参数和 HTTPS E2E fixture，证明本地已登记 API 的筛选透传与 TLS 入口可复现。该证据不替代真实 API 所有者提供的地址、证书、分页和错误响应验收。

## 本轮 Google Chrome CDP 视觉验收（2026-09-13）

验收环境：当前工作树（本轮改动尚未提交）、本地模拟 E2E 服务，工作区 `1`，Google Chrome 交互标签页 viewport `1440x736`；使用 Chrome 页面控制执行列表、编辑器、数据集选择、输入预览、最终结果预览、错误/取消/超时/资源超限和旧 Schema 场景。随后使用同一轮 seed、JWT、`Page.captureScreenshot`、`Accessibility.getFullAXTree` 生成持久截图；截图目录为 `/tmp/mateclaw-dashboard-cdp`，包含 `dashboard-list.png`、`dashboard-editor.png`、`dashboard-preview.png`、`dashboard-echarts-preview.png`。CDP 脚本结果为 AX 节点 `567/497/601/88`（列表/编辑器/Table/ECharts），Table 结果 DOM 有 5 行且包含 `120.5`，ECharts `canvasCount=1` 且标题可见。

| 用例 | 本轮结果 | 实际观察 |
| --- | --- | --- |
| VIS-UI01 | `PASS`（本地模拟） | 编辑器数据集选择器可切换 `E2E JDBC Orders Dataset`、`E2E Aloudata Metrics Dataset`、`E2E HTTP Orders Dataset`、`E2E File Orders Dataset`；切换文件/HTTP 后页面不出现 SQL。Aloudata 管理页的认证值为空，页面未显示旧认证值。 |
| VIS-UI02 | `PASS`（本地模拟，修复后） | HTTP/API 声明式 Schema 和 JDBC 字段在编辑器显示；文件输入在受控预览后自动补齐字段。输入预览使用结构化表格，展示字段名、类型对应的行数据，不再使用原始 JSON 大块输出。 |
| VIS-UI03 | `PASS`（本地模拟） | 非法别名 `bad alias` 在提交前显示“别名须以字母开头，仅允许字母、数字和下划线（最多 64 个字符）”；添加参数后显示参数名、类型 `string`、作用域“仪表盘”；插入读取模板使用当前别名。 |
| VIS-UI04 | `PASS`（本地模拟，修复后） | 最终结果预览返回 5 行双源数据；大结果返回 10 行受限表格并显示完整 ObjectRef 提示；ECharts 预览有可见 canvas。 |
| VIS-UI05 | `PASS`（本地模拟） | 取消场景运行中显示“取消执行”，取消后按钮恢复“最终结果预览”，错误提示为“执行已取消，可点击‘重试’重新运行”；失败场景显示 traceback 和“重试”；超时显示 `task timed out` 和“重试”；未观察到成功态与错误态同时出现。 |
| VIS-UI06 | `PASS`（本地模拟）/真实 Aloudata `BLOCKED` | 脚本失败、超时、资源超限分别显示可读错误分类并保留重试；跨工作区读取已有真实 Compose API `403` 证据。真实 Aloudata `SM_02_0038` 仍缺少授权，保持外部阻塞。 |
| VIS-UI07 | `PASS`（本地模拟） | 旧 Schema 仪表盘可打开，页面明确显示“该仪表盘暂无组件，请先编辑添加组件”，提供“返回/去编辑”，未因无脚本绑定误触发 Runner。 |
| VIS-UI08 | `PASS`（本地模拟，修复后） | JDBC+模拟 Aloudata、HTTP/API+文件双源编辑器结果均有可见 Table 行；`.table-wrapper` 增加最小可视高度并支持滚动，Chrome CDP Table 结果页已复验。ECharts 预览单独通过 canvas 结构断言。 |

### 已确认问题及复现步骤

1. **双源 Table 预览空白（VIS-UI04、VIS-UI08，已修复）**
   - 前置：启动本地模拟服务，使用工作区 `1` 的 `admin` 登录，并使用 `/tmp/mateclaw-dashboard-mvp-state-1.json` 中的 `multiSourceDashboardId` 或 `apiFileDashboardId`。
   - 复现：进入“洞察”→对应 Dashboard 卡片“预览”，等待最终结果加载；用 DOM 检查 `.el-table__body` 或 `table`，可见 4/5 行数据，但 `getBoundingClientRect().height` 为 `0`，页面截图只显示空白结果卡片。
   - 预期：Table 行、列名、行数和双源结果在预览画布中可见。
   - 实际：数据存在于 DOM/AX 树，但可视区域高度为 `0`，用户无法看到结果。
   - 修复：`DataTableWidget.vue` 为 `.table-wrapper` 和内部表格增加最小可视高度并保留滚动；Chrome CDP/Playwright 已确认 Table 行可见。

2. **输入 Descriptor 字段数与输入预览不一致（VIS-UI02，已修复）**
   - 复现：编辑 `E2E JDBC + Aloudata Dashboard`，在“脚本数据集输入”中依次选择 `E2E File Orders Dataset` 或 `E2E HTTP Orders Dataset`，点击“查看字段”，再点击“输入预览”。
   - 预期：Descriptor 展示字段名、类型和可读的字段数量，且与预览结果一致。
   - 修复：HTTP API 定义补充声明式 Schema；`DatasetInputPanel.vue` 在 Descriptor 为空时从受控预览推断字段并回填，Chrome 中 HTTP/JDBC/文件最终均显示 `5 个字段`。

3. **输入预览和最终结果预览缺少可读布局（VIS-UI02、VIS-UI04，已修复）**
   - 复现：在上述编辑器中点击“输入预览”或“最终结果预览”，保持 Chrome viewport `1440x736`。
   - 预期：字段、类型、行数和结果边界在侧栏内可读，不发生横向布局溢出；大结果明确提示 `outputRef` 与受限预览边界。
   - 修复：输入预览改为结构化表格；大结果在 10 行受限表格旁显示“仅展示受限预览”和 ObjectRef；对应单测、Playwright 和 Chrome CDP 均通过。

本轮结论：`VIS-UI01～VIS-UI08` 在本地模拟环境均取得通过；`VIS-UI02、VIS-UI04、VIS-UI08` 的历史失败已由当前实现和 Chrome CDP 证据关闭。真实 Aloudata 结果权限仍保持 `EXTERNAL-BLOCKED`，不以 WireMock 模拟替代。本记录已同步修复实现、测试断言和截图基线；候选 SHA 验收仍需提交后重跑。

### VIS-UI02 空数据集输入补充

在 `E2E JDBC + Aloudata Dashboard` 编辑器中点击“添加”创建未选择数据集的临时输入，再点击“最终结果预览”，页面即时显示“每个数据集输入都必须选择数据集”，同时保留“重试”入口，未发起无效执行；该空态/前置校验子项为 `PASS`。该操作未点击“保存”，不会改变 seed Dashboard。

### 视觉验收范围审计补充

对总体计划引用的 00–09 子计划做静态范围核对：`10/10` 子计划均明确写有视觉验收，或明确标注 `N/A（由 07/09 统一验收）`；测试矩阵中的 `VIS-UI01～VIS-UI08` 共 `8/8` 均有当前验收记录。后端-only 子计划不重复伪造页面证据，页面交互统一归入 07/09；本轮各项的 `PASS`、`FAIL`、`NOT_RUN` 和 `EXTERNAL-BLOCKED` 状态以本记录为准。

## 2026-09-14 当前工作树 CDP 复验补充

验收对象为当前工作树（尚未提交），本地 UI `http://127.0.0.1:15174`、DataAgent `http://127.0.0.1:18189/dataagent/api` 均返回健康响应，state 使用 `/tmp/mateclaw-dashboard-mvp-state-1.json`，工作区为 `1`。

- 非交互 CDP 入口 `node mateclaw-dataagent-ui/e2e/cdp-dashboard-visual-check.mjs` 已重新执行并生成 `/tmp/mateclaw-dashboard-cdp/dashboard-list.png`、`dashboard-editor.png`、`dashboard-preview.png`、`dashboard-echarts-preview.png`；脚本退出码为 `0`，Table 结果 `rows=5` 且包含 `120.5`，ECharts `canvasCount=1` 且标题可见。
- 使用同一轮 seed、JWT、Chrome channel 执行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --workers=1 --reporter=line`，结果为 `9 passed (28.7s)`。原 `dashboard-multi-source.spec.ts:29` 的 `1158 pixels (ratio 0.01)` 差异已通过重新生成与当前渲染一致的快照基线关闭；非更新模式再次执行也通过。
- 本轮同时验证了回归断言：API+文件和大结果编辑器等待字段描述不再包含 `0 个字段`；HTTP API 的 Schema 来自已登记 API 定义，文件输入在受控预览后补齐字段。
- 用户 Google Chrome 的交互页在前一轮同一本地栈中已通过 CUA 复验 JDBC 输入表格、ObjectRef 受限预览和字段显示；本轮 CUA 扩展连接短暂返回请求头策略错误，未改动用户其他 Chrome 标签页。

### 当前问题及复现步骤

1. **JDBC + Aloudata 视觉快照与当前渲染不一致（VIS-UI04/VIS-UI08，已修复）**
   - 前置：保持本地 UI/DataAgent 服务运行，使用 state 文件中的 Dashboard，取得本地 `admin` JWT。
   - 执行：设置 `MATECLAW_E2E_TOKEN`、`MATECLAW_E2E_WORKSPACE_ID=1`、`MATECLAW_E2E_ALOUDATA_MODE=simulation`、`MATECLAW_E2E_BROWSER_CHANNEL=chrome` 及 state 中的 9 个 Dashboard ID，然后运行 `npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line`。
   - 预期：9 个用例全部通过，`dashboard-jdbc-aloudata.png` 与仓库快照一致。
   - 修复与结果：保留严格截图断言，使用当前 UI 结构化输入预览、最小高度 Table 和双源结果重新生成快照；随后非更新模式执行得到 `9 passed`，双源功能断言仍得到 5 行和 `120.5`。该问题已关闭。

## 2026-09-14 产品闭环修复补充

- 数据集管理新增正式路由：`/datasets`、`/datasets/new`、`/datasets/:id/edit`；配置中心“数据配置”页新增“数据集管理”入口。
- `DatasetListView` 已接入 `datasetApi.list()`，展示数据集名称、来源、行数和字段数，并提供新建及编辑/预览操作。
- 自动化证据：新增 `DatasetListView.spec.ts`，UI 全量为 `9 files / 30 tests passed`；production build 成功；`verify-dashboard-design.sh` 输出 `DESIGN-PASS`。
- 非交互浏览器补充验收：在本地 5175 端口使用 Chrome channel 和模拟 API，`/datasets` 页面可见“数据集”“新建数据集”“订单数据集”“编辑 / 预览”，并生成 `/tmp/mateclaw-datasets-route.png`。
- CUA 当前仍返回 `Unable to load browser request-header policy`，因此本轮无法再次控制用户现有 Chrome 标签页；不以非交互截图替代该限制，待连接恢复后补做交互验收。

## 2026-09-14 产品闭环继续实施补充

- 数据源新建不再固定进入 Aloudata：`DatasourceView.vue` 增加来源选择页，支持 MySQL、PostgreSQL、SQL Server、Aloudata；选择后将对应 `sourceId` 传入既有连接表单。
- 产品状态：数据集入口为已实现；数据源类型选择为已实现；数据源列表仍仅展示指标平台，HTTP/API 与文件对象登记及完整前端创建链路仍为未完成。
- 当前工作树 UI 全量回归为 `9 files / 30 tests passed`，production build 成功，`git diff --check` 无输出，设计门禁保持 `DESIGN-PASS`。
- CUA 重试仍失败：`Unable to load browser request-header policy`；本轮未改动用户 Chrome 标签页。此前生成的非交互页面截图仅作为补充证据。

## 2026-09-14 文件数据集上传补充

- `DatasetEdit.vue` 的文件来源改为隐藏原生文件控件 + “选择并上传文件”按钮，调用 `POST /dataagent/api/v1/dataset-files`，上传成功后自动回填受控 `objectId` 和格式。
- 页面不再接受用户手填本地路径或对象 ID；后端 `DataAgentDatasetFileController` 负责工作区隔离和对象存储。
- `DatasetEdit.spec.ts` 新增上传回填测试；当前 UI 全量为 `9 files / 31 tests passed`，production build 和设计门禁均通过。

## 2026-09-14 HTTP/API 定义选择补充

- HTTP/API 来源不再显示可自由编辑的内部 ID 输入框，改为读取所选数据源 `connectionParams.apiDefinitions` 的下拉目录；无定义时显示“请先在数据源管理中登记”。
- 回归测试覆盖从登记目录选择 `orders` 定义并提交 `sourceDefinition.apiDefinitionId`；UI 全量保持 `9 files / 31 tests passed`，production build 与设计门禁通过。

## 2026-09-14 仪表盘绑定心智补充

- 属性面板明确标注“直接指标绑定”，脚本输入面板明确标注“脚本结果数据集输入”，并说明二者的使用边界。
- 该调整先消除两套模型的隐式混淆，不改变现有兼容数据结构；统一保存和绑定结果展示仍列入后续 P0。
- UI 全量回归保持 `9 files / 31 tests passed`，production build 与设计门禁通过。

## 2026-09-14 Google Chrome CUA 视觉复验补充

- CUA 已恢复并连接用户 Chrome 的 DataAgent 标签页 `http://127.0.0.1:5174/?nav=insight`。
- 通过真实 Chrome AX 操作打开“配置 → 数据配置”：可见“数据集管理”入口、现有 Aloudata 数据源及连接管理页面。
- 点击“新建数据源”后，真实 Chrome AX 树可见 MySQL、PostgreSQL、SQL Server、Aloudata 四个来源选项及说明，证明不再固定进入 Aloudata。
- 点击“数据集管理”后，真实 Chrome 导航到 `http://127.0.0.1:5174/datasets`，页面可见“数据集”“新建数据集”和空状态提示。当前工作区无数据集，因此未继续进入编辑器上传流程。

## 2026-09-14 空操作反馈补充

- 数据集编辑器原先为空函数的“了解如何配置、来源表、计算字段、分组依据、聚合编辑器、字段设置、更多、下载”操作均改为明确的提示反馈；未开放能力不再静默无响应。
- 该调整不伪造未实现功能，待后续补齐真实配置面板时再替换提示行为。
- JDBC 表配置页中原先误放的空“上传/下载”工具组已移除；文件上传仅在 `FILE` 来源中出现，避免来源类型与操作不匹配。
- 新增 `DatasetEditRoute.vue` 路由包装器，接收编辑器的 `back/saved` 事件并导航回 `/datasets`，避免独立路由下取消或保存后无导航。

## 2026-09-14 DatasetEdit 路由闭环 Chrome 验收

- 使用用户 Chrome CUA 从 `/datasets` 点击“新建数据集”，真实导航到 `/datasets/new`；AX 树可见“模型配置”、数据源选择、五种来源类型（含 HTTP/API、文件）及取消按钮。
- 点击“取消”后真实返回 `/datasets`，AX 树再次显示数据集列表空状态；证明独立编辑路由的返回事件已接通。
- 数据集列表新增“返回配置”按钮；在用户 Chrome 中点击后真实返回 `/?nav=config`，配置中心 AX 树可见“数据源”和“数据集管理”入口。

## 2026-09-14 全量数据源页面复验

- 当前用户 Chrome 配置页加载完成，Aloudata 数据源的指标/维度管理面板正常显示；页面未出现空白或脚本错误态。
- 由于当前工作区实际仅返回一个 Aloudata 数据源，本轮无法在真实页面中展示 JDBC 条目；JDBC 通用详情分支已通过 UI build 和组件回归验证，待模拟数据源写入后补做现场截图。

## 2026-09-14 HTTP/API 摘要视觉复验

- 在用户 Chrome 中进入 `/datasets/new` 并选择“HTTP/API”，AX 树可见“已登记 HTTP/API 定义”下拉框及只读提示“仅引用所选数据源已登记的 API 定义，不支持在脚本或页面输入 URL”。
- 当前工作区暂无登记定义，因此下拉仅显示空状态；method/path 摘要逻辑已由数据源 `connectionParams.apiDefinitions` 目录驱动，待注入模拟定义后可继续验收具体摘要文案。

## 2026-09-14 脚本绑定目标可发现性复验

- 在用户 Chrome 中打开真实仪表盘编辑器，AX 树可见“脚本结果数据集输入”及说明“当前未选择目标组件，执行结果不会覆盖画布”。
- 该提示明确了脚本结果的绑定目标和未选择组件时的保护行为，补齐 FE-CLOSE-10 的可发现性部分；统一保存后的绑定结果展示仍待后续实现。

## 2026-09-14 Descriptor 空 Schema 中间态修复与 Chrome 复验

- 问题：Descriptor 接口在字段探测尚未完成时可能返回空 `schema`，旧页面直接显示“0 个字段”，但随后输入预览已经包含字段，造成视觉和语义不一致。
- 修复：`DatasetInputPanel.vue` 仅在 `schema.length > 0` 时显示字段数量；空 Schema 期间显示“字段探测中…”并提供“刷新字段”，仍由受控预览完成字段补全，不伪造字段数量。
- 回归：新增 `DatasetInputPanel.spec.ts` 用例覆盖空 Schema/预览未完成中间态，验证不出现“0 个字段”；该文件定向测试 `8/8` 通过。
- Chrome CUA：使用用户 Chrome 实际打开配置中心 → 数据集管理 → 新建数据集，AX 树确认 `/datasets` 空态、`/datasets/new` 五种来源类型和取消返回链路均可见；页面截图已采集。当前工作区没有可授权数据集，无法在现场触发真实 Descriptor 空 Schema 请求，故该特定中间态以定向测试和代码路径证据覆盖，不能伪记为现场 PASS。

## 2026-09-14 脚本结果绑定目标显式选择

- `DatasetInputPanel.vue` 新增“结果绑定组件”下拉框，候选项来自当前页面的 KPI、图表和表格组件；脚本结果不再隐式依赖当前属性面板选中项。
- 编辑器加载已有 `scriptBindings` 时回显首个绑定目标；首次选中画布组件时作为默认目标，用户点击“应用到组件”后仍由 `scriptBindings` 持久化。
- 单测新增绑定目标可发现性覆盖；Chrome CUA 编辑器 AX 树已看到“结果绑定组件”和“选择要接收脚本结果的组件”。当前工作区无可选数据组件，未强行伪造选择结果。

## 2026-09-14 提交后回归与 CUA 状态

- 当前提交 `00c772d8c315b941b2e0171fd26f53fd12b19416` 的 UI 全量回归为 `9 files / 35 tests passed`；production build、`git diff --check` 和 `bash scripts/verify-dashboard-design.sh`（`DESIGN-PASS`）均通过。
- `dashboard-multi-source.spec.ts` 的历史快照差异 `1158 pixels (ratio 0.01)` 已在同一套本地模拟 E2E 环境中重新生成并以非更新模式复跑关闭；双源断言仍为 5 行并包含 `120.5`。
- 本轮尝试恢复用户 Google Chrome CUA 两次，均返回 `Unable to load browser request-header policy`，没有把静态回归或非交互截图升级为新的交互视觉 PASS；CUA 服务恢复后需按当前提交重新采集截图和 AX 树。

## 2026-09-14 数据源与来源类型兼容修复

- 问题：数据集编辑器可以在 Aloudata 连接上选择 `JDBC_SQL`，形成后端无法读取的非法来源组合。
- 修复：来源类型选项按当前连接类型禁用；选择连接时自动切换到兼容默认来源；保存前保留兼容性校验，非法组合不会提交。
- 回归：`DatasetEdit.spec.ts` 覆盖 Aloudata + JDBC SQL 的禁用与保存保护；UI 全量为 `9 files / 36 tests passed`，production build 和 `DESIGN-PASS` 通过。
- 服务端同步拒绝同类非法组合，Docker Maven 定向执行 `DatasetCatalogServiceTest` 通过；因此 API 直提交不会绕过页面来源约束。

## 2026-09-14 产品入口文件数据集 E2E 补充

- 新增真实后端 Playwright 用例，覆盖从配置中心进入数据集管理、上传 CSV、创建/同步数据集、进入字段预览并清理临时数据集。
- 本地 UI 单测、类型检查、production build 和设计门禁均通过；首次 E2E 因缺少 `chromium_headless_shell` 未执行，随后切换系统 Chrome channel 完成目标用例。

- 后续使用系统 Chrome channel 重跑该文件入口用例，结果为 `2 passed`，并实际断言预览表格包含 `120.5`、`east`。全量 11 条 E2E 中另有双源编辑器未选中组件的既有失败，未掩盖或改写为全量 PASS。

## 2026-09-14 创建后统一预览修复

- 创建数据集后不再无条件调用只支持旧 JDBC 表落库的 `/sync`；五种来源统一调用 Descriptor/Preview Adapter，避免 FILE、HTTP/API、Aloudata 和 JDBC SQL 创建后停在配置态。
- 修复中曾遗漏默认列宽常量，导致统一预览异常被捕获；补齐 `DEFAULT_COLUMN_WIDTH` 后真实文件创建链路恢复通过。

## 2026-09-14 当前提交 Chrome CDP 视觉复验

## 2026-09-14 双源视觉快照差异修复与全量回归

- 根因：编辑器右侧属性面板的固定高度/`overflow:hidden` 裁剪了输入区；E2E 又在 Descriptor 异步字段探测完成前采集快照，导致页面可交互但视觉基线不稳定。
- 修复：属性区改为可滚动布局；E2E 使用实际标题“脚本结果数据集输入”，逐个输入等待非零字段描述后再截图；同步更新当前渲染快照。
- 验证：本地 Docker 模拟 DataAgent、MySQL、HTTP、MinIO、Runner 和 Aloudata 环境下，`dashboard-multi-source.spec.ts`、`dashboard-errors-and-compatibility.spec.ts`、`dataset-management-entry.spec.ts` 合计 `11 passed`。JDBC+Aloudata 查询仍返回 5 行并包含 `120.5`；快照非更新模式通过。
- 结论：原报告中的 `1158 pixels (ratio 0.01)` 属于旧基线/异步渲染差异，已关闭；不属于双源查询或数据断言失败。CUA 通道的 `Unable to load browser request-header policy` 仍作为独立环境问题保留。

## 2026-09-14 提交后 Chrome CDP 视觉验收

- 候选提交：`05bda5c771285d3df69016e4390162263f90e969`，本地 UI `http://127.0.0.1:5175`，Google Chrome CDP `9222`，viewport `1440x813`，deviceScaleFactor `2`。
- 通过 CDP 打开洞察列表并进入 `E2E JDBC + Aloudata Dashboard` 编辑器，确认页面标题、`脚本结果数据集输入`、2 个数据集输入和结果绑定组件均可见。
- 截图：`/tmp/mateclaw-cdp-dashboard-after-05bda5c7.png`（列表）、`/tmp/mateclaw-cdp-jdbc-editor-after-05bda5c7.png`（双源编辑器）。编辑器右侧面板可滚动，两个输入项不再被固定高度裁剪。
- CUA 服务仍不可用，因此本条使用直接 Chrome DevTools Protocol 作为交互证据；未将 CUA 错误伪记为产品失败。

## 2026-09-14 组件库键盘入口验收

- 修复前：组件库 `.palette-item` 只有拖拽事件，无法通过键盘创建组件。
- 修复后：条目暴露 `role=button`、`tabindex=0`、`aria-label`，Enter/Space 触发与拖拽相同的 `add-component` 事件；`ComponentPalette.spec.ts` 通过。
- Chrome CDP 当前页面检查到“卡片”“数据表格”等条目均具备上述 AX 属性；截图 `/tmp/mateclaw-cdp-keyboard-palette-d68c66f9.png`。

- 同轮对 `/datasets/new` 和双源编辑器执行可聚焦控件名称审计：`5/5`、`52/52` 均具备文本、`aria-label`、`title` 或 `id`，未发现无名称控件。

## 2026-09-14 正式仪表盘创建链路修复

- 问题：从“洞察→新建仪表盘”拖入第一个组件后，脚本结果数据集面板仍显示“当前未选择目标组件”，用户必须再次点击画布组件才能绑定。
- 修复：`handleAddComponent` 在新增组件后自动设置 `scriptTargetComponentId`（仅当当前没有目标时），保持用户已明确选择的目标不被覆盖。
- 验证：新增 E2E 从产品页面创建仪表盘、拖入“数据表格”、选择 `E2E HTTP Orders Dataset`、填写别名和 `datasets.read` 脚本草稿、保存并确认绑定；完整本地模拟回归 `12 passed`。

- Chrome CDP `9222` 现场复验同一行为：新建仪表盘并拖入“数据表格”后，脚本面板即时显示“当前目标组件：comp_…”。截图 `/tmp/mateclaw-cdp-formal-dashboard-binding-be88696f.png`。

## 2026-09-14 提交后 Chrome CDP 视觉复验

- 候选提交：`58d52abdb505b5d611d9e9e4a5b28fda88599538`；页面：`http://127.0.0.1:5175/?nav=insight&dashboardId=2099412031543152642`；浏览器：Google Chrome CDP `9222`；视口：`1440x900`；时间：2026-09-14（Asia/Shanghai）。
- `Page.captureScreenshot` 截图：列表 `/tmp/mateclaw-cdp-current-dashboard-list.png`，编辑器 `/tmp/mateclaw-cdp-current-dashboard-editor.png`。
- `Accessibility.getFullAXTree`：编辑器 `532` 个节点；脚本面板可见“脚本结果数据集输入”，当前目标组件与输入别名均可见；`Runtime.evaluate`/DOM 检查确认默认目标存在。
- 本次仅做当前提交的交互视觉复验，不替代真实 Aloudata 授权、四主题对比度专项或 CUA 请求头策略故障的后续 Gate。

## 2026-09-14 当前提交全量回归

- 在 `MATECLAW_UI_BASE_URL=http://127.0.0.1:5175`、`MATECLAW_E2E_BROWSER_CHANNEL=chrome`、本地模拟 Aloudata 和同一 seed 工作区下，当前提交 `f22648bccb381f72767a847fb0df4df3405cc53f` 的三组真实 E2E（双源、错误/兼容、数据集入口）共 `12 passed`，无跳过。
- 覆盖 JDBC+Aloudata 结果（5 行、含 `120.5`）、API+文件、ObjectRef、ECharts、旧 Schema、失败/取消/超时/资源限制重试，以及正式页面创建仪表盘并保存脚本数据集绑定。

- 最新文档同步提交 `17029cedffbc4d3b25674eadcc57fa871afde493` 后再次通过 Chrome CDP 现场检查：脚本面板、默认目标组件和 `E2E JDBC Orders Dataset` 均可见；截图 `/tmp/mateclaw-cdp-final-dashboard-editor.png`，AX 树 `532` 节点。

## 2026-09-14 数据集空态四主题修复

- 根因：`DatasetEdit.vue` 空态 `.empty-title`、`.empty-desc`、`.learn-link` 和容器背景使用固定颜色，切换 dark/warm/eye-care 后未跟随全局主题令牌。
- 修复：改用 `--theme-surface`、`--theme-border`、`--theme-text`、`--theme-text-secondary` 和 `--main-orange`；不改变数据集契约或来源逻辑。
- 回归：新增 `数据集空态随四种主题使用主题令牌`，修复前按真实 Chrome channel 复现失败，修复后 `1 passed`；覆盖 `light`、`warm`、`eye-care`、`dark` 的计算样式与主题变量一致性。
- CDP 截图：`/tmp/mateclaw-cdp-dataset-empty-light.png`、`/tmp/mateclaw-cdp-dataset-empty-warm.png`、`/tmp/mateclaw-cdp-dataset-empty-eye-care.png`、`/tmp/mateclaw-cdp-dataset-empty-dark.png`；已查看 dark 主题现场，空态背景和文字清晰可读。表单/表格其他固定颜色仍不宣称已全部收敛。

## 2026-09-14 数据集预览主题令牌补充

- 根因：预览模式 `.toolbar`、`.toolbar-btn`、`.data-preview` 和 `.preview-empty` 使用固定白色/浅灰色，切换 dark/warm/eye-care 会出现非主题背景或低对比度文字。
- 修复：预览工具栏和按钮改用 `--theme-surface`/`--theme-border`/`--theme-text-secondary`/`--main-orange`，结果容器使用 `--theme-surface`/`--theme-border`，加载/空态使用 `--theme-text-secondary`。
- 回归：新增 `数据集预览工具栏和结果空态随主题使用主题令牌`，修复前在 light 主题即因固定 `#c9cdd4` 失败，修复后四主题全部通过。
- 当前提交的本地模拟完整 E2E 共 `14 passed`；新增主题场景未改变双源、ObjectRef、ECharts、错误/兼容和正式创建绑定结果。

## 2026-09-14 数据集表格主题令牌收敛

- 根因：表格列表、字段大纲、表头、单元格、分页和编辑态仍大量使用固定蓝/灰/白色，导致 warm、eye-care、dark 主题出现不一致。
- 修复：将已覆盖的表格/字段/分页/编辑态颜色统一映射到 `--theme-surface`、`--theme-surface-hover`、`--theme-border`、`--theme-text`、`--theme-text-secondary`、`--theme-text-muted` 和 `--main-orange`；保留按钮文字在主色背景上的可读性。
- 验证：四主题预览 E2E 修复前失败（固定 `#c9cdd4`），修复后通过；UI 单测 `37 passed`，生产构建成功，完整本地模拟 E2E `14 passed`。
- 本轮未宣称所有页面颜色已收敛；数据集表格之外的其他历史页面仍需专项审计。

- 最新提交 `df0aef3d941ad79885d0c3e28dbe58a453429a46` 的 Chrome CDP 现场复验进入真实文件数据集预览页并切换 dark 主题：工具栏和预览容器背景均为 `rgb(22, 27, 38)`，结果空态文字为 `rgb(195, 204, 217)`；截图 `/tmp/mateclaw-cdp-dataset-table-dark-df0aef3d.png`。

## 2026-09-14 数据集空态插画主题复验

- 根因：新建数据集空态 SVG 的 `rect/circle/path` 直接写死 `fill`/`stroke`，主题切换时插画仍保持浅色。
- 修复：为卡片、占位线、勾选圆和勾选线增加语义类，分别使用 `--theme-surface-hover`、`--theme-border-strong` 和 `--main-orange`。
- 验证：四主题 E2E 额外检查 SVG 计算后的 fill/stroke；先复现“缺少 illustration card”失败，再补类和样式后通过。最新完整本地模拟 E2E `14 passed`。
- Chrome CDP 最新 dark 主题截图：`/tmp/mateclaw-cdp-dataset-table-dark-df0aef3d.png`；空态卡片和勾选图标与 dark 背景区分清晰。

- 连接用户 Chrome `http://127.0.0.1:9222`，打开 `http://127.0.0.1:5175/datasets/new`，使用本地 JWT 和工作区 `1`。
- 选择 `E2E Aloudata Simulation` 后，页面来源类型自动显示“Aloudata 指标视图”；通过 DOM/AX 对照确认 `JDBC_TABLE`、`JDBC_SQL` 的 `disabled=true`，指标视图可选。
- 页面截图：`/tmp/mateclaw-cdp-aloudata-compatibility.png`。该证据验证来源类型约束的当前渲染；CUA 请求头策略错误仍是独立工具通道问题。

## 2026-09-14 数据源表单主题基础适配

- 根因：`DatasourceForm.vue` 页面容器、头部、卡片、标题、标签和辅助文案使用固定颜色，主题切换时视觉不一致。
- 修复：基础布局、边框和文本改用 `--theme-*` 令牌；针对 Chrome `:-webkit-autofill` 增加填充背景、文字色和光标色覆盖，避免已填充用户名/密码在 dark 主题出现灰色块。
- 验证：新增 `数据源配置表单基础容器随主题使用主题令牌`，在真实 Chrome channel 下覆盖 `light`、`warm`、`eye-care`、`dark`，修复前固定背景/文字断言失败，修复后基础容器和文本断言通过。
- 完整回归：UI 单测 `37 passed`，生产构建成功，真实 Chrome channel E2E `15 passed`（含双源、错误/兼容、数据集入口及本用例）。Chrome CDP dark 主题现场截图确认普通输入和 autofill 输入均与深色表单一致；截图：`/tmp/mateclaw-cdp-datasource-form-dark-autofill-final.png`。
- 其他历史页面主题审计、非 Chrome 浏览器原生控件外观和真实 Aloudata/正式存储环境仍保持未完成状态。

## 2026-09-14 双源 Descriptor Chrome CDP 复验

- 使用 Google Chrome CDP `9222` 打开 `E2E JDBC + Aloudata Dashboard` 编辑器，分别点击两个输入的“查看字段”。
- 实际渲染显示 JDBC 输入 `5 个字段`、Aloudata 输入 `3 个字段`，脚本面板显示当前目标组件 `e2e-table`；未再出现输入预览有字段但 Descriptor 显示 `0 个字段` 的历史问题。
- 截图：`/tmp/mateclaw-cdp-dashboard-descriptors-current.png`。该证据只覆盖本地模拟数据和当前 Chrome 页面，不关闭真实 Aloudata 授权 Gate。

## 2026-09-14 数据源表单下半区主题复验

- 根因：数据源表单步骤条、VPC/开关说明、白名单区域、复选框和底部操作按钮仍有固定浅色 CSS，dark/warm/eye-care 下与表单主体不一致。
- 修复：移除说明文案内联颜色，将步骤条、白名单、复选框、开关、取消/测试/确定按钮统一映射到 `--theme-*` 和 `--main-orange`；不改变连接参数或提交逻辑。
- 验证：扩展 `数据源配置表单基础容器随主题使用主题令牌` 覆盖输入、白名单、操作按钮和步骤条，真实 Chrome channel 四主题定向用例通过；双源定向 E2E `4 passed`，UI 单测 `37 passed`，生产构建成功。
- Chrome CDP dark 主题下滚动至表单下半区现场检查通过，截图：`/tmp/mateclaw-cdp-datasource-form-dark-lower-final.png`。

## 2026-09-14 数据集列表主题现场审查

- 使用 Google Chrome CDP `9222` 打开 `/datasets` 并切换 dark 主题，检查标题、说明、数据集卡片、状态标签和操作按钮。
- 当前可见区域未发现白底/黑字残留，卡片、边框、次要文本和主操作色与 dark 主题一致；截图：`/tmp/mateclaw-cdp-dataset-list-dark-audit.png`。
- 该审查覆盖当前数据集列表主路径；其他历史页面仍需独立主题专项。

## 2026-09-14 数据集列表元数据语义修复

- 问题：文件、HTTP/API 等来源在尚未完成元数据探测时，列表显示“未命名数据源 · 0 行 · 0 个字段”，无法区分真实空数据和待探测状态。
- 修复：列表根据 `sourceType` 提供来源兜底名称（如“文件数据源”），计数统一数值化；行数和字段数均为零时显示“待探测”。不改变后端数据集契约或预览逻辑。
- 回归：`DatasetListView.spec.ts` 增加字符串计数与来源兜底断言，定向测试通过；Chrome CDP dark 主题现场显示文件数据集为“文件数据源 · 待探测”，截图：`/tmp/mateclaw-cdp-dataset-list-dark-metadata-final.png`。
### 2026-09-14 数据集列表可访问性对比度复验

Chrome CDP 连接当前 `http://127.0.0.1:5175/datasets`，分别切换 `light`、`warm`、`eye-care`、`dark` 四种主题，读取数据集列表标题说明、状态标签的实际前景色与主题表面色并按 WCAG AA 普通文字阈值 4.5:1 计算。修复后辅助文字最低对比度为 `4.97:1`，状态标签最低为 `6.24:1`，四种主题均通过。实现上将 `--theme-text-muted` 和 `--theme-success-text` 主题化，移除数据集状态标签的固定绿色。

新增 Playwright 用例 `数据集列表状态和辅助文字满足主题对比度`。通过从当前 Chrome CDP 会话安全读取本地测试上下文，并设置 `MATECLAW_UI_BASE_URL=http://127.0.0.1:5175`、`MATECLAW_E2E_BROWSER_CHANNEL=chrome` 执行，结果为 `1 passed (3.5s)`。Playwright 自带 Chromium 在当前 macOS ARM 架构不支持，但系统 Chrome channel 已满足本地自动化验证。

### 2026-09-14 计划状态一致性复核

复核 01～07 子计划后，补充标注 Descriptor 字段摘要、Table 可视高度、ObjectRef 边界提示和双源快照的历史 `FAIL` 已由后续修复关闭；真实 Aloudata `SM_02_0038`、跨工作区权限 `NOT_RUN`、完整键盘遍历及正式对象存储替换仍保持独立未完成状态。`bash scripts/verify-dashboard-design.sh` 输出 `DESIGN-PASS`，避免历史记录覆盖当前状态。

### 2026-09-14 当前提交 CDP 视觉验收

当前提交 `2fe61dd4fa3ed3671ce11969de8d61d9b8ab416c` 通过 Google Chrome CDP `9222` 打开 `http://127.0.0.1:5175/datasets`，切换暗色主题并采集 `/tmp/mateclaw-cdp-dataset-list-96568fb6.png`。页面标题“数据集”、4 个数据集卡片及状态标签均可见，未出现空白或布局溢出；对比度实测结果见上一节。

### 2026-09-14 键盘可访问性复验

JDBC 表列表项新增 `role=checkbox`、`tabindex=0`、`aria-checked` 和可访问名称，Enter/Space 均可切换选择；`DatasetEdit.spec.ts` 覆盖初始、Enter、Space 三种状态。Chrome CDP 打开 `/datasets/new` 和现有 JDBC 数据集编辑页，入口与配置表单可见；当前模拟数据源未返回表目录，未伪造表项视觉 PASS。

当前提交 `be44efaaee4be6fb42e5868613dafcbc00e1cb74` 的 Chrome CDP 复验截图为 `/tmp/mateclaw-cdp-dataset-edit-keyboard-be44efaa.png`，新建页标题“未命名”、6 个可交互控件可见，页面无空白布局。

### 2026-09-14 JDBC 表目录与键盘操作复验

修复空表目录缺少继续入口的问题：页面新增“刷新表目录”，调用 Schema 探测接口并重新加载目录；失败时保留空态并显示错误提示。Chrome CDP 模拟 JDBC 数据源返回 102 张表，首个表项实际暴露 `role=checkbox`、`tabindex=0`、`aria-checked=false`、`aria-label=选择数据表 dataagent_aloudata_category`，按 Enter/Space 后分别变为 `true/false`。截图：`/tmp/mateclaw-cdp-jdbc-table-keyboard-fc9cba23.png`。对应提交 `86e955b3efda466f42ec64b83d0166add0b408a0`。
### 2026-09-14 空态插图主题视觉复验

数据集新建页 SVG 空态移除固定浅色 `fill/stroke` 属性后，Chrome CDP 暗色主题读取到卡片填充 `rgba(255, 140, 90, 0.07)`、线条填充 `rgba(237, 241, 247, 0.14)`、勾选描边 `rgb(255, 140, 90)`，均来自当前主题令牌。截图：`/tmp/mateclaw-cdp-dataset-theme-svg-8d90ea7d.png`。

### 2026-09-14 表项焦点视觉复验

Chrome CDP 在模拟 JDBC 数据源返回 102 张表后聚焦首个表项，实际读取 `role=checkbox`、`aria-checked=false`、`outline: rgb(65, 118, 230) solid 2px`、`outline-offset: 2px`；暗色主题截图：`/tmp/mateclaw-cdp-table-focus-dark-5d562f69.png`。
### 2026-09-14 来源类型边界复验

Chrome CDP 读取模拟 `E2E HTTP Orders` 数据源的真实响应 `sourceType=api`：来源类型自动为 `HTTP_API`，`JDBC_TABLE`、`JDBC_SQL`、`ALOUDATA_ANALYSIS_VIEW` 均禁用，`HTTP_API` 可用；截图：`/tmp/mateclaw-cdp-api-source-boundary-2bbfddec.png`。对应回归测试验证兼容旧数据源响应。

当前提交 `fa7cd9289b276b99c1049ab7567a0b85def65240` 已在同一 Chrome CDP 页面复验，SQL 编辑器未渲染，截图 `/tmp/mateclaw-cdp-api-source-boundary-fa7cd928.png`。

### 2026-09-14 服务端来源兼容性复验

新增 DataAgent 服务端校验：`HTTP_API` 数据集只能绑定 API 类型数据源，JDBC/Aloudata 连接直接返回 `HTTP API 数据集必须绑定 HTTP/API 数据源`，不写入数据库。`DatasetCatalogServiceTest` 定向通过，`make dashboard-dataagent-test` 退出码为 0。

### 2026-09-14 非 JDBC 表目录视觉复验

Chrome CDP 选择 `E2E HTTP Orders` 后，页面实际为 `HTTP_API`，表区域显示“当前来源类型不使用 JDBC 表目录”，不再显示“刷新表目录”按钮，SQL 编辑器也未渲染。截图：`/tmp/mateclaw-cdp-api-no-table-action-current.png`。

### 2026-09-14 JDBC 类型兼容视觉复验

Chrome CDP 选择 `E2E JDBC Orders` 后，来源类型保持 `JDBC_TABLE`，JDBC 表和 JDBC SQL 选项均可用，无兼容性提示；截图：`/tmp/mateclaw-cdp-jdbc-types-42tests.png`。Oracle 等新增类型由 `DatasetEdit.spec.ts` 回归覆盖。
### 2026-09-14 JDBC 反向绑定拒绝复验

DataAgent 服务端对 `JDBC_TABLE/JDBC_SQL` 绑定 `sourceType=api` 等非 JDBC 数据源返回 `JDBC 数据集必须绑定 JDBC 数据源`，不写入数据集；`DatasetCatalogServiceTest` 定向测试通过。
该拒绝校验已纳入 `make dashboard-dataagent-test` 全量门禁，本轮退出码为 `0`；日志中的既有依赖和 legacy pip 告警不影响测试结果。

### 2026-09-14 当前工作树 Google Chrome CDP 双源现场复验

- 通过 CDP `http://127.0.0.1:9222` 控制用户 Google Chrome，打开 `http://127.0.0.1:5175/?nav=insight`，进入 `E2E JDBC + Aloudata Dashboard` 编辑器。
- 编辑器实际显示“脚本结果数据集输入”、目标组件 `e2e-table`、JDBC `5 个字段` 和 Aloudata `3 个字段`；页面视口为 `1440x813`，AX 摘要为 `211` 行。
- 点击“最终结果预览”后实际显示结果表 `5` 行，包含 `120.5`；现场截图为 `/tmp/mateclaw-cdp-jdbc-aloudata-editor-current.png` 和 `/tmp/mateclaw-cdp-jdbc-aloudata-result-current.png`。
- 同一环境使用系统 Chrome channel 重跑 `dashboard-multi-source.spec.ts` 的双源用例，结果为 `1 passed (7.1s)`；严格 `dashboard-jdbc-aloudata.png` 快照断言通过。由此确认历史 `1158 pixels (ratio 0.01)` 已不再复现，属于已关闭的旧基线差异，而非查询结果问题。

### 2026-09-14 洞察列表状态标签主题对比度修复

- 问题：洞察列表的成功/草稿状态标签仍使用固定浅色 CSS；暗色主题实际渲染为浅橙底，主题不一致且对比度不足。
- 修复：增加成功/警告状态主题令牌；暗色主题使用不透明深色底（成功 `#163b2a`、警告 `#3a2a1b`），确保真实渲染与自动化计算一致。
- 回归：新增 `洞察列表状态标签在四主题下满足对比度`，覆盖成功和草稿两种标签及四主题；系统 Chrome channel `1 passed (3.2s)`。
- Chrome CDP `9222` 现场打开 `/?nav=insight`，实际显示 9 个看板；暗色草稿标签对比度 `8.24:1`。截图：`/tmp/mateclaw-cdp-insight-list-status-dark-fixed.png`。
- 同步修复卡片“撤回/删除”操作按钮的固定橙/红颜色；删除按钮浅色卡片上的原始对比度仅 `3.76:1`，现改用主题语义令牌。四主题回归同时覆盖状态标签和这两类操作按钮，结果仍为 `1 passed`。

### 2026-09-14 仪表盘预览状态圆点主题适配

- 问题：仪表盘预览顶部草稿/已发布状态圆点仍使用固定颜色，与列表状态标签的主题令牌不一致。
- 修复：预览页圆点改用 `--db-status-success-fg` / `--db-status-warning-fg`。
- 回归：新增真实预览路径用例 `仪表盘预览状态圆点跟随主题状态令牌`，系统 Chrome channel `1 passed (3.9s)`；Chrome CDP `9222` 暗色现场读取草稿圆点颜色 `rgb(251, 191, 36)`。截图：`/tmp/mateclaw-cdp-preview-status-dot-dark-fixed.png`。

### 2026-09-14 洞察列表筛选与视图切换可访问状态

- 问题：状态筛选和网格/列表切换按钮只有视觉 `active/on` 样式，未向读屏和自动化暴露当前状态。
- 修复：为三个状态筛选按钮和两个视图切换按钮增加动态 `aria-pressed`。
- 回归：新增 `洞察列表筛选和视图切换暴露当前状态`，真实点击“草稿”和“列表视图”后断言状态切换，系统 Chrome channel `1 passed (4.7s)`。
- Chrome CDP `9222` 现场确认点击前“全部9”为 `true`，点击后“草稿9”为 `true`、其余筛选为 `false`，列表视图为 `true`；暗色截图：`/tmp/mateclaw-cdp-insight-filter-aria-dark.png`。

### 2026-09-14 洞察 AI 助手关闭按钮可访问名称

- 问题：AI 助手面板关闭按钮只有图标，AX 按钮名称为空。
- 修复：增加 `aria-label="关闭 AI 助手"`，不改变面板开关逻辑。
- 回归：系统 Chrome channel 用例 `洞察 AI 助手关闭按钮暴露可访问名称` `1 passed (4.5s)`；Chrome CDP `9222` 现场按钮可按名称定位并点击关闭，截图 `/tmp/mateclaw-cdp-ai-close-button.png`。

### 2026-09-14 页面树更多操作按钮可访问名称

- 问题：编辑器页面树每个页面的三点“更多操作”按钮只有图标，AX 名称为空。
- 修复：增加 `aria-label="页面操作"`。
- 回归：真实编辑入口用例 `仪表盘页面树更多操作按钮暴露可访问名称` `1 passed (4.4s)`；Chrome CDP `9222` 现场读取按钮 `aria-label=页面操作`，截图 `/tmp/mateclaw-cdp-page-actions-button.png`。

### 2026-09-14 脚本数据集下拉框可访问名称

- 问题：脚本结果数据集输入中的“结果绑定组件”和“选择已授权数据集”使用 Element Plus `el-select`，实际渲染的 `input[role=combobox]` 没有可访问名称，键盘/读屏用户无法区分两个下拉框。
- 修复：在 `DatasetInputPanel.vue` 为两个选择器补充对应 `aria-label`，不改变数据绑定或交互行为。
- 回归：系统 Chrome channel 用例 `从洞察产品入口创建仪表盘并绑定脚本数据集` 修复前因 combobox 名称缺失失败，修复后 `1 passed (4.8s)`；UI 全量 `10 files / 42 tests passed`、production build 和 `DESIGN-PASS` 通过。
- Chrome CDP `9222` 现场读取两个可见 combobox：`选择要接收脚本结果的组件`、`选择已授权数据集`；截图 `/tmp/mateclaw-cdp-dataset-input-aria.png`。

### 2026-09-14 组件属性面板控件可访问名称

- 问题：选中数据表格组件后，属性面板中的组件标题、数据源、数据行数和绑定筛选器控件没有把旁侧可见标签传给实际输入元素，AX 名称为空。
- 修复：为 `PropertyPanel.vue` 的标题输入、图表/数据源/指标/维度/筛选器选择器及数据行数输入补充对应 `aria-label`，覆盖单 Tab、多 Tab、筛选器和数据组件分支。
- 回归：扩展真实 Chrome channel 用例断言 `组件标题`、`数据源`、`数据行数`、`绑定筛选器`；修复前因“组件标题”名称缺失失败，修复后 `1 passed`。UI 全量 `10 files / 42 tests passed`、production build 和 `DESIGN-PASS` 通过。
- Chrome CDP `9222` 现场拖入“数据表格”并读取属性面板，四个控件名称均可读；截图 `/tmp/mateclaw-cdp-property-a11y-fixed.png`。

### 2026-09-14 当前页面键盘遍历补充

- 范围：用户 Chrome `9222` 的 `/datasets/new` 和 `/?nav=insight` 两个真实页面；分别从页面起点执行最多 80 次 `Tab`，只统计可见、可聚焦且缺少名称的控件。
- 结果：两页 `missingCount=0`，未发现新的无名焦点控件；洞察编辑器已覆盖选中数据表格后的属性面板。
- 边界：该结果只关闭当前两页的控件级风险，不等同于全站所有历史页面、错误关联、焦点样式和跨浏览器原生控件的完整键盘审计；`FE-CLOSE-09` 的后续专项仍保留。

### 2026-09-14 页面树新增按钮可访问名称

- 问题：页面树新增页面按钮仅显示 `+`，可访问名称不表达操作含义。
- 修复：为按钮增加 `aria-label="新增页面"`，保留原有点击行为和视觉符号。
- 回归：`仪表盘页面树更多操作按钮暴露可访问名称` 系统 Chrome channel `1 passed (4.8s)`；Chrome CDP `9222` 现场读取页面树按钮名称为“新增页面”和“页面操作”，截图 `/tmp/mateclaw-cdp-page-add-a11y.png`。

### 2026-09-14 脚本别名错误关联

- 问题：非法脚本别名虽然显示错误文案，但输入控件未通过 `aria-describedby` 关联错误，读屏用户难以定位原因。
- 修复：别名输入动态关联 `dataset-alias-error-{index}`，错误节点增加 `role="alert"`；合法值时移除关联属性。
- 回归：定向真实 Chrome channel 用例在非法别名断言 `aria-describedby` 和错误节点可见性后 `1 passed (5.7s)`；UI 全量 `10 files / 42 tests passed`、production build、`DESIGN-PASS` 通过。
- Chrome CDP `9222` 现场读取 `aria-describedby=dataset-alias-error-0`、`role=alert`、错误可见且无名控件数 `0`；截图 `/tmp/mateclaw-cdp-alias-error-a11y.png`。

### 2026-09-14 属性面板开关可访问名称

- 问题：数据表格属性面板的“多 Tab 模式”和“组件级时间筛选”可视开关由 Element Plus 绘制，但原生 `input[role=switch]` 没有名称。
- 修复：为多 Tab、多指标、自动生成和组件级时间筛选开关补充对应 `aria-label`；不改变开关状态或事件逻辑。
- 回归：真实 Chrome channel E2E 断言两个数据表格开关语义控件挂载并具备名称，修复后 `1 passed (4.9s)`；UI 全量 `10 files / 42 tests passed`、production build、`DESIGN-PASS` 通过。
- Chrome CDP `9222` 现场读取 `多 Tab 模式`、`组件级时间筛选` 及其 `aria-checked=false`，截图 `/tmp/mateclaw-cdp-property-switches-a11y.png`。

### 2026-09-14 画布组件删除按钮可访问名称

- 问题：编辑画布组件右上角删除按钮仅显示 `✕`，无法说明将删除哪个组件。
- 修复：按钮增加动态 `aria-label="删除组件 {组件标题}"`，保留原有删除行为和视觉符号。
- 回归：真实添加数据表格流程断言删除按钮名称，系统 Chrome channel `1 passed (5.5s)`；UI 全量 `10 files / 42 tests passed`、production build 和 `DESIGN-PASS` 通过。
- Chrome CDP `9222` 现场拖入数据表格并读取 `删除组件 数据表格`，按钮可见；截图 `/tmp/mateclaw-cdp-component-delete-a11y.png`。

### 2026-09-14 数据源配置关闭控件可访问名称

- 问题：数据源配置页右上角关闭控件是 `<span @click>`，只能鼠标触发，不能获得键盘焦点，也没有按钮语义。
- 修复：改为 `type="button"` 的语义化按钮，增加 `aria-label="关闭数据源配置"`，保留原有样式和关闭行为。
- 回归：数据源配置主题 E2E `1 passed (6.6s)`；UI 全量 `10 files / 42 tests passed`、production build、`DESIGN-PASS` 通过。
- Chrome CDP `9222` 按“配置 → 数据配置 → 新建数据源 → MySQL”进入真实页面，关闭按钮可见并获得焦点，名称为“关闭数据源配置”；截图 `/tmp/mateclaw-cdp-datasource-close-a11y.png`。

### 2026-09-14 文件预览字段分组键盘操作

- 问题：文件数据集预览的“维度/度量”分组使用 `div @click`，键盘无法展开或收起。
- 修复：分组改为 `role="button"`、`tabindex="0"`，增加动态 `aria-expanded`/名称，并支持 Enter/Space 切换。
- 回归：文件数据集创建与预览 Chrome E2E `1 passed (4.8s)`；UI 全量 `10 files / 42 tests passed`、production build、`DESIGN-PASS` 通过。
- Chrome CDP `9222` 打开真实文件数据集编辑页，按 Enter 将“维度”分组从 `aria-expanded=true` 切换为 `false`，焦点保持在分组按钮；截图 `/tmp/mateclaw-cdp-field-group-keyboard.png`。

### 2026-09-14 文件预览字段可见性按钮语义

- 问题：文件预览字段行的显示/隐藏图标原先是仅绑定点击事件的 `span`，无法获得按钮语义和稳定的辅助技术名称。
- 修复：改为原生 `button type="button"`，保留图标和点击行为，按字段状态提供“显示列/隐藏列” `aria-label`，并通过无边框样式保持原视觉。
- 回归：文件数据集创建与预览定向 Chrome E2E `1 passed (4.5s)`；断言在存在字段行时检查按钮类型和名称。当前模拟文件 fixture 返回字段行数为 0，因此本轮未虚报字段按钮的现场点击结果。
- 该修复与字段分组键盘操作共同收敛文件预览的键盘/读屏语义；字段行数据由后端 schema 决定，真实文件数据联调时需补充至少一条字段行的现场验收。

### 2026-09-14 图表组件内部 Tab 键盘语义

- 问题：图表组件多 Tab 原先使用点击专用 `div`，运行时图表分页无法通过键盘切换，也没有 `tablist/tab` 语义。
- 修复：补充 `role="tablist"`、`role="tab"`、动态 `aria-selected` 与 roving `tabindex`；支持 Enter/Space 激活、方向键循环切换及 Home/End 定位，并保留当前视觉样式和焦点轮廓。
- 回归：新增 `ChartWidget.spec.ts`，验证两个 Tab 的语义、选中状态和 ArrowRight 切换；UI 全量回归为 `11 files / 43 tests passed`，production build、`DESIGN-PASS` 通过。
- 当前模拟 Dashboard fixture 未包含带多 Tab 的真实图表组件，故运行时 Chrome CDP 只记录组件代码/单测证据；真实多 Tab 看板联调时需补充现场截图与焦点证据。

### 2026-09-14 指标/维度类目树键盘语义

- 问题：Aloudata 指标/维度配置类目树节点及展开图标原先仅绑定点击事件，键盘无法选择或展开。
- 修复：类目节点增加 `role="treeitem"`、`aria-selected`、可聚焦属性和来源计数名称；展开控件增加 `role="button"`、`aria-expanded`、动态名称，并支持 Enter/Space。
- 回归：新增 `CategoryTreeNode.spec.ts`，验证选择、展开语义及键盘事件；UI 全量为 `12 files / 44 tests passed`，production build、`DESIGN-PASS` 通过。
- Chrome `9222` 当前页面未打开指标平台面板，故本轮保留单测证据；进入真实指标/维度配置页后需补采现场焦点截图。
- CDP 首次复验发现本地 Aloudata 模拟环境缺少 `metric_list` 端点，页面返回“同步失败”，因此未将空态误记为树控件验收通过。
- 修复后：本地 WireMock 已补齐 `category_list`、`metric_list`、`metric_batch_detail`、`metric_all_dimensions`、`dimension_list`、`dimension_detail`；DataAgent 端点服务改为“默认核心端点 + 数据库覆盖”，兼容历史数据库端点配置不完整的环境。`AloudataEndpointServiceTest` 定向 Maven 回归通过。
- Chrome `9222` 重建 DataAgent 后重新点击“同步元数据”，实际生成 `role=tree` 和两个 `treeitem`，其中“销售，1 个指标”；聚焦后按 Enter，`aria-selected` 变为 `true`，并显示指标“收入”。截图 `/tmp/mateclaw-cdp-metric-tree-keyboard-pass.png`。
- 同一提交 DataAgent 全量 Maven 回归：`158 tests，0 failures，0 errors，0 skipped`。

### 2026-09-14 Aloudata 同步后面板刷新

- 问题：元数据同步成功后，父页面没有递增已有的 `panelRefreshKey`，右侧指标/维度面板继续显示同步前的空态。
- 修复：`DatasourceView.doSync()` 在同步返回 `completed` 后递增 `panelRefreshKey`，触发 `MetricPlatformPanel` 重新加载类目和分页数据。
- Chrome `9222` 现场：同步前后面板均自动显示两个类目树（指标“销售，1 个指标”；维度“时间与区域，2 个维度”）及“收入”指标，无需切换数据源或刷新页面；截图 `/tmp/mateclaw-cdp-sync-refresh-panel.png`。

### 2026-09-14 类目树 roving tabindex

- 问题：类目树节点全部为 `tabindex=0`，Tab 会重复停留在同一棵树的多个节点。
- 修复：当前选中节点保持 `tabindex=0`，其余节点为 `-1`；Enter/Space 选择后状态和焦点语义保持一致。
- Chrome `9222` 现场读取两棵树：指标树“销售”节点为 `aria-selected=true/tabindex=0`、“全部指标”为 `false/-1`；维度树“全部维度”为 `true/0`、“时间与区域”为 `false/-1`。按 Enter 选择指标节点成功；截图 `/tmp/mateclaw-cdp-tree-roving-tabindex.png`。

### 2026-09-14 预览状态圆点异步加载时序修复

- 现象：完整 22 条 Chrome channel E2E 中，状态圆点主题用例在预览容器刚出现时立即读取 DOM，偶发因仪表盘状态尚未异步加载而报“预览页缺少状态圆点”；不是查询或主题样式失败。
- 修复：在读取颜色前显式等待 `.toolbar-status-dot` 可见，保留真实状态令牌断言。
- 回归：修复后完整本地模拟矩阵 `22 passed (1.1m)`；Chrome CDP `9222` 当前洞察编辑器现场确认无可见无名控件（`unnamed=0`），页面树按钮名称仍为“新增页面”“页面操作”，截图 `/tmp/mateclaw-cdp-final-e2e.png`。

### 2026-09-14 完整矩阵参数复验

- 首次执行完整矩阵时未注入 `MATECLAW_E2E_ALOUDATA_MODE`，双源用例按设计显式 `BLOCKED`，其余 `21 passed`；补齐 `simulation` 后重新执行当前工作树完整矩阵，结果为 `22 passed (1.1m)`。
- 该记录确认失败属于验收参数缺失，不修改或放宽双源用例的真实 DataAgent/模拟 Aloudata 要求。

### 2026-09-14 仪表盘多页面 Tab 可访问语义

- 问题：预览页多页面导航只有视觉 active 样式，没有 `tablist/tab` 和选中状态，读屏无法识别当前页面。
- 修复：顶级页面和子页面导航增加 `role=tablist`、`role=tab`、动态 `aria-selected`、`tabindex` 及按钮类型。
- 回归：系统 Chrome channel 临时双页面看板用例 `仪表盘预览多页面 Tab 暴露选中状态` `1 passed (3.7s)`，失败时自动清理临时看板。
- Chrome CDP `9222` 实际创建并打开双页面看板，读取 `tablists=1`、`tabs=2`，首页 `aria-selected=true/tabindex=0`、明细页 `false/-1`；截图 `/tmp/mateclaw-cdp-multipage-tabs.png`。临时看板已删除。
- 追加键盘验收：在首页 Tab 聚焦后按 `ArrowRight`，明细页实际变为 `aria-selected=true`、`tabindex=0` 且获得焦点；截图 `/tmp/mateclaw-cdp-multipage-tabs-arrow.png`。临时看板已删除。

### 2026-09-14 维度类目持久化与统计修复

- 问题：维度同步结果带有 `dimCategoryId=time`，但历史 upsert 未写入类目字段，且单列实体映射可能丢失类目 ID，导致类目树显示 0 个维度。
- 修复：MySQL/PostgreSQL upsert 增加维度编码、类目 ID/名称和显示状态；类目统计改为查询完整维度实体。
- 验证：重建 DataAgent 后 Chrome CDP `9222` 同步元数据，维度树显示“时间与区域，2 个维度”，页面可见“日期”“区域”；截图 `/tmp/mateclaw-cdp-dimension-tree-fixed-final.png`。

### 2026-09-14 当前工作树完整矩阵复验

- 复验命令使用 `MATECLAW_UI_BASE_URL=http://127.0.0.1:15174` 对应独立 E2E Compose UI，并注入本地 JWT、工作区 `1`、`MATECLAW_E2E_ALOUDATA_MODE=simulation`；先前未指定 UI 地址时实际访问了本地开发 UI，造成仪表盘卡片不存在，该环境误差不计为产品失败。
- 同一套 DataAgent、MySQL、HTTPS WireMock、MinIO、Runner 和 seed 状态下，`npm --prefix mateclaw-dataagent-ui run test:e2e -- --reporter=line` 完整矩阵结果为 `22 passed (1.5m)`；`dashboard-multi-source.spec.ts` 定向结果为 `4 passed (28.7s)`。
- JDBC+Aloudata 仍实际返回 5 行并包含 `120.5`，`dashboard-jdbc-aloudata.png` 非更新模式严格快照通过；维度树现场同时确认“时间与区域，2 个维度”。

> 当前基线说明：本文更早的快照失败、Descriptor 空 Schema 和 Table 高度记录均为历史复现；以本节及后续记录为准。当前本地模拟完整矩阵 `22 passed`，相关快照差异已关闭。真实 Aloudata 结果查询仍因 `SM_02_0038` 保持外部阻塞。

### 2026-09-14 当前工作树 UI 回归

- `npm --prefix mateclaw-dataagent-ui test -- --run`：`12 files / 44 tests passed`。
- `npm --prefix mateclaw-dataagent-ui run build`：生产构建成功；仅保留既有 Rollup `PURE` 注释和 chunk size warning，不影响构建退出码。

### 2026-09-14 智能问数模型选择器可访问名称

- 问题：智能问数底部模型 `el-select` 的原生 `role=combobox` 没有可访问名称。
- 修复：补充 `aria-label="选择模型"`，不改变模型切换行为和视觉样式。
- 回归：修复前 Chrome channel 定向用例因名称为空失败，修复后 `1 passed (5.1s)`；Chrome CDP `9222` 现场读取该 combobox 的名称为“选择模型”，截图 `/tmp/mateclaw-cdp-model-select-a11y-20260914.png`。

### 2026-09-14 顶部导航链接语义

- 问题：顶部导航使用没有 `href` 的 `<a>`，鼠标可点击但无法进入键盘 Tab 顺序，也不是可复用链接。
- 修复：为五个导航项生成当前路由对应的 `href`，保留 SPA 路由点击并阻止默认整页刷新；当前项增加 `aria-current="page"`。
- 回归：修复前定向 Chrome channel 用例因 `href` 缺失失败，修复后 `1 passed (3.7s)`；Chrome CDP `9222` 现场读取五个链接地址、洞察项 `aria-current=page`，首个链接可获得焦点；截图 `/tmp/mateclaw-cdp-top-nav-links-20260914.png`。
