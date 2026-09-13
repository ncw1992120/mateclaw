# 仪表盘全量实施外部前置条件与测试支撑计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to apply this checklist before running the 00–09 implementation plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为总体计划及 00–09 子计划准备完整、可验证且不泄露凭据的外部环境、数据、接口和证据条件；身份与权限完善不纳入本次实施。

**Architecture:** 外部系统只通过已登记的数据源连接和 Adapter 访问；Aloudata 负责指标视图语义与结果，JDBC/API/文件提供可复现测试数据，DataAgent 负责查询和任务治理；现有访问上下文仅保持兼容，不在本次完善身份、角色或跨工作区权限。Python Runner 只通过 `datasets.read` 读取任务声明的输入。所有真实凭据通过 shell、CI Secret 或加密配置注入，不进入本文件、Git、日志或截图。

**Tech Stack:** Aloudata AnyMetrics/Semantic API、MySQL 8.4、PostgreSQL 15.6、HTTPS API、CSV/JSON/Parquet/XLSX、S3 兼容 MinIO、Docker Compose、Python 3.12、Polars/Pandas/PyArrow、Playwright。

**Spec:** `docs/superpowers/plans/2026-09-11-dashboard-overall-implementation-plan.md`、`docs/策略解读/design.md`、`docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md`。

**当前状态（2026-09-13）：** 本地模拟依赖栈已建立，并通过清理后重新启动、健康检查、数据初始化和接口读取验证；候选 SHA `fc799a85414520a4118b36d736f01984b773255e` 上的本地 E2E Compose 全量矩阵为 `9 passed`，DataAgent Docker Maven 全量为 `152/152`，Runner 为 `20/20`，UI 为 `24/24`。当前 Aloudata 真实结果访问仍待外部条件满足，候选提交已完成但远端推送待重试；平台内 AI 自动生成不属于本期；身份与权限完善已明确延期。

**本轮复验（2026-09-13）：** 当前工作树执行 `make dashboard-prerequisites-simulation` 和 `./scripts/verify-dashboard-external-prerequisites.sh --local` 均通过；设计门禁同时输出 `DESIGN-PASS`。Aloudata Adapter 的显式外部测试使用 Docker Maven + 本地 WireMock 环境变量运行通过，认证值仍为本地占位符，不计入真实 ALO-X02。

**真实外部 Gate 预检（2026-09-13）：** 当前 shell 未注入任何 `ALOU_DATA_*` 外部测试变量；执行 `./scripts/verify-dashboard-external-prerequisites.sh --external` 立即以退出码 `3` 报告 `ALOU_DATA_EXTERNAL_TEST=true 未设置`，未发起真实网络请求。待安全注入完整变量后再执行 ALO-X01/X02。

## Global Constraints

- 外部条件只支撑 JDBC、Aloudata 已有指标视图、HTTP/API、文件和 Python 多源预处理；不为 Trino、DuckDB、JS 或平台内 AI 生成准备首期依赖。
- Aloudata 只选择和查询已有指标视图；不要求 MateClaw 创建、修改或删除视图。
- JDBC 数据库账号必须是最小权限只读账号；只有 JDBC 数据集可以配置 SQL。
- HTTP/API 必须是已登记的 HTTPS 地址；脚本和 Runner 不接触 URL、Header 或凭据。
- 文件必须通过对象存储对象引用登记；不得把本地绝对路径或签名 URL 写入 Dashboard Schema。
- 真实认证值、密码、Token、签名 URL 和带敏感字段的响应原文不得进入文档、日志、截图、测试报告或提交。
- 没有真实外部条件时，测试必须 fail-fast 或显式 `BLOCKED`，不得使用 mock、`test.skip`、空数据或历史 PASS 替代真实验收。

## 1. 条件分级与最终 Gate

