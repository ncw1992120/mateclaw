# File Dataset Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 将上传的 Excel、CSV、JSON、Parquet 文件固化为带 Schema 和资源限制的数据集。

**Architecture:** 上传文件进入对象存储并生成不可变对象引用；解析服务抽取 Schema 和预览，正式读取转换为 Arrow/Parquet 批次。DataAgent 不把整份文件加载进 JVM。

**Tech Stack:** Java 21、Apache POI/Jackson/Parquet、S3 API、JUnit 5。

**Spec:** `docs/策略解读/design.md` 第 4.4、5 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 7 节（FILE-U01～FILE-U09）。

**当前状态（2026-09-13）：** CSV/JSON/XLSX/Parquet 受控读取、Schema/资源限制和 Parquet 下推已完成验证，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

**本地模拟：** 文件样本和 MinIO 由 `dev-support/local-simulation/` Docker 环境提供；正式测试前切换 S3 兼容对象存储 endpoint、bucket、prefix 和 Secret。

**开发验证配置：** 使用 `files/fixtures-manifest.json` 固定四种格式的 SHA-256、Schema、过滤预期和 `id` Join 键；对象从 MinIO bucket `mateclaw-sim` 的 `files/<name>` 读取。

**执行约定：** 文件读取必须通过 ObjectRef 和 manifest 校验；新增样本先运行 `scripts/generate-fixtures.py`，再运行模拟前置检查，不接受工作区任意本地路径。

**本轮复验记录（2026-09-13）：** MinIO fixture manifest、CSV/JSON/Parquet/XLSX 对象及 Schema/大小校验通过；文件 Adapter 纳入 DataAgent `152/152` 与本地 E2E `9 passed` 基线。

## Global Constraints

- 只读取平台上传并登记的对象；拒绝本地任意路径和外部 URL。
- 首期支持 `.xlsx`、`.csv`、`.json`、`.parquet`；文件大小、行数、列数和压缩比必须受限。
- Schema 推断记录版本；后续文件不能静默改变已发布数据集字段。

---

### Task 1: 上传登记与 Schema 探测

**Files:**
- Modify: `mateclaw-dataagent/pom.xml`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/file/FileDatasetDefinition.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/file/StoredFileRef.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/DatasetFileStorageService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/service/impl/S3DatasetFileStorageService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/controller/DataAgentDatasetFileController.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/file/FileSchemaInspector.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/file/FileSchemaInspectorTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/controller/DataAgentDatasetFileControllerTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/service/S3DatasetFileStorageServiceTest.java`
- Test fixtures: `mateclaw-dataagent/src/test/resources/datasets/`

**Interfaces:**
- Produces: `POST /v1/dataset-files` → `StoredFileRef`；`inspect(StoredFileRef ref,FileReadOptions options)` → `List<DatasetColumn>`。`StoredFileRef` 只接受上传登记返回的内部对象键，存储客户端和 bucket 配置复用 06，生命周期不同于临时结果引用。

- [x] **Step 1: 添加最小 CSV、JSON、Parquet、XLSX fixture 和失败测试**：当前已覆盖四种格式、扩展名拒绝、字节/列数限制、Schema 漂移、上传控制器上下文、空/损坏文件、读取资源限制和 XLSX 压缩比超限，文件核心错误矩阵已覆盖。
- [x] **Step 2: 运行**：`FileSchemaInspectorTest`、`SchemaVersionValidatorTest` 和 `FileDatasetAdapterTest` 在受控 Docker Maven 环境通过。
- [x] **Step 3: 增加 POI/Parquet 依赖并实现上传登记、流式探测和明确错误码，不读取任意 filesystem path**。
- [x] **Step 4: 重跑测试**，Expected: PASS。

> 进度：已完成 CSV/JSON/XLSX/Parquet 的受限 Schema 探测、真实 Parquet fixture、四类格式对象引用批读、上传登记入口、扩展名/内容签名校验、Schema 版本校验、空文件/损坏文件/字节超限、XLSX 压缩比/条目数/解压后大小限制与 MinIO 持久存储；Parquet 基础比较谓词和请求列已实际下推到读取器，并通过 `PushdownReport` 区分 pushed/residual filters；上传控制器上下文测试已补齐，文件核心错误矩阵已覆盖。

### Task 2: 受控批量读取

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/file/FileDatasetAdapter.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/file/FileDatasetAdapterTest.java`

**Interfaces:**
- Produces: `DatasetSourceAdapter.describe/read` for `FILE`，调用时必须传入 `DatasetAccessContext`。

- [x] **Step 1: 写失败测试**：已覆盖投影、filters（含 `in`/`between`）、limit/offset、Schema 版本不匹配、资源清理，以及 Parquet 投影和基础比较谓词下推。
- [x] **Step 2: 运行测试**，新增用例先因集合过滤、Schema 校验缺失而失败。
- [x] **Step 3: 实现批读；CSV/JSON/XLSX 流式扫描、过滤、投影和分页，Parquet 通过 Avro requested projection 与 Parquet FilterPredicate 对基础比较谓词做列/谓词下推；不支持下推的过滤器保留为 residual filters；读取失败时释放对象流和临时文件**。
- [x] **Step 4: 重跑测试**：`FileDatasetAdapterTest` 已通过；提交由总计划统一管理。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。文件选择、Schema 展示和双源结果页面由 VIS-UI02、VIS-UI08 通过 CDP 复核；本计划只验收格式、资源限制、Schema 和对象生命周期。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=FileSchemaInspectorTest,DataAgentDatasetFileControllerTest,S3DatasetFileStorageServiceTest,FileDatasetAdapterTest test
```

预期：FILE-U01～FILE-U09 全部 PASS；CSV、JSON、Parquet、XLSX 的正常、空和损坏 fixture 都被实际解析；上传失败、Schema 漂移和读取异常后不存在孤儿对象、未关闭流或可读半成品。

## 验收标准

- FILE-U01～FILE-U09 全部通过且无跳过。
- 文件格式以内容检测和允许扩展名共同校验，本地路径和外部 URL 永远不能作为读取入口。
- Parquet 如实报告列/谓词下推；CSV、JSON、XLSX 即使需要流式扫描，也不能把未过滤全集交给 Runner。
- 任何 Schema 变化必须形成新版本或显式失败，不能静默改变已发布数据集。
