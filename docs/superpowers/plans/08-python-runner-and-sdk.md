# Python Runner 与 SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 提供固定依赖、隔离进程和 `datasets.read` SDK 的新仪表盘 Python 执行路径。

**Architecture:** 新建独立 `mateclaw-python-runner` 服务，任务 HTTP 承担控制面，`dataRef` 承担数据面。`datasets.read` 使用短期任务令牌回调 DataAgent 的内部读取接口；DataAgent 只允许令牌声明的输入别名并再次做权限、Schema 和资源校验。旧 `PythonAnalysisTool` 继续调用 `LocalCodeExecutorService`。

**Tech Stack:** Python 3.12、uv lock、FastAPI、Pydantic、Polars/Pandas/PyArrow、pytest、Docker；Java 21/Spring HTTP Client。

**Spec:** `docs/策略解读/design.md` 第 6 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 10 节（PY-U01～U05、PY-R01～R06、PY-J01～J03）。

**当前状态（2026-09-13）：** 固定 Runner 镜像、SDK、任务隔离、ObjectRef 读取和旧执行器兼容已完成验证，Runner 17/17；统一交付节点待用户确认。

**本地模拟：** Runner 通过本地 Compose 访问 DataAgent 和临时 MinIO；正式测试前只替换内部服务地址、对象存储地址和镜像版本，不改变无运行时 `pip install` 约束。

## Global Constraints

- 镜像构建时固定依赖版本，任务请求没有 requirements 字段。
- 每个任务独立子进程和临时目录；网络出口仅允许 DataAgent 内部读取接口和受控对象存储，根文件系统只读并使用非 root 用户。
- Runner 不能接收数据库凭据，也不能自行访问数据源。
- 任务级读取令牌必须绑定 `taskId,workspaceId,allowedInputNames,expiresAt`，只能访问 DataAgent 内部读取接口，任务结束或取消后立即失效。
- `readToken` 和对象签名不得写入任务日志、错误堆栈或审计详情；审计只记录 task、inputName、datasetId 和查询摘要。

---

### Task 1: Python SDK

**Files:**
- Create: `mateclaw-python-runner/pyproject.toml`
- Create: `mateclaw-python-runner/uv.lock`
- Create: `mateclaw-python-runner/src/mateclaw/datasets.py`
- Create: `mateclaw-python-runner/src/mateclaw/filters.py`
- Create: `mateclaw-python-runner/src/mateclaw/params.py`
- Create: `mateclaw-python-runner/src/mateclaw/types.py`
- Test: `mateclaw-python-runner/tests/test_dataset_sdk.py`

**Interfaces:**
- Produces: `datasets.read(input_name,columns=None,filters=None)` → `DatasetInput` with `schema,to_polars,to_pandas,iter_batches`；SDK 不接受 `datasetId` 或连接信息。

- [x] **Step 1: 写失败 pytest**：别名读取、参数引用、非法字段、Descriptor 解析、Parquet 批读和 DataFrame 后置过滤不影响请求。
- [x] **Step 2: 运行**：`uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests/test_dataset_sdk.py -q`，Expected: FAIL。
- [x] **Step 3: 实现最小 SDK；读取请求必须带任务令牌经 Runner 上下文转发给 DataAgent**。
- [x] **Step 4: 重跑测试**：SDK/Runner 测试通过（当前 `17 passed`）；`datasets.read` 客户端会在发起网络请求前拒绝非法字段、未知操作符和非映射过滤值，兼容 DataAgent `R.ok(...)` 响应包装层，并将业务错误转换为明确异常；提交仍按统一交付边界处理。

### Task 2: Runner 控制面和进程隔离

**Files:**
- Create: `mateclaw-python-runner/src/runner/app.py`
- Create: `mateclaw-python-runner/src/runner/executor.py`
- Create: `mateclaw-python-runner/src/runner/models.py`
- Create: `mateclaw-python-runner/Dockerfile`
- Test: `mateclaw-python-runner/tests/test_runner_api.py`
- Test: `mateclaw-python-runner/tests/test_executor.py`

**Interfaces:**
- Produces: `POST /v1/tasks`、`GET /v1/tasks/{id}`、`POST /v1/tasks/{id}/cancel`、`GET /health`。
- Task request: `taskId,script,inputCatalog,parameters,limits,datasetReadEndpoint,resultUploadEndpoint,readToken`; response: `status,result,outputRef,outputSchema,stats,error`。两个 endpoint 均由 DataAgent 配置生成，不能由用户脚本覆盖。

- [x] **Step 1: 写失败测试**：成功、语法错误、超时、取消、输出超限、拒绝非白名单网络、禁止跨目录文件访问、无 requirements 字段。
- [x] **Step 2: 运行 `uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests/test_runner_api.py mateclaw-python-runner/tests/test_executor.py -q`**，Expected: FAIL。
- [x] **Step 3: 实现 FastAPI 和独立子进程执行；强制终止进程树并截断日志**。
- [x] **Step 4: 使用锁文件构建镜像，并验证非 root、只读目录、健康检查及只连接 `runner_internal` 内部网络**。

- [x] **Step 3: 运行 Java 测试和 Compose 健康检查**：历史工作树记录曾为 `140/140`；当前工作树补充统一执行服务五类来源读取回归后重新执行，DataAgent 全量为 `146/146`，均在 Docker Desktop/Testcontainers 环境通过。Compose 中 `python-runner` 与 MinIO 健康检查通过，Runner `/health` 返回 `{"status":"UP"}`。
- [x] **Step 5: 重跑 pytest**：Runner 测试 `17 passed`，镜像非 root、只读根目录、无 DuckDB/运行时安装依赖基线已验证；2026-09-12 14:08 在当前工作树重新执行全量 pytest 仍为 `17 passed`；提交仍按统一交付边界处理。
- 最新 DataAgent 回归补充了统一执行服务未知来源拒绝测试，当前全量为 `147/147`；前述 `140/140`、`146/146` 均为历史工作树计数。