| 优先级 | 条件 | 阻塞范围 | 负责人/提供方 | 验证方式 |
| --- | --- | --- | --- | --- |
| P0 | Docker、Java 21、Python/uv、Node/npm、浏览器和本地端口可用 | 所有本地测试与 Compose | 实施环境维护者 | 基线命令、Compose healthcheck |
| P0 | MySQL 8.4、PostgreSQL 15.6 测试连接和只读账号 | 03 JDBC、07 管理、09 E2E | 数据库环境提供方 | 连接、迁移、过滤/分页集成测试 |
| P0 | Docker 临时 MinIO（S3 兼容）及测试 bucket | 05 文件、06 ObjectRef、08 Runner | 实施环境维护者 | `docker-compose.test.yml` healthcheck、上传/读取/TTL/清理 |
| P0 | Aloudata 产品/语义地址、默认 tenantId、认证上下文和已授权视图 | 02 Aloudata、09 JDBC+Aloudata E2E | Aloudata 管理员 | ALO-X01、ALO-X02 |
| P1 | HTTPS API 定义、参数/分页/结果映射和可重复响应 | 04 HTTP/API、09 API+文件 E2E | API 所有者 | WireMock/真实 API 请求摘要 |
| P1 | 四种文件样本及对象登记信息 | 05、06、07、09 | 业务数据提供方 | Schema、过滤、坏文件和资源限制 |
| P1 | 可复现的双源 Join 键、筛选条件和期望结果 | 08、09 | 业务/测试负责人 | Python 脚本断言与页面结果 |
| P2 | 候选 SHA 提交边界和发布凭据 | 00–09 最终交付 | 仓库维护者 | 同 SHA 全量验收、Git 状态/远端校验 |

P0 未全部满足前，不得声称“全量实施计划完成”。本次不把身份与权限条件作为 Gate；相关能力只做既有行为回归，完整角色矩阵和跨工作区授权列入后续计划。P1 缺失只阻塞对应来源场景；P2 缺失时本地实现仍可验证，但最终 Gate 不得关闭。

## 2. Aloudata 外部条件（02、09）

### 2.1 连接与网络信息

需要由 Aloudata 管理员提供或确认以下信息（认证值只通过安全渠道提供）：

| 项目 | 要求 | 当前基线/备注 |
| --- | --- | --- |
| 产品层地址 | 可访问 AnyMetrics 目录/详情接口的 HTTPS URL，包含协议和端口 | 当前探测基线为 `https://demo.can.aloudata.com:443` |
| 语义层地址 | 可访问 Semantic 结果接口的 URL，包含协议和端口 | 当前探测基线为 `http://semantic.demo.can.aloudata.com:80`；若生产要求 HTTPS，应提供证书链/代理地址 |
| 默认租户 | 连接级 `tenantId`，是唯一查询上下文 | 不与 MateClaw 数据源名称绑定，也不要求名称含租户 |
| 认证方式 | `UID` 等明确枚举值 | 与当前 Aloudata 部署约定一致 |
| 认证值 | UID/Token 等敏感值 | 只注入环境变量或加密配置，不写文件和日志 |
| TLS/代理 | CA、企业代理、出站白名单、DNS 解析要求 | 需要能从 DataAgent 容器访问两个地址 |
| 部署版本 | 产品层和语义层版本 | 用于解释接口字段和筛选能力差异 |

### 2.2 必须开放或确认的接口

1. `GET /anymetrics/api/v1/analysisview/treeList`：返回当前租户可见的指标视图树。
2. `GET /anymetrics/api/v1/analysisview/queryByName?viewName=<encoded>`：返回一个视图的完整详情。
3. `GET /semantic/api/v1.1/analysisView/query`：无临时筛选时查询视图结果，至少支持 `viewName`、`pageIndex`、`pageSize`、`queryResultType=DATA`。
4. `POST /semantic/api/v1.1/metrics/query`：当视图结果接口不支持临时筛选时，用视图详情编译等价的指标/维度/过滤请求。

