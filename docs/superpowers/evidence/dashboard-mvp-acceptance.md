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
