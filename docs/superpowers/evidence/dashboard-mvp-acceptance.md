# Dashboard MVP 真实验收记录

状态：`PARTIAL-PASS / ALOUDATA-BLOCKED`

本记录只接受同一候选 SHA 下的真实 DataAgent、Python Runner、MinIO、JDBC、HTTP/API、文件和已授权 Aloudata 环境证据；MockMvc、单元测试和 Playwright 用例枚举不能替代真实验收。

## 已准备

- `docker-compose.test.yml`：隔离的 MySQL、MinIO、WireMock、DataAgent、Python Runner 和 UI 测试服务。
- `mateclaw-dataagent/src/test/resources/e2e/mysql/init.sql`：可重复的订单测试数据。
- `mateclaw-dataagent/src/test/resources/e2e/wiremock/mappings/orders.json`：API 测试响应。
- `mateclaw-dataagent-ui/e2e/fixtures/orders.csv`：文件数据集 fixture。
- `scripts/e2e/seed-dashboard-mvp.sh`：通过真实 API 创建数据源、数据集和仪表盘；要求注入真实 JWT、工作区 ID；只有要创建 JDBC+Aloudata 场景时才需要已授权 Aloudata 数据集 ID。
- `scripts/e2e/verify-dashboard-mvp-cleanup.sh`：删除并验证仪表盘、数据集和数据源；当前公共 API 尚无文件对象删除端点，因此会明确报告保留的对象 ID。

## 当前验收状态与尚未取得的证据

| 用例 | 状态 | 缺失证据 |
| --- | --- | --- |
| Compose 健康检查 | `PASS` | DataAgent actuator `UP`；Runner `/health` `UP`；MinIO/MySQL/WireMock/UI 均 healthy/可访问；DataAgent→Runner 内部访问成功 |
| JDBC + Aloudata 双源 | `BLOCKED` | 需要当前认证上下文下可查询的 Aloudata 指标视图数据集 |
| API + 文件双源 | `PASS` | 真实 Compose + DataAgent + Python Runner + MinIO + WireMock；Playwright 1 passed，脚本执行无错误并生成截图 |
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
- 前端 Vitest/build：`24 passed`、生产构建通过；Python Runner：`17 passed`（通过 Python 3.13/uv 环境运行，宿主默认 Python 3.8 不满足项目类型标注要求），新增后台 executor 异常必须收敛为 `FAILED`、SDK 在网络请求前拒绝非法过滤条件、兼容 DataAgent `R.ok(...)` 包装响应以及识别业务错误的回归测试；本轮补充了 Playwright 本机 Chrome channel 兼容配置。
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
docker compose -f docker-compose.test.yml up -d \
  e2e-mysql e2e-http minio python-runner mateclaw-dataagent mateclaw-dataagent-ui
scripts/e2e/seed-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui exec -- playwright test
scripts/e2e/verify-dashboard-mvp-cleanup.sh
```

候选 SHA、任务 ID、来源请求/响应、下推报告、页面截图和 trace 应在实际运行后追加，未运行前不得填写 PASS。

## 工作树复验（2026-09-12）

- 总体计划基线命令在 Docker Maven JDK 21 下通过：`mvn -pl mateclaw-plugin-api,mateclaw-server install -Dmaven.test.skip=true`、`mvn -N install -DskipTests` 和 `mvn -f mateclaw-sdk/pom.xml install -Dmaven.test.skip=true` 均返回成功；该证据仅证明当前工作树的构建前置条件可用，不代表已生成候选提交 SHA。
- `bash scripts/verify-dashboard-design.sh`：`DESIGN-PASS`。
- Docker Maven JDK 21 执行 DataAgent 全量测试：`140 tests, 0 failures, 0 errors, 0 skipped`。
- `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q`：`17 passed`。
- `docker build -t mateclaw-python-runner:plan-verify mateclaw-python-runner` 构建通过；以镜像实际启动解释器 `/app/.venv/bin/python` 导入 `pandas`、`polars`、`pyarrow`、`fastapi` 成功，容器 UID 为 `10001`，并确认 `uv.lock` 存在。该检查未修改生产镜像标签。
- `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests/test_executor.py -q`：`7 passed`，其中包含 stdout 资源上限终态验证。
- Aloudata Adapter 定向测试：`9 passed`，覆盖 `SM_02_0038` 到 `ACCESS_DENIED` 的映射、行偏移到零基 `pageIndex` 以及非 `pageSize` 对齐偏移拒绝；真实 Aloudata 结果查询仍按 `SM_02_0038` 保持 `BLOCKED`。
- Dataset Catalog 迁移集成测试：真实 MySQL 8.4、PostgreSQL 15.6 容器均从模拟 V217 数据集表迁移到 V220 通过；旧记录默认 `JDBC_TABLE`，`FILE` 数据集的 `datasource_id=NULL` 可写入。MySQL 仅输出 Flyway 版本支持范围警告。
- UI 依赖按 `package-lock.json` 恢复缺失的 Rollup macOS arm64 optional 包后，Vitest：`24 passed`。
- 这次复验仍属于工作树证据，没有生成候选提交 SHA；因此不改变“同一候选 SHA”验收条件，也不解除 Aloudata 结果查询和真实 LLM Agent 对话 E2E 的剩余项。资源超限、取消、超时、旧 Schema 和跨工作区读取拒绝已分别有真实 Compose 证据。

## 追加工作树复验（2026-09-12 14:08，当前工作树）

以下命令均在当前工作树重新执行并取得退出码 0；结果仍属于工作树证据，未生成候选提交 SHA：

- Docker Maven JDK 21：`mvn -f mateclaw-dataagent/pom.xml test -q`，`140 tests, 0 failures, 0 errors, 0 skipped`。
- Python Runner：`uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q`，`17 passed`（1 个上游弃用警告）。
- UI 单元测试：`npm --prefix mateclaw-dataagent-ui test -- --run`，`8 files / 24 tests passed`。
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