每个接口需要确认：HTTP 方法、认证 Header 名称、租户 Header 名称、响应 envelope（`code`/`success`/`data`）、分页基准（零基/一基）、最大 `pageSize`、超时和错误码。

### 2.3 已授权指标视图数据要求

至少提供一个在当前认证上下文中同时满足以下条件的稳定视图：

- 目录接口可见，详情接口可读；
- `analysisView/query` 能成功返回至少 5 行数据，而不是 `SM_02_0038`；
- 详情包含至少一个指标和一个维度；
- 至少一个维度或时间字段允许 `eq` 条件，并且筛选前后结果可观察到变化；
- 字段名称、数据类型、排序/默认过滤和时间约束在联调期间不变；
- 结果中不得包含生产敏感信息，优先使用脱敏或专用验收视图；
- 允许测试账号在同一 `tenantId` 下重复读取，不因短期缓存或数据刷新随机改变预期。

建议提供以下脱敏视图摘要（不提供完整响应原文）：

```text
viewName: <stable-view-name>
dimensions: [<dimension-a>, <date-column>]
metrics: [<metric-a>]
filterable: <dimension-a>=<known-value>
expected: baseline_rows=5, filtered_rows != baseline_rows
```

### 2.4 Aloudata 验收前置检查

- [ ] 管理员确认产品层和语义层地址、版本、证书和网络白名单。
- [ ] 管理员提供一个已授权 `viewName`、对应 MateClaw 数据源连接 ID 和可筛选字段/值。
- [ ] 运行目录/详情探测并记录脱敏 HTTP 状态、Aloudata `code`、耗时和字段摘要。
- [ ] 运行 5 行无筛选查询；若返回 `SM_02_0038`，记录 `VIEW_ACCESS_DENIED` 并停止该 Gate。
- [ ] 运行一个维度或时间筛选，确认请求进入远端且结果变化；不能只在 JVM/Python 后置过滤。
- [ ] 验证错误响应、日志和截图不含认证值。

外部测试变量（值不写入文档）：

```text
ALOU_DATA_EXTERNAL_TEST=true
ALOU_DATA_PRODUCT_BASE_URL=<secret-managed-url>
ALOU_DATA_SEMANTIC_BASE_URL=<secret-managed-url>
ALOU_DATA_TENANT_ID=<secret-managed-tenant>
ALOU_DATA_AUTH_TYPE=<secret-managed-auth-type>
ALOU_DATA_AUTH_VALUE=<secret-managed-auth-value>
ALOU_DATA_TEST_DATASOURCE_ID=<mateclaw-datasource-id>
ALOU_DATA_TEST_VIEW_NAME=<authorized-view-name>
ALOU_DATA_TEST_FILTER_COLUMN=<declared-dimension-or-time-field>
ALOU_DATA_TEST_FILTER_VALUE=<non-sensitive-test-value>
```

## 3. JDBC 数据源条件（03、07、08、09）

### 3.1 最低数据库矩阵

首期至少准备：

- MySQL 8.4：验证表数据集、固化 SQL 数据集、过滤、投影、分页、日期、Decimal、null、超时和行数限制；
- PostgreSQL 15.6：验证 JDBC 方言、日期/时间、Decimal、null、分页和连接归还；
- Doris/StarRocks 只在有真实环境和明确方言验收需求时追加，不得把协议兼容当作已验证。

每个数据库需要提供：

| 信息 | 要求 |
| --- | --- |
| Host/port | 容器网络或可达 DNS，禁止使用不稳定个人机器地址 |
| Database/schema | 测试专用 database/schema，不与业务生产写入混用 |
| Username/password | 最小权限只读账号；密码通过 Secret 注入 |
| TLS | 是否强制 TLS、CA/客户端证书要求 |
| 连接限制 | 最大连接数、查询超时、空闲超时、并发配额 |
| 版本 | 精确镜像 tag 或服务器版本 |

### 3.2 必须准备的表和数据

建议使用专用 `orders`、`customers` 两张表，至少包含：

