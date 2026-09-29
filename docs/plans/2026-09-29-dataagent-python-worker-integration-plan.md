# DataAgent Python Worker 集成与内存治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将洞察 Python Worker 源码与依赖定义迁入 DataAgent 模块，修复本地解释器/依赖选择，并确保任务终态先持久化、内存缓存有界。

**Architecture:** Python Worker 仍由 DataAgent 通过 `ProcessBuilder` 启动为本机子进程，但源码目录、锁文件、依赖清单和测试归属 `mateclaw-dataagent/python-worker/`。DataAgent 优先使用显式 `PYTHON_COMMAND`，否则发现模块内虚拟环境并验证 Worker 依赖。Worker 完成时同步持久化终态，再按 TTL/容量清理终态缓存；并发默认保持 2，超额快速失败，不增加排队。

**Tech Stack:** Java 21、Spring Boot、Python 3.12、uv、Polars、PyArrow、JUnit 5、pytest、Maven、内部浏览器。

**Spec:** `docs/superpowers/specs/2026-09-29-dataagent-managed-python-execution-design.md`

## Global Constraints

- 开发直接使用本地 `feature/dev_fu`，不创建 worktree；保留所有不相关工作区改动。
- Python Worker 是无 HTTP listener 的 CLI 子进程，不改为 JVM 内嵌解释器，也不增加独立部署服务。
- 删除旧目录前，先迁移全部受跟踪文件，并保留未跟踪的 `mateclaw-python-runner/tests/test_strategy_contribution_script.py`。
- 不在线安装用户脚本依赖；Python 虚拟环境不提交 Git。
- 终态任务必须先写入数据库，随后才可从内存状态缓存淘汰；淘汰后状态、日志、结果 API 仍返回持久化结果。
- 默认 Python Worker 并发上限为 2；超额任务继续快速失败，不实现排队。
- 只有内部浏览器真实仪表盘执行验证通过后，才中文 commit 并 push `feature/dev_fu`。

## Review Focus

- 配置了无效 Python 路径或缺少 Polars/PyArrow：执行前给出解释器路径、缺失依赖和本地修复命令，不返回难定位的通用错误。
- Windows 使用 `.venv/Scripts/python.exe`，macOS/Linux 使用 `.venv/bin/python`：两种布局均能正确发现。
- Worker 执行完毕但前端暂时未轮询：终态仍由完成事件写入数据库，缓存淘汰后查询不可退回过期的 `RUNNING`。
- 并发达到 2 后再提交任务：不启动第 3 个子进程，保持明确快速失败。
- Worker 输出上限/超时/取消后：活动进程与临时目录被清理，终态仍可查询。

---

### Task 1: 将 Python 包与测试迁入 DataAgent 模块

**Files:**
- Move: `mateclaw-python-runner/src/` → `mateclaw-dataagent/python-worker/src/`
- Move: `mateclaw-python-runner/tests/` → `mateclaw-dataagent/python-worker/tests/`
- Move: `mateclaw-python-runner/examples/` → `mateclaw-dataagent/python-worker/examples/`
- Move: `mateclaw-python-runner/pyproject.toml`, `uv.lock`, `requirements-worker.txt` → `mateclaw-dataagent/python-worker/`
- Modify: `mateclaw-dataagent/python-worker/pyproject.toml`（包名与项目路径）和锁文件
- Preserve/move: 未跟踪 `mateclaw-python-runner/tests/test_strategy_contribution_script.py`
- Delete: 完成验证后的整个 `mateclaw-python-runner/`

**Interfaces:**
- Consumes: 当前 `runner.worker` CLI、`mateclaw.datasets` SDK 和结果信封契约。
- Produces: DataAgent 内 `python-worker/src` 可用 `python -m runner.worker` 启动，且 Python 测试可在新目录运行。

- [x] **Step 1: 迁移前盘点所有文件和 Git 状态**

Run: `git status --short -- mateclaw-python-runner && git ls-files mateclaw-python-runner`
Expected: 精确列出待迁移的受跟踪文件和未跟踪策略脚本；禁止覆盖或丢弃未跟踪文件。

