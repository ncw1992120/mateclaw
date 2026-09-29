# DataAgent 本地管理洞察 Python 执行 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 DataAgent 直接管理洞察 Python Worker 子进程，日常运行只需启动 DataAgent HTTP 服务，不再部署或启动独立 Python Runner 服务。

**Architecture:** 保持 `DashboardExecutionServiceImpl` 与前端执行 API 不变，以一任务一进程的 Python CLI Worker 替换 `RunnerPythonExecutionService` 的 HTTP 调用。Java 侧拥有异步任务状态、日志、取消、超时和清理；Python Worker 继续提供 `datasets` SDK 与结果标准化。Worker 源码和固定依赖随 DataAgent 部署包/镜像交付。

**Tech Stack:** Java 21、Spring Boot、`ProcessBuilder`/`ProcessHandle`、Python 3.12、Pandas/Polars/PyArrow、pytest、JUnit 5、Maven、内部浏览器。

**Spec:** `docs/superpowers/specs/2026-09-29-dataagent-managed-python-execution-design.md`

## Global Constraints

- 保持现有仪表盘执行 API、前端轮询行为、数据集输入与结果信封契约不变。
- Python 仍是独立 OS 子进程，不嵌入 JVM，也不新增 Python HTTP 服务。
- 外部 Python 客户端 SDK `mateclaw-dataagent-client-python` 继续只负责调用 DataAgent API，不承载 Worker。
- 首要运行环境是 Windows 10 + Python 3.12；同一执行方式也应能在 Linux/K8s 使用。
- 不改变数据集查询计划、权限校验、prepared input、结果校验和持久化逻辑。
- 不允许用户脚本自行传入 requirements 或触发在线 pip 安装；依赖通过受控发布包统一管理。
- Worker 子进程只获得当前任务短期数据读取 token 和必要端点；不得继承数据库密码、DashScope Key、JWT/AES 密钥或完整 DataAgent 环境变量。
- Windows 首轮使用并发限制、超时、stdout/stderr/结果字节限制保护；不宣称有单进程硬内存限制或强多租户沙箱。
- 解释器复用现有 `PYTHON_COMMAND`。Worker 默认从 DataAgent 部署包约定的相对目录发现；仅当 Worker 不在约定目录时，才用 `MATECLAW_PYTHON_WORKER_HOME` 覆盖路径，避免 Windows 本地启动必须额外配置目录。
- 按项目规则在内部浏览器完成真实仪表盘 Python 执行验收后，才进行中文 commit 并 push `feature/dev_fu`；提交只包含本任务文件。

## Review Focus

- Worker 请求/响应 JSON 缺字段、未知字段或格式错误：明确失败，不能启动任意脚本或卡在 `RUNNING`。由 Task 1 的 `test_worker_rejects_invalid_request` 覆盖。
- 用户脚本 stdout 混入结果协议、输出超过上限：结果通道独立于 stdout，超限转为明确状态。由 Task 1/2 的 `test_worker_response_is_separate_from_user_stdout`、`test_stdout_limit_terminates_task` 覆盖。
- 子进程继承 DataAgent 环境变量泄露凭据：只传 allowlist 环境和本任务 token。由 Task 2 的 `test_child_process_receives_only_task_environment` 覆盖。
- Windows 与 POSIX 进程终止行为不同：执行入口不能依赖 `resource`/`preexec_fn`，取消时终止整个后代进程树。由 Task 1 的 `test_worker_imports_on_windows_without_posix_resource` 和 Task 2 的 `test_cancel_terminates_process_tree` 覆盖。
- DataAgent 重启后数据库留下旧 `RUNNING` 状态：启动恢复将其标记为 `FAILED`，不能无限返回 `RUNNING`。由 Task 3 的 `test_startup_marks_unowned_running_executions_failed` 覆盖。

---

### Task 1: 把 Python Runner 执行入口改为跨平台单任务 CLI Worker

**Files:**
- Create: `mateclaw-python-runner/src/runner/worker.py`
- Modify: `mateclaw-python-runner/src/runner/models.py`
- Modify: `mateclaw-python-runner/pyproject.toml`
- Modify: `mateclaw-python-runner/uv.lock`
- Create: `mateclaw-python-runner/tests/test_worker_cli.py`
- Modify: `mateclaw-python-runner/tests/test_result_contract.py`
- Modify: `mateclaw-python-runner/tests/test_prepared_input_contract.py`