```sql
orders(
  id BIGINT PRIMARY KEY,
  customer_id BIGINT,
  order_date TIMESTAMP,
  status VARCHAR(32),
  amount DECIMAL(18,2),
  nullable_note VARCHAR(255)
)
customers(
  id BIGINT PRIMARY KEY,
  customer_name VARCHAR(128),
  region VARCHAR(64)
)
```

数据必须同时包含：

- 至少 10 行订单，其中 `PAID`、`CANCELLED` 和其他状态均有记录；
- 至少一个可 Join 的 `customer_id`，至少一个无匹配值；
- 两个日期范围、两个 Decimal 精度值、一个 null 值和一个中文/Unicode 值；
- 可预测的过滤结果，例如 `status=PAID` 命中行数固定且不为 0；
- 一组超过预览上限但低于数据库资源上限的数据，用于 ObjectRef/行数限制测试；
- 测试数据版本号或 checksum，防止测试过程中被外部任务修改。

禁止在样例中使用真实客户姓名、手机号、身份证、Token、密码或生产主键。

### 3.3 JDBC 数据集配置

必须分别登记以下两种数据集：

1. `JDBC_TABLE`：指向 `orders` 表，允许字段投影、`status`/`order_date` 过滤和分页。
2. `JDBC_SQL`：使用单条只读 SQL，例如：

```sql
SELECT id, customer_id, order_date, status, amount
FROM orders
WHERE amount >= :min_amount
ORDER BY order_date DESC
```

需要提供参数定义 `min_amount` 的类型和值范围，但不得把值拼进 SQL 文本。还要准备 DML、DDL、多语句、未知字段、恶意参数、字符串字面量含分号和尾部注释等负例。

## 4. HTTP/API 数据源条件（04、07、09）

### 4.1 API 所有者需要提供

- 已登记的 HTTPS endpoint；
- OpenAPI 文件或等价接口说明：方法、路径、参数位置、类型、必填性、默认值、枚举和最大值；
- 认证方式及由 DataAgent 注入的 Header/Query/Cookie 名称；
- 响应 JSON 样例和结果 JSONPath；
- 分页方式：`page`、`offset` 或 `cursor`，字段名、起始值、终止条件和最大页数；
- 幂等性说明：GET/幂等 POST 才允许一次重试，非幂等请求必须明确禁止重试；
- 预期的 2xx、4xx、429、5xx、超时和错误响应样例；
- 是否允许重定向、允许的最终主机和 DNS 解析范围；
- 测试数据固定窗口和可重复的筛选值，例如 `status=PAID`。

### 4.2 API 验收清单

- [ ] API 已在某个 MateClaw HTTP 数据源的 `connectionParams.apiDefinitions` 中登记；数据集只引用 `apiDefinitionId`。
- [ ] DataAgent 容器可通过 HTTPS 访问，证书链和 DNS 校验通过。
- [ ] `status=PAID` 等结构化参数能到达服务端；脚本不能注入 URL、Header 或凭据。
- [ ] page/offset/cursor 至少选择一种真实分页模式验证；非对齐 offset、超页数和空结果有明确结果。
- [ ] 429/5xx、结果路径错误、超时和重定向策略取得可重复响应。
- [ ] API 所有者确认测试响应不包含生产敏感数据。

隔离 E2E 已内置 WireMock HTTPS 8443 fixture；它只证明本地 TLS、参数透传和过滤约束，不替代真实 API 所有者提供的外部接口验收。

## 5. 文件与对象存储条件（05、06、07、08、09）

### 5.1 四种最低样本

至少提供以下脱敏文件，并给出 checksum、格式版本、编码和预期 Schema：

| 格式 | 最小内容 | 必须覆盖 |
| --- | --- | --- |
| CSV | `orders.csv`，10–100 行 | UTF-8、表头、空值、中文、PAID/CANCELLED |
| JSON | 行对象数组或约定 envelope | 类型混合、缺失字段、嵌套深度边界 |
| Parquet | 带 Schema 和至少一个 row group | 列投影、比较谓词下推、批读取 |
| XLSX | 至少一个工作表和表头 | 日期、Decimal、空单元格、公式值策略 |