- [x] **Step 2: 迁移代码后先运行原 Python 契约测试**

Run: `uv run --directory mateclaw-dataagent/python-worker --offline -- pytest -q`
Expected: Worker CLI、Dataset SDK、结果契约及迁入的策略脚本测试通过。

- [x] **Step 3: 更新 Python 项目元数据与锁文件**

将包名改为 DataAgent Worker 的项目名；保留依赖版本语义，检查锁文件可由 `uv sync --locked --offline` 读取。

- [x] **Step 4: 删除旧源码目录并验证迁移没有遗漏**

Run: `test ! -e mateclaw-python-runner && rg -n "mateclaw-python-runner" --glob '!docs/plans/**' --glob '!docs/superpowers/**'`
Expected: 旧目录不存在；生产源码、脚本和打包流程不再依赖旧路径。

### Task 2: 修复 Worker 路径和 Python 解释器/依赖发现

**Files:**
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/impl/LocalProcessPythonExecutionService.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/PythonWorkerProperties.java`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/LocalProcessPythonExecutionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `mateclaw-dataagent/python-worker` 布局。
- Produces: 显式 `PYTHON_COMMAND` 优先；未配置时依次发现 Windows/macOS/Linux 的模块内 `.venv` Python，再检查系统 Python。执行前验证 Worker 固定依赖可导入；失败消息指出所用路径、缺失模块和 `uv sync --locked` 修复命令。

- [x] **Step 1: 为 DataAgent 工作目录和仓库根目录两种启动方式写失败测试**

断言从 `mateclaw-dataagent/`、仓库根目录均能解析新 Worker 根目录；显式 `MATECLAW_PYTHON_WORKER_HOME` 仍优先。

- [x] **Step 2: 为解释器优先级和依赖缺失写失败测试**

断言显式命令优先、POSIX venv 与 Windows venv 可发现、无兼容环境时提示缺失依赖及修复命令。

- [x] **Step 3: 运行测试确认失败，再实现解析和预检**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=LocalProcessPythonExecutionServiceTest test`
Expected: 新增用例先因旧路径/默认解释器策略失败；实现后同一命令全部通过。