**Interfaces:**
- Consumes: 现有 task 字段 `taskId`、`script`、`inputCatalog`、`parameters`、`limits`、`datasetReadEndpoint`、`datasetInputEndpoint`、`preferPreparedInputs`、`resultUploadEndpoint`、`readToken`。
- Produces: CLI `python -m runner.worker --request <request.json> --response <response.json>`；请求/响应 JSON 文件位于 Java 为单任务创建的临时目录。响应沿用当前字段 `taskId/status/output/result/outputRef/stats/error`，但不启动 HTTP listener。

- [ ] **Step 1: 写 Worker CLI 失败测试**

在 `test_worker_cli.py` 覆盖：有效请求执行 `result` 并生成符合 `schemaVersion/kind/data/meta` 契约的 response；用户 `print()` 只写 stdout、不污染 response JSON；Python SDK 能读取 prepared input；缺字段/额外字段返回非零并生成明确错误；Worker 模块可在没有 POSIX `resource` 的平台导入。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd mateclaw-python-runner && python3 -m pytest -q tests/test_worker_cli.py`
Expected: FAIL，因为当前没有 `runner.worker` CLI。

- [ ] **Step 3: 实现单任务 Worker**

在 `worker.py` 实现 `execute_task(request: TaskRequest) -> TaskResponse` 与 `main(argv: Sequence[str] | None = None) -> int`。每个 Python 进程只运行一个脚本；保留 `mateclaw.datasets` 的 prepared/legacy 输入行为、参数传递、结果标准化和受控结果上传。Worker 输入/输出走独立 UTF-8 JSON 文件，stdout/stderr 保留给用户日志。使用 `models.py` 对请求做严格校验；不在用户任务中安装 requirements。

- [ ] **Step 4: 验证 Worker 与既有 Python 契约**

Run: `cd mateclaw-python-runner && python3 -m pytest -q tests/test_worker_cli.py tests/test_dataset_sdk.py tests/test_prepared_input_contract.py tests/test_result_contract.py tests/test_strategy_readout.py`
Expected: 所有测试通过；Worker 直接作为单任务 CLI 工作，不启动 FastAPI/HTTP listener。

---

### Task 2: 在 DataAgent 中实现 Python Worker 子进程监督器

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/PythonWorkerProperties.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/impl/LocalProcessPythonExecutionService.java`
- Create: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/LocalProcessPythonExecutionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 CLI 请求/响应 JSON 字段；现有 `PythonExecutionService` 方法。
- Produces: `LocalProcessPythonExecutionService implements PythonExecutionService`，实现 `submit(Map<String,Object>)`、`getStatus(String)`、`cancel(String)`；提交异步启动进程并立即返回 `RUNNING`，终态返回现有 Runner response map。

- [ ] **Step 1: 写进程监督器失败测试**

测试有效任务提交/查询终态、未知 taskId、超时、stdout 上限、取消进程树、无 Python 可执行文件、response JSON 缺失/损坏、并发任务数上限，以及子进程只获得 allowlist 环境。通过构造器注入 Python 命令和 Worker 根目录，测试不依赖用户机器配置。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=LocalProcessPythonExecutionServiceTest test`
Expected: FAIL，因为本地异步监督器尚未实现。

- [ ] **Step 3: 实现任务状态与进程生命周期**

`PythonWorkerProperties` 绑定 `mateclaw.python.worker`，包含 `PYTHON_COMMAND` 解释器、`MATECLAW_PYTHON_WORKER_HOME` Worker 根目录、任务临时根目录、最大并发数、超时和输出限额。`ProcessBuilder` 以 Worker 根目录为工作目录启动 `python -m runner.worker`，传入 request/response 文件路径；后台线程有界地收集 stdout/stderr，超时/输出超限后终止整个进程树；保存每任务状态、日志、结果、错误和 return code；终态清理临时目录。

- [ ] **Step 4: 运行监督器测试**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=LocalProcessPythonExecutionServiceTest test`
Expected: PASS；无进程残留，超限和异常均有终态，token/应用密钥不出现在子进程环境快照和日志中。