每个文件必须说明对象 ID、格式、字节数、行数、列数、Schema 版本、SHA-256、是否允许替换、预期过滤行数、Join 键、数据授权、保留期限和清理负责人。

不需要业务方提供危险文件；空文件、损坏文件、超字节、超列数、Schema 漂移和 XLSX 压缩限制由隔离测试 fixture 生成。

### 5.2 对象存储条件（本次采用 Docker 临时部署）

- 开发测试直接启动 `dev-support/local-simulation/docker-compose.yml` 中的 MinIO，不要求业务方或外部存储管理员提前提供 bucket；现有 E2E 的 `docker-compose.test.yml` 保持独立，不与本地模拟配置混用；
- Compose 为 MinIO 创建隔离测试 bucket、固定测试账号和健康检查，启动脚本上传四种脱敏 fixture 到稳定的 `files/<name>` 对象键；DataAgent 可写入临时 ObjectRef，Runner 只能经受控内部接口读取；
- 测试结束使用 Compose 清理临时对象和卷，禁止连接生产 bucket；
- 生产 S3/MinIO endpoint、region、bucket、生命周期和密钥管理不属于本次交付，另列后续部署计划；
- 不把签名 URL、access key、secret key 或宿主路径写入 Dashboard、日志和截图。

## 6. Python Runner 与脚本条件（08、09）

### 6.1 固定运行环境

- 可拉取或本地构建固定版本的 `mateclaw-python-runner` 镜像；
- 镜像内预装并锁定 `pandas`、`polars`、`pyarrow`；不安装 DuckDB，不支持运行时 `pip install`；
- 容器以非 root、只读根文件系统和临时工作目录运行；
- Runner 能访问 DataAgent 内部读取接口和对象存储，不能访问任意外网、宿主目录或 Docker Socket；
- 任务可被取消、超时终止，内存/单文件/日志上限可配置且有可观察结果。

### 6.2 脚本与期望结果

脚本读取条件必须显式写在 `datasets.read`：

```python
paid = {"field": "status", "operator": "eq", "value": "PAID"}
orders = datasets.read("orders_api", filters=[paid], columns=["id", "customer_id", "amount"])
files = datasets.read("orders_file", filters=[paid], columns=["id", "customer_id", "amount"])
result = orders.join(files, on="id", how="inner")
print(result)
```

需要同时提供成功脚本、未知字段/操作符负例、跨任务 token 负例、未声明别名负例、超时脚本、失败后可重试脚本和大结果脚本。脚本不得出现连接字符串、凭据、`pip install`、任意 URL 或本地绝对路径。

为每个双源场景提供输入别名、字段列表、过滤条件、Join 键、预期行数/列名/关键值、允许的排序差异和空结果行为。

## 7. 身份、工作区与权限条件（后续，不作为本次前置）

本次不新增或完善 viewer/member/admin 角色、跨工作区授权、数据源可见性矩阵和真实 JWT 登录验收。现有代码中的访问上下文、Controller/Service 校验和已有拒绝用例仅作为兼容回归，不扩展为本次功能 Gate。

后续若纳入权限实施，再单独准备短期 JWT、workspace ID、角色映射和安全刷新方式；凭据不得写入脚本、截图、trace、CI 输出或 Git。

## 8. 本地前置条件完成记录

本地开发不等待上述真实外部资源，统一使用 `dev-support/local-simulation/` 和独立 E2E Compose。当前已完成的可复核入口如下：

```bash
./dev-support/local-simulation/scripts/start.sh
./dev-support/local-simulation/scripts/check.sh
```

通过条件：MySQL `13306`、PostgreSQL `15432`、MinIO `19000/19001`、WireMock HTTP/HTTPS `18081/18443`、Python Runner 均为 healthy；fixture manifest 的行数、文件集合、字节数、SHA-256 和 MinIO 对象大小校验通过。