### Task 3: 完成结果持久化后淘汰终态任务缓存

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/PythonWorkerCompletedEvent.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/impl/LocalProcessPythonExecutionService.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DashboardExecutionServiceImpl.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/PythonWorkerProperties.java`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/LocalProcessPythonExecutionServiceTest.java`
- Modify: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/impl/LocalDashboardExecutionIntegrationTest.java`

**Interfaces:**
- Event: `PythonWorkerCompletedEvent(Map<String, Object> taskSnapshot)`，由本地 Worker 进入终态时发布。
- Dashboard listener: 读取相应 execution 记录并复用现有 `updateFromRunner(...)` 持久化 status/output/error/return code。
- Cache policy: 已结束任务状态、日志和结果默认保留 `5m`，之后清理；运行中的任务不清理。最多保留 `256` 个终态任务；活动任务继续受 max-concurrent=2 保护。

- [x] **Step 1: 添加事件持久化失败测试**

断言 Worker 完成事件能将终态、日志、结构化结果和错误保存到执行表，即使浏览器未继续轮询也不留下永久 `RUNNING`。

- [x] **Step 2: 添加 TTL、容量上限和淘汰后读取测试**

断言过期/超量时只淘汰终态；淘汰后调用 dashboard status/log/result 仍能从数据库返回已持久化内容；活动任务不被淘汰。

- [x] **Step 3: 运行失败测试，再实现事件持久化和有界缓存**

Run: `mvn -f mateclaw-dataagent/pom.xml -Dtest=LocalProcessPythonExecutionServiceTest,LocalDashboardExecutionIntegrationTest test`
Expected: 新增测试先失败；实现后任务终态持久化、缓存淘汰及回退查询测试通过。

- [x] **Step 4: 验证并发保护没有回归**

断言已运行 2 个任务时第 3 个提交立即失败，Worker 子进程总数不超过 2，不进入等待队列。

### Task 4: 更新 DataAgent 打包、IDEA 指引和本地启动

**Files:**
- Modify: `mateclaw-dataagent/Dockerfile`
- Modify: `dev-support/package-dataagent-offline-win.sh`
- Modify: `dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh`
- Modify: `dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh`
- Modify: `dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh`
- Modify: `docs/策略解读/dataagent-local-deployment.md`
- Modify: `mateclaw-dataagent/pom.xml`（仅当 Maven 需校验/打包 worker resources 时）

**Interfaces:**
- Docker 与 Windows 离线 ZIP 从 `mateclaw-dataagent/python-worker` 读取源码和依赖清单。
- IDEA 指引提供 `uv sync --locked` 和 `PYTHON_COMMAND` 配置；不要求启动 Python HTTP 服务。

- [x] **Step 1: 更新打包脚本测试并确认其因旧路径失败**

Run: `bash dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh && bash dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh`

- [x] **Step 2: 将 Docker、离线 ZIP、本地启动路径改为 DataAgent 内部 Worker**

保留 Windows x64 Python 3.12 wheelhouse 和无在线安装约束；删除所有打包器对仓库根目录旧模块的引用。

- [x] **Step 3: 更新 IDEA/本地运行文档**

说明 Python 仍由 DataAgent 作为本地子进程管理；首次在联网环境同步锁定依赖，离线机器安装 wheelhouse；明确不需要独立 Runner 服务。

- [ ] **Step 4: 运行打包、脚本语法和生产路径检查**

Run: `bash dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh && bash dev-support/local-simulation/scripts/test_launchagent_dataagent_runner_startup.sh && bash -n dev-support/package-dataagent-offline-win.sh dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh docs/策略解读/restart-dataagent-backend.sh`
Then check active runtime consumers with `rg -n "mateclaw-python-runner" mateclaw-dataagent/src/main mateclaw-dataagent/Dockerfile mateclaw-dataagent/python-worker dev-support/package-dataagent-offline-win.sh dev-support/local-simulation/scripts/start-dataagent-with-python-runner.sh docs/策略解读/restart-dataagent-backend.sh docs/策略解读/dataagent-local-deployment.md Makefile`.
Expected: tests and Bash syntax pass; the scoped `rg` returns no matches. Historical plans/reports and negative regression assertions may retain the old name intentionally.

### Task 5: 全量验证并浏览器验收

**Files:**
- Test: `mateclaw-dataagent/python-worker/tests/`
- Test: DataAgent Worker 与仪表盘执行 JUnit 测试
- Verify: 当前 Chrome 内部浏览器中的“测试”仪表盘卡片1

**Interfaces:**
- Uses Tasks 1–4 的最终源码布局和执行/持久化契约。
- Produces: 实际浏览器中双数据集 Python 脚本执行成功、结果集 schema 生成、结果值可见；并能查看日志与后续状态。

- [ ] **Step 1: 运行 Python Worker 全量测试和 DataAgent 目标测试**

Run: `uv run --directory mateclaw-dataagent/python-worker --offline -- pytest -q`
Then: `mvn -f mateclaw-dataagent/pom.xml test`

- [ ] **Step 2: 构建 DataAgent 并验证离线包结构**

Run: `mvn -f mateclaw-dataagent/pom.xml package -DskipTests && bash dev-support/local-simulation/scripts/test_package_dataagent_offline_win.sh`
Expected: 构建成功，发布产物只含一个 DataAgent 服务和内部 Worker 文件，不含独立 HTTP Runner 服务。

- [ ] **Step 3: 在内部浏览器验证真实仪表盘脚本**

使用用户“测试”仪表盘卡片1的两个数据集执行求和脚本；确认 Worker 数量有界、状态终态持久化、结果 schema/卡片可读。记录 URL、页面状态和执行结果。构建、单测或接口直调不能代替此项。

- [ ] **Step 4: 检查工作区并按项目规约交付**

运行 `git diff --check`，检查冲突标记和 staged paths；只暂存本任务迁移/实现文件，保留其他已有改动。浏览器与测试通过后创建中文 commit，push `feature/dev_fu` 并核对远端 SHA。