---

### Task 3: 将仪表盘执行接入本地监督器并处理 DataAgent 重启

**Files:**
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DashboardExecutionServiceImpl.java`
- Delete: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/impl/RunnerPythonExecutionService.java`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Create: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/impl/LocalDashboardExecutionIntegrationTest.java`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DashboardExecutionServiceTest.java`
- Delete: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/RunnerPythonExecutionServiceTest.java`

**Interfaces:**
- Consumes: Task 2 的 `PythonExecutionService` 默认实现。
- Produces: `DashboardExecutionServiceImpl` 继续使用相同接口和 HTTP DTO，不再依赖 `MATECLAW_RUNNER_URL`；短期数据集 token 仍由 `MATECLAW_RUNNER_TOKEN_SECRET` 签发与校验。

- [ ] **Step 1: 写接线与恢复测试**

在 `DashboardExecutionServiceTest` 验证现有 Planner → prepared input → task request 字段原样进入本地服务；新增 `test_startup_marks_unowned_running_executions_failed`，断言数据库里无对应本机活动进程的 `RUNNING/SUBMITTING` execution 被标记为 `FAILED`，错误信息为“DataAgent restarted before Python execution completed”，之后 status 不会永远返回 `RUNNING`。通过现有 `DashboardExecutionMapper.selectList` 查询，不增加 Mapper SQL。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=DashboardExecutionServiceTest,LocalDashboardExecutionIntegrationTest test`
Expected: 新恢复和默认本地实现测试失败；既有 API/Schema 测试仍能收集。

- [ ] **Step 3: 切换默认执行实现**

将本地进程服务设为唯一默认 `PythonExecutionService` Bean，删除启动所需的 Runner URL 注入；新增 DataAgent 启动恢复步骤，把无法重新绑定的 `RUNNING/SUBMITTING` 任务标记为 `FAILED`，错误信息为 `DataAgent restarted before Python execution completed`，并在应用关闭时终止 Worker 进程。不要改 Controller 路由、请求字段、权限或 Query Planner。

