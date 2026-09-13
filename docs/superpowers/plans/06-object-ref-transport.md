# ObjectRef 与批量传输 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 为 Adapter 与 Runner 提供不经过大 JSON 的 Arrow/Parquet 数据交换和临时对象生命周期；当前优先覆盖脚本大结果与页面受限预览，输入侧远程 ObjectRef 批读作为后续扩展。

**Architecture:** `ObjectRefService` 将批次写入 MinIO/S3 兼容存储，返回只含对象 ID、格式、摘要和过期时间的受控引用。Runner 通过短期签名或 DataAgent 代理读取，引用必须绑定 workspace 和 task。

**Tech Stack:** Java 21、S3 API、Parquet/Arrow、MinIO、JUnit 5、Testcontainers、Docker Compose。

**Spec:** `docs/策略解读/design.md` 第 6、10、13 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 8 节（OBJ-I01～OBJ-I06、OBJ-U01～OBJ-U03）。

**当前状态（2026-09-13）：** MinIO/S3 ObjectRef、Parquet 编解码、TTL、越权和大结果传输已完成验证，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

**本地模拟：** 默认使用 Docker 临时 MinIO 和测试 volume，不要求外部存储服务；正式测试前按总体计划替换对象存储地址、bucket、生命周期和 Secret。

**开发验证配置：** MinIO endpoint 为 `http://127.0.0.1:19000`，bucket 为 `mateclaw-sim`；对象引用只在本地测试上下文使用，仍必须验证 TTL、digest、workspace/task 约束。

**执行约定：** ObjectRef 的签发、读取、过期和越权用例均使用本地 MinIO 实际对象；不得把预签名 URL 或本机路径写入 Dashboard Schema。

**本轮复验记录（2026-09-13）：** 本地 MinIO 健康检查和对象大小契约通过；ObjectRef 大结果链路已在本地 E2E `9 passed` 中验证，真实存储配置仍待正式环境替换。

## Global Constraints

- `dataRef` 不得是任意 URL；必须包含服务端可验证的 `objectId,workspaceId,taskId,expiresAt,digest,format`。
- 预览 JSON 上限为 10 MB、10,000 行、100 列、嵌套深度 5；脚本大结果使用 `dataRef`。当前输入读取仍由 DataAgent 返回受限批次，输入侧远程 `dataRef` 批读待后续接口和 SDK 支持。
- 过期、跨 workspace、跨 task、摘要不匹配均拒绝读取。

---

### Task 1: ObjectRef 服务

**Files:**
- Modify: `mateclaw-dataagent/pom.xml`
- Modify: `mateclaw-dataagent/src/main/resources/application.yml`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/objectref/ObjectRefService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/objectref/S3ObjectRefService.java`
- Modify: `docker-compose.yml`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/objectref/ObjectRefServiceTest.java`

**Interfaces:**
- Consumes: 01 子计划定义的 `vip.mate.dataagent.dataset.ObjectRef`。
- Produces: `put(taskContext,format,inputStream)`、`open(taskContext,objectId)`、`expire(objectId)`。

- [x] **Step 1: 使用 Testcontainers MinIO 写失败集成测试**：覆盖成功读取、TTL、workspace/task 越权、摘要损坏、重复过期，不使用内存 mock 代替 S3 语义。
- [x] **Step 2: 运行**：`mvn -f mateclaw-dataagent/pom.xml -Dtest=ObjectRefServiceTest test`，Expected: FAIL。
- [x] **Step 3: 增加 S3/Parquet 依赖与配置，在 Compose 中增加 MinIO 和独立 bucket 初始化，凭据只从 `.env` 注入**。
- [x] **Step 4: 实现服务和清理任务；日志只记录 objectId/digest，不记录签名 URL**。
- [x] **Step 5: 重跑测试**：ObjectRef/S3 集成测试在 Docker Desktop socket、`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` 和 `TESTCONTAINERS_RYUK_DISABLED=true` 的受控环境通过；提交仍按统一交付边界处理。

> 进度：`ObjectRefServiceTest` 已连接真实 MinIO 容器通过 2/2（无跳过）；Compose/application 已补齐 MinIO、bucket 和内部访问配置，Runner 大结果通过 DataAgent 内部令牌接口写入对象存储；提交仍按统一交付边界处理。

### Task 2: DatasetBatch 列式编码

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/objectref/DatasetBatchCodec.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/objectref/DatasetBatchCodecTest.java`

**Interfaces:**
- Produces: `writeParquet(DatasetBatch,ObjectRefService)` and `readSchema(ObjectRef)`。

- [x] **Step 1: 写失败测试**：数字、日期、时间、Decimal、null、多批次和 Schema 不匹配往返。
- [x] **Step 2: 实现 Parquet 首选编码；Arrow IPC 仅在同版本组件间启用**。
- [x] **Step 3: 运行测试**：Parquet 编解码与 ObjectRef 结果读取定向测试通过；提交仍按统一交付边界处理。

> 进度：已实现 `DatasetBatchCodec.writeParquet/readSchema`，覆盖常见数值、布尔、字符串、null、`LocalDate`、`Instant` 和 `BigDecimal` 的 Parquet logical type 往返；混合小数位会按放大后的有效精度建模，批次内行 Schema 不一致会以 `INVALID_REQUEST` 拒绝，不再静默丢弃字段；专项测试通过，提交仍按统一交付边界处理。当前输入 Adapter 仍返回受限内联批次，未宣称 Python SDK 可直接打开输入 ObjectRef。

> 运行时补充：Python Runner 的大结果已可惰性转换为 Parquet，并通过 DataAgent 内部短期令牌接口写入 `ObjectRefService`；DataAgent 已增加受工作区/任务上下文保护的 Parquet 受限行读取；当前专项测试已覆盖常见数值、布尔、字符串、`null`、`LocalDate`、`Instant`、`BigDecimal` 以及多行读取。本轮新增真实 MinIO 上的 Parquet 编码→内部 HTTP 上传接口→ObjectRef→Schema/行读取往返测试；真实 Runner 容器到 DataAgent Compose 网络的大结果 E2E 已通过，执行结果接口返回 `inline=false`/受控 `outputRef`，页面预览限制为 10 行。证据见 `docs/superpowers/evidence/dashboard-mvp-acceptance.md`；提交仍按统一交付边界处理。
- [x] **Step 3: 运行测试**：Parquet 编解码与 ObjectRef 结果读取定向测试通过；提交仍按统一交付边界处理。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。大结果 `outputRef` 的页面展示由 VIS-UI04、VIS-UI08 通过 CDP 复核；本计划只验收 ObjectRef 授权、TTL、摘要和 Parquet 往返。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=ObjectRefServiceTest,DatasetBatchCodecTest test
```

预期：测试通过 Testcontainers MinIO 验证 OBJ-I01～OBJ-I06，并通过 fake Clock 验证 TTL；OBJ-U01～OBJ-U03 验证内联限制和 Parquet 类型往返。跨 workspace/task、摘要损坏和过期对象都不可读取。

## 验收标准

- OBJ-I01～OBJ-I06、OBJ-U01～OBJ-U03 全部通过且无跳过。
- 测试必须连接真实 MinIO 容器，不以 Map 或本地文件 mock 代替 S3 行为。
- 失败写入不生成可读引用；清理和重复过期具备幂等性。
- 日志扫描不出现签名 URL、access key、secret key、`readToken`。
