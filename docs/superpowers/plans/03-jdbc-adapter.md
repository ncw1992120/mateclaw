# JDBC Dataset Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 安全执行 JDBC 固化 SQL，并将 `datasets.read` 的投影、过滤和分页下推到目标数据库。

**Architecture:** `JdbcDatasetAdapter` 读取已固化基础 SQL，交给独立 `SqlValidationService` 解析为 AST，再生成占位 SQL 和有序参数；连接与凭据仍由 `DatasourceConnectionPoolService` 管理。

**Tech Stack:** Java 21、JDBC、MyBatis-Plus JSqlParser、JUnit 5、Testcontainers MySQL/PostgreSQL。

**Spec:** `docs/策略解读/design.md` 第 4.2、5 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 5 节（JDBC-U01～JDBC-I05）。

**当前状态（2026-09-13）：** JDBC 表/SQL Adapter、AST 校验、参数绑定及 MySQL/PostgreSQL 集成验证已完成；统一交付节点待用户确认。

**本地模拟：** MySQL/PostgreSQL 使用 `dev-support/local-simulation/` Docker 容器和脱敏初始化 SQL；正式测试前替换测试环境只读连接 Secret。

**开发验证配置：** MySQL 使用 `127.0.0.1:13306/mateclaw_sim`，PostgreSQL 使用 `127.0.0.1:15432/mateclaw_sim`；两者均以只读模拟账号读取 `orders`，覆盖 `PAID` 过滤、Decimal、null、日期和无匹配 Join 键。

**执行约定：** SQL/参数和 `pushedFilters` 证据必须来自上述容器的实际查询；不得用静态 SQL 字符串或纯 mock 代替数据库执行。

**本轮复验记录（2026-09-13）：** 本地 MySQL 8.4、PostgreSQL 15.6 容器保持健康，`orders/customers` fixture 可重复读取；JDBC 定向回归纳入 DataAgent `152/152` 全量基线。

## Global Constraints

- 只允许单条 `SELECT`/`WITH`；拒绝 DML、DDL、会话命令和多语句。
- 过滤条件只能追加，不能删除或覆盖基础 SQL 条件。
- SQL 方言由目标数据库执行；平台不承诺跨数据库同义。

---

### Task 1: SQL 校验和编译

**Files:**
- Modify: `mateclaw-dataagent/pom.xml`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/jdbc/SqlValidationService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/jdbc/JSqlParserValidationService.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/jdbc/CompiledJdbcQuery.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/jdbc/JSqlParserValidationServiceTest.java`

**Interfaces:**
- Produces: `compile(String baseSql,List<String> columns,List<DatasetFilter> filters,int limit,int offset)` → `CompiledJdbcQuery(sql,parameters,digest)`。

- [x] **Step 1: 写失败测试**：覆盖既有 WHERE、WITH、ORDER BY、LIMIT、DML、多语句、未知字段、集合参数和分页上限。
- [x] **Step 2: 运行**：测试先因校验服务不存在而失败。
- [x] **Step 3: 实现 AST 校验、外层安全追加和参数顺序，不使用字符串拼接参数值**。
- [x] **Step 4: 重跑测试**，当前 5/5 通过；多语句通过 JSqlParser AST 数量判断，字符串字面量分号和尾部注释均可安全编译。
- [ ] **Step 5: 统一交付节点（待用户确认）**：`feat: compile safe jdbc dataset queries`。

### Task 2: JDBC 执行与下推报告

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/jdbc/JdbcDatasetAdapter.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/jdbc/JdbcDatasetAdapterTest.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/jdbc/JdbcDatasetAdapterMysqlIT.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/jdbc/JdbcDatasetAdapterPostgresIT.java`

**Interfaces:**
- Produces: `DatasetSourceAdapter.describe/read` for `JDBC_TABLE` and `JDBC_SQL`，调用时必须传入 `DatasetAccessContext`。
- Consumes: `DatasourceConnectionPoolService`、`CompiledJdbcQuery`。

- [x] **Step 1: 增加 Testcontainers 测试依赖并写 MySQL/PostgreSQL 集成测试**：插入命中/不命中数据，断言返回行数、PreparedStatement 参数、超时、分页及报告中的 `pushedFilters`；Doris/StarRocks 未经真实环境验证前不得标为已验收方言。
- [x] **Step 2: 运行测试**：首次运行暴露嵌套网络问题；改用宿主网络和 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` 后真实测试可达。
- [x] **Step 3: 实现只读连接、查询超时、最大行数、结果 Schema 和审计摘要**；MySQL/PostgreSQL 容器集成测试已执行并通过，方言扩展仍需真实环境验证。
- [x] **Step 4: 重跑 JDBC 单元测试和两个容器集成测试**，Expected: PASS；SQL/JDBC 单元测试通过，MySQL/PostgreSQL 集成测试各 1/1、0 跳过。
- [ ] **Step 5: 统一交付节点（待用户确认）**：`feat: add jdbc dataset adapter`。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。SQL 配置页面和双源结果页面分别由 VIS-UI01、VIS-UI08 通过 CDP 复核；本计划只验收 SQL、参数和下推报告的自动/集成测试证据。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=JSqlParserValidationServiceTest,JdbcDatasetAdapterTest test
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=JdbcDatasetAdapterMysqlIT,JdbcDatasetAdapterPostgresIT test
```

预期：JDBC-U01～JDBC-U07 验证 AST、安全绑定和审计摘要；JDBC-I01～JDBC-I05 在真实 MySQL/PostgreSQL 容器验证结果、类型、超时和连接归还。恶意参数只作为绑定值，不改变 SQL 结构。

## 验收标准

- JDBC-U01～JDBC-I05 全部通过且无跳过。
- DML、DDL、多语句和无法安全改写的 SQL 在获取连接或执行前失败。
- `PushdownReport` 与实际执行 SQL 一致，SQL/digest/日志不包含敏感参数值。
- MySQL、PostgreSQL 可以标记已验收；Doris、StarRocks 只有完成真实环境用例后才能加入已验收方言列表。