执行 E2E 时必须先执行 `dev-support/local-simulation/scripts/cleanup.sh` 释放同端口资源，再运行 `scripts/e2e/start-dashboard-mvp.sh`、simulation seed 和 Playwright；E2E 结束后清理 `docker-compose.test.yml` 专属容器/卷并重新执行上述本地模拟启动与检查。E2E 启动脚本会检测仍运行的 `mateclaw-local-sim-*` 容器并以退出码 `2` fail-fast。

本地模拟条件不等于正式外部条件：真实 Aloudata 必须补齐 ALO-X01/ALO-X02，正式 S3/MinIO 需替换 endpoint/bucket/Secret；这些条件未满足时不得关闭对应 Gate。平台内 AI 生成不属于本期，不要求模型或 Agent 配置。

## 8. Docker、网络与观测条件（00、05、06、08、09）

- Docker Desktop 和 Testcontainers socket 可用；JDK 21 容器可运行 Maven；
- Python 3.12/uv、Node/npm、Chromium/Chrome channel 可用；
- 端口 18089（DataAgent）、18090（Runner）、5174（UI）及 E2E 映射端口不冲突；
- DataAgent、Runner、MinIO、MySQL、WireMock 都有健康检查；
- 内部网络隔离必须能证明脚本外部 DNS/HTTPS 访问失败；
- 每个真实场景保留脱敏状态码、公共错误码、非敏感 ID、下推报告、Runner 状态时间线、截图和清理结果；
- ObjectRef 证据只包含 objectId/digest/format/TTL，不包含签名 URL；
- 证据绑定同一候选 SHA 后才能关闭 E2E-06。

### 8.1 本地模拟环境目录与职责

本地模拟依赖统一放在当前项目的 `dev-support/local-simulation/`，不新建独立 Git 项目，也不依赖系统临时目录。建议目录如下：

```text
dev-support/local-simulation/
├── docker-compose.yml
├── .env.example
├── mysql/init/
├── postgres/init/
├── minio/{init-minio.sh,seed-minio.sh}
├── api/orders-openapi.yaml
├── wiremock/mappings/
├── wiremock/init-tls.sh
├── files/
└── scripts/{start.sh,check.sh,cleanup.sh,generate-fixtures.py}
```

- MySQL/PostgreSQL 容器提供 JDBC 表、固化 SQL 和 Join 样本；
- MinIO 容器提供隔离测试 bucket、文件对象和大结果 ObjectRef；
- WireMock 提供 HTTP/API 与 Aloudata 目录、详情、结果的脱敏固定响应；
- `api/` 提供脱敏 OpenAPI 参数契约，WireMock TLS 初始化脚本生成本地 HTTPS 证书；
- Python Runner 复用项目固定 Dockerfile，在 `runner_internal` internal 网络中提供 `/health` 和外网阻断验证；
- `files/` 只保存小型脱敏 CSV/JSON/Parquet/XLSX fixture，生成的大对象放 Docker Volume；
- `.env.local`、访问密钥、日志和 trace 不进 Git；
- `docker-compose.test.yml` 继续服务于现有 E2E，不与本地模拟环境混用生产配置。

启动与清理入口：

```bash
docker compose -f dev-support/local-simulation/docker-compose.yml \
  --env-file dev-support/local-simulation/.env.local up -d
docker compose -f dev-support/local-simulation/docker-compose.yml down -v
```

## 9. 外部条件收集表