### Task 3: DataAgent 客户端与兼容回归

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/PythonExecutionService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/impl/RunnerPythonExecutionService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/internal/ScriptDatasetReadController.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/ScriptDatasetReadTokenService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/code/ScriptTaskInputRegistry.java`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/RunnerPythonExecutionServiceTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/code/LocalCodeExecutorCompatibilityTest.java`

**Interfaces:**
- Produces: `submit,getStatus,cancel` 和 `POST /internal/v1/script-tasks/{taskId}/datasets/read`；`ScriptTaskInputRegistry` 保存任务期内不可变的 `inputName → datasetId/descriptor` 映射；consumes Runner HTTP API。

- [x] **Step 1（基础安全与兼容用例）**：已覆盖输入别名严格校验、任务级令牌跨任务拒绝、`datasetId` 仅由注册别名解析、Runner status/cancel 路径、危险 taskId 拒绝，以及旧 Local 执行器继续接受 `requirement`。
- [x] **Step 2: 实现客户端、任务输入注册表、内部读取接口、短期令牌和 Compose 服务；读取接口只接受 `inputName,columns,filters` 并忽略/拒绝客户端提供的 `datasetId`；DataAgent 不挂载 Docker Socket**。
- [x] **Step 3: 运行 Java 测试和 Compose 健康检查**：历史工作树记录曾为 `140/140`；当前工作树补充统一执行服务五类来源读取回归后重新执行，DataAgent 全量为 `146/146`，均在 Docker Desktop/Testcontainers 环境通过。Compose 中 `python-runner` 与 MinIO 健康检查通过，Runner `/health` 返回 `{"status":"UP"}`。
- [ ] **Step 4: 统一交付节点（待用户确认）**：`feat: connect dataagent to python runner`。

> 进度：已加入 `RunnerPythonExecutionService`、`ScriptTaskInputRegistry`、`ScriptTaskPreparationService`、HMAC 短期读取令牌、内部别名读取 Controller 和 Compose `runner_internal` 网络；客户端 status/cancel、危险 taskId、Runner 不可用、过期令牌、任务别名隔离、旧 Local `requirement` 兼容测试均已通过。Compose 首次健康验证发现 Dockerfile 仅复制 `/app/src` 但未设置模块搜索路径，导致 `uvicorn` 重启并报 `ModuleNotFoundError: No module named 'runner'`；已通过设置 `PYTHONPATH=/app/src` 修复并复测通过。资源限制与外部网络阻断已有定向证据，带测试凭据的正式 Compose 内部连通性验证已完成，证据见 `docs/superpowers/evidence/dataagent-compose-internal-connectivity-2026-09-12.md`。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。脚本配置、执行状态、错误、取消、重试和大结果展示由 VIS-UI03～VIS-UI06 通过 CDP 复核；本计划只验收 SDK、Runner、进程隔离和兼容回归。

## 测试执行与预期结果

```bash
uv run --project mateclaw-python-runner pytest mateclaw-python-runner/tests -q
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=RunnerPythonExecutionServiceTest,LocalCodeExecutorCompatibilityTest test
docker build -t mateclaw-python-runner:test mateclaw-python-runner
docker run --rm --entrypoint id mateclaw-python-runner:test
docker run --rm --read-only --tmpfs /tmp --entrypoint python \
  mateclaw-python-runner:test \
  -c "import importlib.util; assert importlib.util.find_spec('duckdb') is None"
```

预期：PY-U01～U05、PY-R01～R06、PY-J01～J03 全部 PASS；超时和取消后无子进程；非法网络/文件访问失败但 DataAgent/MinIO 内部访问成功；`id` 输出的 uid 不为 0，镜像可在只读根目录运行且依赖清单不含 DuckDB；新路径不调用 `LocalCodeExecutorService`。

## 验收标准

- PY-U01～U05、PY-R01～R06、PY-J01～J03 全部通过且无跳过。
- SDK 请求只含别名、字段、条件和任务上下文，伪造 `datasetId`、跨任务/过期 token 在 Adapter 前被拒绝。
- 任务成功、失败、超时、取消和资源超限各有稳定终态；输出半成品不可读。
- 旧 Agent Python 行为保持兼容，新 Runner 不接受 requirements 或运行时 `pip install`。
- Runner 容器满足非 root、只读根目录、内部网络白名单和无 Docker Socket。

## 标准脚本样例

脚本只使用管理界面分配的输入别名，不直接填写内部 `datasetId`。需要下推到数据源的条件必须放在 `datasets.read(..., filters=...)` 中：

```python
import polars as pl
from mateclaw import datasets, filters, params

# input_name 是仪表盘输入面板中配置的别名，而不是数据集 ID。
orders = datasets.read(
    input_name="orders",
    # 只请求脚本实际需要的列，减少返回数据量。
    columns=["user_id", "order_date", "amount"],
    # 这些条件会随读取请求发送给 DataAgent，并由来源 Adapter 尝试下推。
    filters=[
        filters.gte("order_date", params.get("start_date")),
        filters.lte("order_date", params.get("end_date")),
    ],
)

# 读取返回后再做的 DataFrame 过滤只属于 Python 本地处理，不会下推。
result = orders.to_polars().filter(pl.col("amount") > 0)
```

执行前，DataAgent 会根据别名解析数据集、校验字段和操作符、复核权限，并返回受控批次或 `dataRef`；SDK 会在发起请求前拒绝非法字段、未知操作符和非 mapping 过滤值。
