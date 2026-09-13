# Dataset Catalog 与统一输入契约 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 扩展现有数据集模型，使四类来源（对应五种来源类型，JDBC 表与 JDBC SQL 分开建模）通过稳定别名、Schema 和统一读取契约被组件与 Python 使用。

**Architecture:** 保留现有 `DatasetEntity`/`DatasetManageService`，通过向后兼容字段扩展来源类型和来源配置。新增 `dataset` 包存放只读执行契约；Adapter 只消费该契约，不依赖 UI DTO。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Flyway、JUnit 5、Jackson。

**Spec:** `docs/策略解读/design.md` 第 3、5、6 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 3 节（CAT-U01～CAT-DB02）。

**当前状态（2026-09-13）：** 契约、目录服务、五种来源类型路由及 CAT-U06/CAT-U07 已在本地完成并验证，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

**本地模拟：** 数据源连接和文件 ObjectRef 的开发数据由 `dev-support/local-simulation/` 提供；本计划不引入新的身份与权限模型。

**开发验证：** 使用本地 MySQL/PostgreSQL 的 `orders/customers` 和 MinIO `mateclaw-sim/files/<name>` 创建目录记录；执行 `make dashboard-prerequisites-simulation` 后再运行 CAT-U/CAT-DB 用例。

**执行约定：** 本子计划只消费模拟环境中已登记的连接和 ObjectRef，不新增外部账号或数据；目录响应需能被 02～09 直接复用。

**本轮复验记录（2026-09-13）：** 模拟 MySQL/PostgreSQL、MinIO 和 WireMock 健康检查通过，fixture manifest 与对象大小校验通过；目录契约定向测试纳入 DataAgent `153/153` 全量基线。

## Global Constraints

- `datasetId` 是内部稳定 ID；脚本只使用本任务唯一的 `inputName`。
- Descriptor 不包含事实数据、连接凭据或未授权数据集。
- 数据源权限必须在 Service 层再次校验，不能只依赖 Controller 注解。

---

### Task 1: 定义纯 Java 契约

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetSourceType.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetColumn.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetFilter.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetAccessContext.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/ObjectRef.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetReadRequest.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetInputDescriptor.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetBatch.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/PushdownReport.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetReadErrorCode.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetReadException.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/DatasetSourceAdapter.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/DatasetContractTest.java`

**Interfaces:**
- Produces: `DatasetSourceAdapter.supports(DatasetSourceType)`、`describe(DatasetAccessContext context,long datasetId)`、`read(DatasetAccessContext context,DatasetReadRequest request)`。
- `DatasetReadRequest`: `datasetId,inputName,columns,filters,limit,offset,parameters`。
- `DatasetAccessContext`: `workspaceId,userId,taskId,allowedDatasetIds`；只能由认证后的服务端构造。
- `ObjectRef`: `objectId,workspaceId,taskId,format,digest,expiresAt`；只定义传输契约，存储与授权实现由 06 子计划完成。
- `PushdownReport`: `pushedFilters,residualFilters,projectionPushed,limitPushed,sourceQueryDigest`；四个 Adapter 实现都必须如实填写，覆盖五种来源类型。
- `DatasetReadErrorCode`: 至少区分 `ACCESS_DENIED,INVALID_REQUEST,UNSUPPORTED_FILTER,SOURCE_TIMEOUT,SOURCE_UNAVAILABLE,RESULT_LIMIT_EXCEEDED`，来源专用错误可以扩展但不能改写公共语义。

- [x] **Step 1: 写失败测试**：序列化 Descriptor 后不得出现 `password`、`authValue`、`jdbcUrl`；未知字段角色和操作符必须拒绝。
- [x] **Step 2: 运行失败测试**

```bash
mvn -f mateclaw-dataagent/pom.xml -Dtest=DatasetContractTest test
```

Expected: FAIL，契约类型尚不存在。

- [x] **Step 3: 实现最小契约**：使用不可变 record/enum；`DatasetBatch` 只保存 Schema、行数、预览行或 `ObjectRef`，禁止同时携带大行集和引用。
- [x] **Step 4: 重跑测试**，Expected: PASS。
- [x] **统一 Adapter 选择回归补强**：`DatasetExecutionServiceTest` 已参数化覆盖 JDBC 表、JDBC SQL、Aloudata 指标视图、HTTP/API 和文件五种首期来源，均通过同一 `DatasetSourceAdapter`/`DatasetAccessContext` 契约选择描述器。
- [x] **统一 Adapter 读取与缺失实现边界**：`DatasetExecutionServiceTest` 以 `CAT-U06/CAT-U07` 覆盖五种来源实际 `read` 路由，以及目录来源无对应 Adapter 时的 `INVALID_REQUEST` 拒绝。
- [x] **Step 5: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

### Task 2: 扩展持久化与目录服务

**Files:**
- Modify: `mateclaw-dataagent/pom.xml`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/model/DatasetEntity.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dto/DatasetVO.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/DatasetManageService.java`
- Modify: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/DatasetManageServiceImpl.java`
- Create: `mateclaw-dataagent/src/main/resources/db/migration/mysql/V218__extend_dataset_source_contract.sql`
- Create: `mateclaw-dataagent/src/main/resources/db/migration/postgresql/V218__extend_dataset_source_contract.sql`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/DatasetCatalogServiceTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/migration/DatasetCatalogMigrationMysqlIT.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/migration/DatasetCatalogMigrationPostgresIT.java`

**Interfaces:**
- Produces: `DatasetManageService.getInputDescriptor(DatasetAccessContext context,Long datasetId,String inputName)`。
- Persists: `source_type`、`source_config`、`schema_version`；旧记录默认 `JDBC_TABLE`。

- [x] **Step 1: 写失败测试**：旧数据集可读取；不可见数据集拒绝；Descriptor 不泄露 `source_config` 中的凭据。
- [x] **Step 2: 运行测试**：先因统一目录契约和服务入口不存在而失败。
- [x] **Step 3: 添加双数据库迁移和最小服务实现**，保持现有 `/v1/datasets` 响应字段兼容。
- [x] **Step 4: 运行契约与目录服务测试**，Expected: PASS；MySQL 8.4 和 PostgreSQL 15.6 均已用真实 Testcontainers 从模拟 V217 数据集表连续迁移至 V220，旧记录保留 `JDBC_TABLE`，文件数据集可使用空 `datasource_id`。MySQL 运行时仅有 Flyway 对 8.4 高于已测试版本的兼容性提示，无迁移失败。
- [x] **Step 5: 统一交付节点**：已纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。Descriptor、目录和权限结果在 07/09 的真实页面中通过 VIS-UI02～VIS-UI03 复核，不在本子计划重复创建页面证据。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=DatasetContractTest,DatasetCatalogServiceTest test
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=DatasetExecutionServiceTest test
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=DatasetCatalogMigrationMysqlIT,DatasetCatalogMigrationPostgresIT test
```

预期：CAT-U01～CAT-S03、CAT-U06、CAT-U07 全部 PASS；MySQL/PostgreSQL 均可从现有版本连续迁移至 V220，旧数据集按 `JDBC_TABLE` 读取，文件数据集允许无 `datasource_id`，越权请求没有调用 Adapter，序列化结果不含敏感字段。

## 验收标准

- CAT-U01～CAT-DB02 全部通过且无跳过。
- `DatasetSourceAdapter`、`DatasetAccessContext`、`DatasetReadRequest`、`ObjectRef`、错误码和 `PushdownReport` 字段与后续计划完全一致。
- 两种数据库迁移语义一致，现有 `/v1/datasets` 调用方不需要一次性修改。