| 编号 | 条件 | 提供方 | 安全存放位置 | 验证入口 | 关联计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| EXT-ALO-01 | Aloudata 产品/语义地址和版本 | Aloudata 管理员 | Secret/加密配置 | ALO-X01 | 02/09 | 待提供 |
| EXT-ALO-02 | 默认 tenantId 和认证上下文 | Aloudata 管理员 | Secret/加密配置 | ALO-X01 | 02/09 | 待提供 |
| EXT-ALO-03 | 已授权稳定视图及筛选字段/值 | Aloudata 管理员 | 视图目录 + Secret ID | ALO-X02 | 02/09 | 阻塞；当前缺 `ALOU_DATA_TEST_VIEW_NAME`、筛选字段/值和结果查询授权 |
| EXT-JDBC-01 | MySQL 8.4 只读连接 | DB 管理员 | Secret | JDBC-I01/I02 | 03/07/09 | 本地模拟已验证；真实连接待提供 |
| EXT-JDBC-02 | PostgreSQL 15.6 只读连接 | DB 管理员 | Secret | JDBC-I03/I05 | 03/07 | 本地模拟已验证；真实连接待提供 |
| EXT-JDBC-03 | orders/customers 脱敏样本 | 测试负责人 | 测试 DB | migration/integration | 03/09 | 本地样本已准备；正式样本待确认 |
| EXT-API-01 | 登记的 HTTPS API 定义 | API 所有者 | DataSource 加密配置 | HTTP-U01～U11 | 04/07/09 | 本地 OpenAPI/HTTPS fixture 已验证；正式定义待提供 |
| EXT-FILE-01 | 四种文件样本及 checksum | 业务/测试负责人 | 测试 bucket | FILE-U01～U09 | 05/06/09 | 本地四种样本已准备；正式样本待确认 |
| EXT-OBJ-01 | Docker 临时 MinIO、测试 bucket 和清理 | 实施环境维护者 | `dev-support/local-simulation/` | OBJ-I01～I06 | 06/08/09 | 已完成本地验证 |
| EXT-AUTH-01 | 身份、workspace、角色矩阵 | 后续权限项目 | 后续单独定义 | 权限专项验收 | 未来 | 延期，不阻塞本次 |
| EXT-DEL-01 | 候选 SHA 提交边界 | 仓库维护者 | Git 审批记录 | E2E-06 | 00–09 | 已完成本地候选提交 `fc799a85`；远端推送待重试 |
| EXT-LOCAL-01 | 本地模拟 Compose、初始化脚本和脱敏 fixture | 实施环境维护者 | `dev-support/local-simulation/` | healthcheck + seed/cleanup + manifest checksum | 00–09 | 已完成本地验证（10 行 fixture，start 等待长期服务 healthy） |

> 当前已收到产品层/语义层地址、默认租户和 UID 认证方式；仍需 Aloudata 管理员确认一个允许结果查询的 `viewName`，并由项目维护者提供对应的 MateClaw `datasourceId`、可筛选字段和值，才能填充外部测试变量并执行 ALO-X02。

## 10. 一次性预检与解除顺序

1. 启动 Docker 临时 MinIO，并核对 EXT-ALO/JDBC/API/FILE 条件，保存脱敏摘要。
2. 运行设计门禁、DataAgent/Runner/UI 基线和 Compose healthcheck。
3. 使用当前已有测试上下文完成非 Aloudata seed/Playwright/cleanup；不新增真实 JWT/角色矩阵验收。
4. 注入已授权 Aloudata 视图，完成 ALO-X01/X02 和 JDBC+Aloudata 双源场景。
5. 用户确认提交范围后生成候选 SHA，在同一 SHA 重跑全量矩阵并生成最终证据。

推荐命令：

```bash
make dashboard-prerequisites-local
bash scripts/verify-dashboard-design.sh
docker compose -f docker-compose.test.yml config --quiet
docker compose -f docker-compose.test.yml up -d e2e-mysql e2e-http minio mateclaw-dataagent python-runner
curl --fail http://127.0.0.1:18089/dataagent/api/actuator/health
curl --fail http://127.0.0.1:18090/health
ALOU_DATA_EXTERNAL_TEST=true mvn -f mateclaw-dataagent/pom.xml -Dtest=AloudataAnalysisViewExternalIT test
bash scripts/e2e/seed-dashboard-mvp.sh
npm --prefix mateclaw-dataagent-ui run test:e2e
bash scripts/e2e/verify-dashboard-mvp-cleanup.sh
```