- [ ] **Step 4: 验证执行编排与失败恢复**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=DashboardExecutionServiceTest,LocalDashboardExecutionIntegrationTest,ScriptTaskReadControllerTest,ScriptTaskPreparationServiceTest test`
Expected: PASS；无 Runner HTTP 地址时仍可完整 submit/status/cancel，prepared input/token/result upload 链路不变。

---

### Task 4: 随 DataAgent 交付 Worker 并移除单独 Runner 启停步骤

**Files:**
- Modify: `mateclaw-dataagent/Dockerfile`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Modify: `docs/策略解读/restart-dataagent-backend.sh`
- Modify: `dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh`（改为只启动 DataAgent）
- Modify: `dev-support/local-simulation/scripts/test_restart_dataagent_backend_build.sh`
- Modify: `dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh`（改测本地 Worker 配置，不再断言 18090 服务）
- Modify: `dev-support/local-simulation/docker-compose.yml`、`dev-support/local-simulation/scripts/start.sh`、`dev-support/local-simulation/scripts/check.sh`（取消默认 Runner 服务依赖；若保留独立网络安全夹具，须置于显式测试 profile）
- Modify: `docs/策略解读/dataagent-local-deployment.md`
- Create: `dev-support/package-dataagent-offline-win.sh`
- Create: `dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh`
- Modify: `mateclaw-python-runner/pyproject.toml` / lock 与 Windows 离线依赖清单
- Delete: `mateclaw-python-runner/src/runner/app.py`
- Delete: `mateclaw-python-runner/src/runner/executor.py`
- Delete: `mateclaw-python-runner/Dockerfile`
- Delete: `mateclaw-python-runner/tests/test_runner_api.py`
- Delete: `mateclaw-python-runner/tests/test_executor.py`

**Interfaces:**
- Consumes: Task 1 的 Worker CLI；Task 2 的配置键；Task 3 的无 HTTP 默认路径。
- Produces: 本地脚本只重建并启动 DataAgent；Docker 镜像中含 Worker 源码和固定依赖；Windows 离线包中 Worker 源码/wheelhouse 与 JAR 一起交付，不提供 Runner 端口或服务健康检查。

- [ ] **Step 1: 写部署约定回归检查**

更新 shell 测试，断言默认 DataAgent 启动流程不再调用 Runner 重启子命令、不监听 18090、不做 `/health` 检查；Worker 命令和运行时位置有明确配置；Compose 默认服务集合无单独 Runner。确认删除 HTTP 服务实现后，`pyproject.toml` 只保留 Worker 实际需要的固定依赖。

- [ ] **Step 2: 运行部署脚本测试确认失败**

Run: `bash dev-support/local-simulation/scripts/test_restart_dataagent_backend_build.sh && bash dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh`
Expected: 旧脚本断言 Runner 必须启动而失败，随后按新约定更新。

- [ ] **Step 3: 更新运行时交付与文档**

DataAgent 镜像复用其现有 Python venv，加入 Worker 源码及锁定的 Worker 依赖，并按约定相对目录放置 Worker。新增离线 Windows 打包脚本，从锁文件导出依赖并下载 Python 3.12 x64 wheels，和 JAR、Worker 源码一起生成 `mateclaw-dataagent/target/dataagent-offline-win-x64.zip`；ZIP 内保持约定目录布局，启动时不在线 pip install。`MATECLAW_PYTHON_WORKER_HOME` 仅作为自定义布局的覆盖项。更新本地部署文档、重启脚本、Compose/start/check 和相关自动化测试。

- [ ] **Step 4: 验证启动不依赖 Runner 服务**

Run: `bash dev-support/local-simulation/scripts/test_restart_dataagent_backend_build.sh && bash dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh && bash dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh && bash -n docs/策略解读/restart-dataagent-backend.sh dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh`
Expected: PASS；全仓检索确认生产/本地默认启动链路不再依赖 `MATECLAW_RUNNER_URL`、端口 18090 或 `uvicorn runner.app:app`；离线 ZIP 中含 JAR、Worker 源码、Windows x64 wheelhouse，且 `unzip -t` 通过。

---

### Task 5: 完成端到端验证、内部浏览器验收和交付

**Files:**
- Test: `mateclaw-python-runner/tests/test_worker_cli.py` 及剩余 Python Worker tests
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/LocalProcessPythonExecutionServiceTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/impl/LocalDashboardExecutionIntegrationTest.java`
- Review: `docs/superpowers/specs/2026-09-29-dataagent-managed-python-execution-design.md`

- [ ] **Step 1: 运行 Python Worker 全量测试**

Run: `cd mateclaw-python-runner && python3 -m pytest -q`
Expected: PASS；新增 Worker CLI、prepared inputs、结果契约和既有策略样例测试通过。

- [ ] **Step 2: 运行 DataAgent 目标测试与构建**

Run: `mvn -f mateclaw-dataagent/pom.xml test`
Then: `mvn -f mateclaw-dataagent/pom.xml package -DskipTests`
Expected: PASS（先确保本地 Maven 仓库中的父 POM、plugin-api/server、mateclaw-sdk 已按依赖顺序安装）。

- [ ] **Step 3: 启动真实 DataAgent/UI 并做内部浏览器验收**

只启动 DataAgent 与 UI，不启动 Python Runner。使用一个有两个 prepared dataset inputs 的仪表盘 Python 卡片验证：页面加载 → 提交执行 → RUNNING 轮询 → 结构化结果渲染 → 查看日志 → 取消另一任务；另验证 Python 抛异常/超时的错误状态。记录浏览器 URL、动作和结果，并通过页面实际状态确认结果契约。

- [ ] **Step 4: 检查最终提交范围并交付**

运行 `git diff --check`、检查冲突标记和 staged paths；确认当前为 `feature/dev_fu`，只暂存 Task 1–4 的本次源码/脚本/文档文件，不暂存生成的 ZIP。浏览器验收和测试全部通过后，创建中文 commit 并 push `feature/dev_fu`，核对远端 SHA。不得把已有用户改动、生成的 `target/`、日志或 `_tmp_` 文件带入提交。
