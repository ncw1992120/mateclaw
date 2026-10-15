# HTTP API Dataset Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development, then execute this plan task-by-task.

**Goal:** 将已登记的 HTTP API 定义固化为可分页、可参数透传的安全数据集。

**Architecture:** 保存模板化 endpoint、允许参数和结果路径；`HttpApiDatasetAdapter` 只根据声明构建请求。脚本不能传 URL、Header 或凭据，Runner 不直接联网访问数据源。

**Tech Stack:** Java 21、Spring HTTP Client、Jackson、JUnit 5、MockRestServiceServer。

**Spec:** `docs/策略解读/design.md` 第 4.3、5 节。

**Test Matrix:** `docs/superpowers/specs/2026-09-11-dashboard-mvp-test-and-acceptance.md` 第 6 节（HTTP-U01～HTTP-U11）。

**当前状态（2026-09-13）：** HTTP/API 安全策略、分页、重试、过滤透传和 HTTPS 8443 E2E fixture 已完成验证，并纳入候选提交 `fc799a85414520a4118b36d736f01984b773255e`。

**本地模拟：** WireMock 映射和 HTTPS fixture 位于 `dev-support/local-simulation/`（现有 E2E fixture 可复用）；正式测试前替换已登记的测试 API 定义、证书链和认证注入配置。

**开发验证配置：** API 定义读取 `api/orders-openapi.yaml`，endpoint 使用 `https://127.0.0.1:18443/orders`；本地临时证书仅用于模拟，生产 HTTPS/allowlist/truststore 策略不因本地 fixture 放宽。

**执行约定：** 请求、过滤透传、分页和错误分类均以 WireMock 实际请求记录为证据；修改 OpenAPI fixture 时必须同步更新 manifest/检查脚本和定向测试。

**本轮复验记录（2026-09-13）：** WireMock HTTP/HTTPS fixture、OpenAPI 定义和过滤约束检查通过；HTTPS API 场景纳入本地 E2E `9 passed` 矩阵。

## Global Constraints

- URL scheme 只允许 `https`；隔离 E2E 可显式启用固定 `e2e-http:8443` TLS fixture，明文 `http` 例外仍默认关闭。
- 禁止 localhost、loopback、link-local、云元数据地址和重定向到未授权主机。
- 只有声明过的 Query/Body 参数可以接受 `datasets.read` 条件映射。

---

### Task 1: API 定义与安全校验

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/http/HttpApiDatasetDefinition.java`
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/http/HttpApiRequestPolicy.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/http/HttpApiRequestPolicyTest.java`

**Interfaces:**
- Produces: `validate(URI endpoint,List<String> allowedHosts)` and `mapFilters(definition,filters)`。

- [x] **Step 1: 写失败测试**：覆盖允许域名、解析到私网地址、未声明参数、敏感 Header、URL 凭据和非 HTTPS。
- [x] **Step 2: 运行**：测试先因策略类型不存在而失败。
- [x] **Step 3: 实现 fail-closed 校验和结构化参数映射**。
- [x] **Step 4: 重跑测试**，当前策略测试 6/6 通过（包含固定 `e2e-http:8443` TLS fixture），默认生产策略仍拒绝私网和明文 HTTP。

### Task 2: 分页读取与错误分类

**Files:**
- Create: `mateclaw-dataagent/src/main/java/vip/mate/dataagent/dataset/http/HttpApiDatasetAdapter.java`
- Test: `mateclaw-dataagent/src/test/java/vip/mate/dataagent/dataset/http/HttpApiDatasetAdapterTest.java`

**Interfaces:**
- Produces: `DatasetSourceAdapter.describe/read` for `HTTP_API`，调用时必须传入 `DatasetAccessContext`。

- [x] **Step 1: 写失败测试**：覆盖 page/offset/cursor 分页、结果 JSONPath、未声明参数拒绝、5xx/429 错误分类、幂等重试、行数上限、最大响应字节、重定向拒绝、最大页数和 DNS 重绑定再校验。
- [x] **Step 2: 运行测试**，测试先因 Adapter 类型不存在而失败。
- [x] **Step 3: 实现请求、page/offset/cursor 分页、结构化 JSONPath 错误、HTTP 状态错误分类、幂等请求一次重试和 `DatasetBatch` 行数限制；页码按 offset/size 计算并限制最大页数，每次网络尝试前重新校验 DNS 解析结果**。
- [x] **Step 4: 重跑完整 HTTP 测试**：当前 14 个 Adapter 测试通过；page 分页对非 `pageSize` 对齐的行偏移显式拒绝，避免静默返回错误页；生产 RestTemplate 已配置 5 秒连接、30 秒读取超时。

## 视觉验收（CDP）

本子计划没有页面交互，视觉验收为 `N/A（由 07/09 统一验收）`。HTTP/API 来源配置和双源结果页面由 VIS-UI01、VIS-UI08 通过 CDP 复核；本计划只验收白名单、参数透传、分页和错误分类。

本轮关联结果（2026-09-13）：VIS-UI01 的 HTTP/API 数据集可在选择器中选取且页面不出现 SQL；VIS-UI08 的 HTTP/API+文件 Table 在仪表盘预览中因可视容器高度为 `0` 显示空白，保持 `FAIL`。

**状态更正（2026-09-14）：** Table 容器高度修复后，HTTP/API+文件本地模拟 E2E 可见结果行并通过快照回归；上述空白问题为历史记录，当前 VIS-UI08 本地模拟已关闭。

## 测试执行与预期结果

```bash
mvn -f mateclaw-dataagent/pom.xml \
  -Dtest=HttpApiRequestPolicyTest,HttpApiDatasetAdapterTest test
```

预期：HTTP-U01～HTTP-U11 全部 PASS；当前 focused 结果为策略测试 6/6、Adapter 测试 14/14。测试 HTTP Server 记录的 method/path/query/body 与固化定义一致；私网地址、DNS 重绑定、越权重定向和未声明参数均在发送业务请求前失败，固定 `e2e-http:8443` TLS fixture 之外的端口和明文协议均拒绝。

## 验收标准

- HTTP-U01～HTTP-U11 全部通过且无跳过。
- 非幂等请求失败后不自动重试；幂等请求只按明确策略重试。
- 最大页数、响应字节、行数和超时均能终止读取并返回准确错误码。
- 脚本无法提供 URL、Header 或凭据，日志不记录敏感参数值。