## 验收定义

- P0 条件全部满足（MinIO 由 Docker 临时部署）；P1 场景都有对应数据和接口证据；身份与权限专项不计入本次 Gate；平台内 AI 生成不纳入本期，不要求真实 LLM E2E。
- 外部凭据均可追溯到安全存放位置，但文档、日志、截图和测试输出中不存在凭据值。
- 每个 EXT-* 都有提供方、验证入口、关联计划和状态；阻塞项有明确解除条件。
- 只有 Aloudata 结果、两条双源 E2E 和候选 SHA 全部取得证据，才可将本次范围标记完成；身份与权限完善不作为本次完成条件。

## 11. 本轮本地模拟实施记录

执行入口：

```bash
./scripts/verify-dashboard-external-prerequisites.sh --local
./dev-support/local-simulation/scripts/start.sh
./dev-support/local-simulation/scripts/check.sh
```

已验证结果：

- Compose 配置解析和全部 shell 脚本语法通过；
- MySQL 8.4、PostgreSQL 15.6、MinIO、WireMock HTTP/HTTPS 和 Python Runner 容器均为 healthy；本地 HTTPS 端口为 18443，使用临时 PKCS12 证书；Runner 使用项目固定 Dockerfile，并在独立 `internal` 网络中运行；
- 本地模拟 fixture 两个数据库均初始化 10 行 `orders` 和 3 行 `PAID`，包含无匹配 Join 键、Decimal、null、日期、中文及多种状态；可直接用于本地开发验证，正式环境仍需按本节记录数据版本与 checksum；
- MinIO `mateclaw-sim` bucket 已创建并上传 `orders.csv`、`orders.json`、`orders.parquet`、`orders.xlsx`，对象键统一为 `files/<name>`；`fixtures-manifest.json` 固化四种文件的字节数、SHA-256、Schema、过滤预期和 Join 键；
- WireMock `/orders`、Aloudata tree/detail/analysis result/metrics query 模拟接口均取得预期响应；`api/orders-openapi.yaml` 的 OpenAPI 3 参数契约也通过前置检查；
- `.env.aloudata-simulation.example` 提供本地 `tenantId`、UID 占位值、`datasourceId=9001`、`local_sales_view` 及 `region=east` 筛选参数；它只用于 WireMock 模拟，不替代真实 Aloudata HTTPS 验收；
- 使用上述模拟变量运行 `AloudataAnalysisViewExternalIT` 已通过，覆盖目录、详情、模拟基线和筛选结果变化；真实环境仍须使用独立的 HTTPS/truststore 和授权视图重新执行；
- 清理入口 `./dev-support/local-simulation/scripts/cleanup.sh` 可删除容器、网络和本地测试卷。
- 根目录统一入口 `./scripts/verify-dashboard-external-prerequisites.sh --local` 已通过；`--simulation` 还会校验本地 Aloudata tenant/view/filter 占位配置；入口会实际列出 MinIO 四个对象并检查 Runner；`--external` 在缺少真实 Aloudata 变量时返回明确 `BLOCKED`（退出码 3），且不打印认证值。
- 独立 Dashboard E2E 可通过 `MATECLAW_E2E_ALOUDATA_MODE=simulation` 复用 E2E HTTPS WireMock，自动 seed `local_sales_view` 数据源/数据集；该模式只验证本地 Adapter、`datasets.read` 过滤下推和页面编排，不得替代真实 Aloudata 视图授权与 ALO-X02。
- Runner 网络复验：`/health` 返回 `status=UP`，访问外部 DNS/HTTPS 失败，证明本地 Runner 未获得任意外网访问能力。

当前未覆盖：真实 Aloudata 结果权限、正式测试环境对象存储、真实 API 所有者接口和身份权限专项。
