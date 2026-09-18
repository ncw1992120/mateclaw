# 洞察-仪表盘-组件-卡片-添加数据集：Aloudata 指标视图本地 Mock 设计

> 版本：v1.1（2026-09-18）—— v1.1 依据**真实环境实测**修正了字段形状与报文样例，并记录落地实现
> 范围：`洞察 → 仪表盘 → 组件库 → 卡片 → 添加数据集 → Aloudata.指标视图` 及其「字段名称默认值」链路
> 数据来源：`generated_cljd_zcl.sql`（原 PostgreSQL 造数脚本，已转为**代码内 mock 数据**）
> 交付形态：本文档 + `mock/aloudata/*.json` 夹具 + `LocalAloudataApiClient` 切面

---

## 0. 结论速览

| 问题 | 结论 |
|---|---|
| 前端是否直连 Aloudata？ | **否**。全部经 `mateclaw-dataagent` 后端中转，前端只感知 dataagent 的 `/dataagent/api/v1/**` 接口 |
| 「字段名称默认值」是不是一个独立接口？ | **不是**。它就是 `GET /v1/datasources/{id}/analysis-views/{viewName}/fields`，前端按 `displayName` 生成默认值 |
| Mock 应该切在哪一层？ | **`AloudataApiClient.callWithParams`**（`@Profile("local-mock")` + `@Primary` 覆写），一处收口覆盖全部 7 个上游端点 |
| 需要 mock 几个上游端点？ | **7 个**：`analysis_view_tree` / `analysis_view_list` / `analysis_view_query_by_name` / `metric_batch_detail` / `dimension_list` / `analysis_view_query_data` / `metrics_query` |
| mock 报文格式依据 | **真实 Aloudata 实测响应**（§6 附实测样本；夹具由脚本按同一结构生成） |
| `queryByName` 的 `metrics`/`dimensions` 形状 | **已实测定论：字符串数组**（`["AUM", ...]`），展示名单独在 `data.displayNameMap`。本仓 `listFields` 的读法正确，`getByName` 的 `mapList` 是 bug（详情接口会 500），已修，见 §8-P0-1 |
| 上游 `id` 与 `viewName` 的关系 | 真实 `id` 是 **int**（实测 `824`），`viewName` 是名称（实测 `cs202609161430`）→ **两者不相等**，§8-P0-2 的坑实测存在 |
| 是否零代码改动？ | 否。需要修 `getByName` 的字符串数组解析（P0-1）；P0-3 已在 mock 层消除（owner 跟随数据源认证值） |

**非目标**：不改动真实 Aloudata 调用路径的默认行为（未激活 `local-mock` 时该 Bean 不会被创建）；不引入 WireMock/Hoverfly 等外部进程；不模拟登录、工作区、权限体系（这些走本地真实后端）。

---

## 1. 链路与接口全景

```
前端 mateclaw-dataagent-ui
  │  axios  baseURL=''  路径自带 /dataagent/api 前缀
  │  Header: Authorization: Bearer <token> / X-Workspace-Id: <wsId>
  ▼
后端 mateclaw-dataagent   context-path = /dataagent/api
  ├─ DataAgentDatasourceController   /v1/datasources/**
  ├─ DatasetComposerController       /v1/dataset-composer/**
  ▼
  ├─ AloudataAnalysisViewServiceImpl   ← 目录 / 列表 / 字段 / 详情
  ├─ AloudataAnalysisViewAdapter       ← 草稿预览取数、数据集字段描述
  ▼
  └─ AloudataApiClient.callWithParams(endpointName, config, params)
       ▼  ← ★ Mock 切点：覆写这里，按 endpointName 返回内置报文
     真实 Aloudata（anymetrics :8083 / semantic :8085）
```

### 1.1 前端动作 → dataagent 接口 → 上游端点

| # | 前端动作（组件） | dataagent 接口 | 上游端点 |
|---|---|---|---|
| 1 | 打开来源弹窗，加载指标视图目录（`DatasetSourceDialog`） | `GET /v1/datasources/{id}/analysis-views` | `analysis_view_tree` |
| 2 | 卡片属性侧「指标视图」下拉 +「只看我的」（`AloudataDialog`） | `GET /v1/datasources/{id}/analysis-views/list` | `analysis_view_list` |
| 3 | ★「字段名称」弹窗默认值（`FieldMappingDialog` ← `useInsight.refreshDatasetSchema`） | `GET /v1/datasources/{id}/analysis-views/{viewName}/fields` | `queryByName` + `batchDetail` + `dimension/list` |
| 4 | 字段兜底：视图展示名映射 | `GET /v1/datasources/{id}/analysis-views/{viewName}` | `analysis_view_query_by_name` |
| 5 | 来源弹窗点「预览」 | `POST /v1/dataset-composer/drafts/preview` | `analysis_view_query_data`（无筛选）/ `metrics_query`（有筛选） |
| 6 | 来源弹窗点「确定」 | `POST /v1/dataset-composer/drafts/confirm` | —（落库，无上游调用） |

### 1.2 运行前提

| 项 | 值 |
|---|---|
| context-path | `/dataagent/api`（`application.yml:4`） |
| 默认端口 | 18089（本地可能被占用而落到其他端口，以启动日志为准） |
| 激活 profile | `pgsql`（默认）+ `local-mock`（新增） |
| 数据源类型判定 | `source_type` 小写后需包含 `aloudata` 或 `anymetrics`（`AloudataAnalysisViewServiceImpl#requireAloudataDatasource`） |
| 工作区/归属校验 | 数据源需 `workspace_id` 匹配当前 `X-Workspace-Id`，且 `meta_shared=true` 或 `owner_id` 匹配当前用户 |

---

## 2. 接口清单（真实契约）

> 所有 dataagent 接口统一返回 `vip.mate.common.result.R<T>`：
> ```json
> { "code": 200, "msg": "<i18n result.success>", "data": <T> }
> ```
> `code=200` 为 `ResultCode.SUCCESS`；`msg` 由 i18n 决定，前端不依赖其字面值，只判 `code`。

### 2.1 指标视图目录树

```http
GET /dataagent/api/v1/datasources/{datasourceId}/analysis-views
```

- 权限：`@RequireWorkspaceRole(WORKSPACE_ROLE_VIEWER)`
- 请求：路径参数 `datasourceId`（Long）
- 上游：`analysis_view_tree` → `GET {anymetricsHost}:{port}/anymetrics/api/v1/analysisview/treeList`
- 响应 `data`：`AloudataAnalysisViewSummary[]`

```java
public record AloudataAnalysisViewSummary(
        String id,
        String viewName,
        String displayName,
        String categoryId,
        String categoryName) {}
```

```typescript
export interface AloudataAnalysisViewSummary {
  id: string
  viewName: string
  displayName: string
  categoryId?: string
  categoryName?: string
}
```

> **树结构约定**：上游响应的树根列表位于 `data.analysisViewRoots`（线上探测：16 个根节点 / 166 个视图条目）。每个节点含 `categoryId` / `categoryName` / `analysisViewList` / `subCategory`。`flattenTree` 会同时兼容 `analysisViewRoots`、`subCategory`、`analysisViewList`，并对 `data` 下的对象做兜底下钻 —— **但 mock 必须用 `analysisViewRoots`，否则兜底分支不会产出视图节点**（现有 `dev-support` 里的 WireMock 夹具正是因为缺这个键而取不到数据）。

### 2.2 指标视图平铺列表

```http
GET /dataagent/api/v1/datasources/{datasourceId}/analysis-views/list?keyword=&onlyMine=false
```

- Query：`keyword?`（空则后端改写为单字符通配 `_`）、`onlyMine?`（默认 `false`；前端 `AloudataDialog` 传 `true`）
- 上游：`analysis_view_list` → `GET /anymetrics/api/v1/analysisview/list?keyword=&pageNumber=1&pageSize=200`
- 响应 `data`：`AloudataAnalysisViewItem[]`

```java
public record AloudataAnalysisViewItem(
        String id,
        String viewName,
        String displayName,
        String description,
        String owner,   // Aloudata UID
        boolean mine) {}
```

**`mine` 的判定**：`datasource.username/password` 解析出的 `auth-value` 与 `basicAttributes.owner` 字符串相等 → `true`。

> ⚠️ 前端 `AloudataDialog` 默认 `onlyMine=true`。mock 若不返回 `basicAttributes.owner`，或 owner 与数据源 `auth-value` 不一致，**下拉列表恒为空**。这是最容易踩的坑。

### 2.3 ★ 字段名称（字段默认值）

```http
GET /dataagent/api/v1/datasources/{datasourceId}/analysis-views/{viewName}/fields
```

- 上游：三段聚合调用
  1. `analysis_view_query_by_name?viewName=` → 取 `data.metrics[]`、`data.dimensions[]` 的**名称**
  2. `metric_batch_detail?metricNames=a&metricNames=b` → 取 `metricDisplayName`、`businessCaliber`
  3. `dimension_list`（POST BODY，`pager.pageSize=1000`）→ 取 `dimDisplayName`、`dimDescription`
- 响应 `data`：`AloudataAnalysisViewField[]`，**顺序恒为「先指标后维度」**

```java
public record AloudataAnalysisViewField(
        String name,          // 指标 metricName / 维度 dimName
        String displayName,
        String description,   // 指标取 businessCaliber；维度取 dimDescription
        String role) {        // "measure" | "dimension"
    public static final String ROLE_MEASURE = "measure";
    public static final String ROLE_DIMENSION = "dimension";
}
```

```typescript
export interface AloudataAnalysisViewField {
  name: string
  displayName?: string
  description?: string
  role?: string
}
```

### 2.4 指标视图详情

```http
GET /dataagent/api/v1/datasources/{datasourceId}/analysis-views/{viewName}
```

- 上游：`analysis_view_query_by_name`
- 响应 `data`：`AloudataAnalysisViewDetail`（metrics/dimensions 为 `List<Map>`，即**对象数组**）

```java
public record AloudataAnalysisViewDetail(
        String id, String viewName, String displayName, String description,
        List<Map<String, Object>> metrics,        // [{name, displayName, dataType}, ...]
        List<Map<String, Object>> dimensions,
        String timeConstraint,
        List<Map<String, Object>> filters,
        List<Map<String, Object>> resultFilters,
        List<Map<String, Object>> orders) {}
```

> 前端 `getAnalysisView` 无强类型；仅 `viewDisplayNameMap` 用来兜底取展示名。

### 2.5 草稿预览

```http
POST /dataagent/api/v1/dataset-composer/drafts/preview
Content-Type: application/json

{ "sourceType": "ALOUDATA_ANALYSIS_VIEW",
  "datasourceId": "9001",
  "sourceConfig": { "analysisViewId": "cljd_zcl_zb_view" },
  "filters": [],
  "limit": 20 }
```

- 响应 `data`：`{ rows, schema, rowCount, pushdownReport, executionId }`
  - `schema` 为 `string[]`（取首行 keySet）
  - `rows` 为 `Record<string, unknown>[]`
- 后端对 `analysisViewId` 的处理：**直接当 `viewName` 使用**（见 §8-P0-2）

### 2.6 草稿确认

```http
POST /dataagent/api/v1/dataset-composer/drafts/confirm

{ "sourceType": "ALOUDATA_ANALYSIS_VIEW", "datasourceId": "9001",
  "sourceConfig": { "analysisViewId": "cljd_zcl_zb_view" },
  "name": "策略解读-子策略-指标", "description": "" }
```

- 响应 `data`：`{ datasetId: string, descriptor: DatasetInputDescriptor }`
- `DatasetInputDescriptor.schema[i]` 为 `DatasetColumn(name, title, dataType, nullable, semanticRole)`，**维度在前、指标在后**（与 `/fields` 相反，注意别混）

---

## 3. 「字段名称需要填默认值」的口径

### 3.1 数据来源

`useInsight.ts` 在 `ds.aloudata.mode === 'metric-view'` 时，**优先**调 `datasourceApi.listAnalysisViewFields(datasourceId, metricView)`；只有返回空数组时才降级为「`previewDraft` 取列名 + `getAnalysisView` 取展示名」。

因此 §2.3 的接口 mock 通，即等于字段默认值可用。

### 3.2 三列取值规则

`FieldMappingDialog.vue` 渲染三列，其中只有「目标名称」可编辑：

| 列 | 取值 | 来源 |
|---|---|---|
| 字段名 | `field.name` | `/fields` 的 `name`（只读） |
| 字段描述 | `field.description`，缺失时回退 `displayName` | `/fields` 的 `description`（只读） |
| 目标名称 | `defaultTargetOf(field)` = `displayName \|\| name` | `/fields` 的 `displayName`（可编辑） |

```typescript
// src/utils/field-mapping.ts
export function defaultTargetOf(field: DatasetSchemaField): string {
  const display = (field.displayName ?? '').trim()
  return display || field.name
}
```

### 3.3 二次进入弹窗的「是否被用户改过」判定

`buildFieldMappingRows(schema, existing)` 中：

```
prevDefault = 上一轮 desc || source           // 上一轮的默认目标名
userEdited  = prevTarget && prevTarget !== prevDefault
target      = userEdited ? prevTarget : defaultTargetOf(field)
```

含义：用户改过的目标名会被保留；没改过的会跟随新的 `displayName` 刷新。**推论**：mock 的 `displayName` 一旦定稿就不要再随意改，否则老配置卡片的目标名会被判定为「未改过」而被覆盖；反之若希望刷新，改 mock 的 `displayName` 即可触发。

---

## 4. Mock 域模型设计（源：`generated_cljd_zcl.sql`）

### 4.1 源脚本语义

| 表 | 中文名 | 主键（粒度） | 内容 |
|---|---|---|---|
| `cljd_zcl_zb` | 策略解读-子策略-指标 | `metric_time, attribution_plan_id, attribution_strategy_id, platform_id, channel` | 5 个维度 + **13 个 `digo_*` 指标**（宽表） |
| `cljd_zcl_wd` | 策略解读-子策略-维度 | 上述 + `metric_id` | **13 个维度**（含计划/策略/子策略展示名）+ **6 个 `digo_*` 指标** |

关联规则（脚本头注释）：`cljd_zcl_wd.metric_id` ↔ `cljd_zcl_zb` 中字段名 `digo_<metric_id>_trans_user_cnt` 的中间部分。

**行数**：`cljd_zcl_zb` 6 行；`cljd_zcl_wd` = 6 行 × 3 个 `metric_id` = **18 行**。

### 4.2 Mock 视图规划

在 Aloudata 目录「策略解读」下建 **2 个指标视图**，与两张 PG 表一一对应：

| viewName | displayName | 结构 | 行数 |
|---|---|---|---|
| `cljd_zcl_zb_view` | 策略解读-子策略-指标 | 5 维度 + 13 指标 | 6 |
| `cljd_zcl_wd_view` | 策略解读-子策略-维度 | 13 维度 + 6 指标 | 18 |

> 约定 **`id`（int：9001/9002）≠ `viewName`**，与真实环境一致（实测 `id=824` / `viewName=cs202609161430`）。
> 活跃链路 `AloudataDialog` 取 `viewName`，不受影响；唯一会传 id 的是 `DatasetSourceDialog`，而它只被死代码引用（§8-P0-2）。

### 4.3 维度清单（共 13 个）

| dimName | dimDisplayName | dimDescription | originDataType | 备注 |
|---|---|---|---|---|
| metric_time | 指标日期 | 指标日期 | DATE | 两个视图都有 |
| attribution_plan_id | 计划id | 计划id | STRING | |
| attribution_plan_name | 计划名称 | 计划名称 | STRING | 仅 wd |
| attribution_plan_um_account | 计划负责人 | 计划负责人 | STRING | 仅 wd |
| attribution_strategy_id | 策略id | 策略id | STRING | |
| attribution_strategy_name | 策略名称 | 策略名称 | STRING | 仅 wd |
| attribution_over_by | 策略负责人 | 策略负责人 | STRING | 仅 wd |
| platform_id | 子策略id | 子策略id | STRING | |
| platform_name | 子策略名称 | 子策略名称 | STRING | 仅 wd |
| create_by | 子策略创建人 | 子策略创建人 | STRING | 仅 wd |
| channel | 触达渠道 | 触达渠道 | STRING | |
| metric_id | 转化指标id | 转化指标id；对应指标表中 `digo_<metric_id>_trans_user_cnt` 的中间部分 | STRING | 仅 wd |
| metric_name | 转化指标名称 | 转化指标名称 | STRING | 仅 wd |

### 4.4 指标清单（共 19 个）

**zb 视图的 13 个指标**（`metricName` = PG 列名，保留 `digo_` 前缀）

| metricName | metricDisplayName | businessCaliber（描述） | dataType |
|---|---|---|---|
| digo_trd_fund_amt_inout_cy_jjgr | 经纪个人客户场内公募非货当年净买入 | 经纪个人客户场内公募非货当年净买入-策略归因 | DECIMAL |
| digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt | 经纪个人客户场内公募非货当年净买入-转化客户数 | 经纪个人客户场内公募非货当年净买入-转化客户数-策略归因 | BIGINT |
| digo_trd_fund_amt_inout_cy_jjgr_pb | 经纪个人场内公募非货破冰客户当年净买入 | 经纪个人场内公募非货破冰客户当年净买入-策略归因 | DECIMAL |
| digo_pbcnt_kgdb_a566_fh_jjgr | 经纪个人场内公募非货破冰客户数 | 经纪个人场内公募非货破冰客户数-策略归因 | BIGINT |
| digo_fund_trd_amt_a566_fh_kgdb_jj0 | 经纪个人场内公募非货交易量 | 经纪个人场内公募非货交易量-策略归因 | DECIMAL |
| digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt | 经纪个人场内公募非货交易量-转化客户数 | 经纪个人场内公募非货交易量-转化客户数-策略归因 | BIGINT |
| digo_pub_fh_kgdb_trdamt_ppcadd_jjgr | 经纪个人场内公募非货加仓交易量 | 经纪个人场内公募非货加仓交易量-策略归因 | DECIMAL |
| digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt | 经纪个人场内公募非货加仓交易量-转化客户数 | 经纪个人场内公募非货加仓交易量-转化客户数-策略归因 | BIGINT |
| digo_cust_asset_in | 入金客户数 | 入金客户数_策略归因 | BIGINT |
| digo_new_cust_asset_in | 新客入金数 | 新客入金数_策略归因 | BIGINT |
| digo_cnt_cust_code_new_brok | 新增客户数 | 新增客户数_策略归因 | BIGINT |
| digo_cust_valid_new_yxh | 新增有效户数 | 新增有效户数_策略归因 | BIGINT |
| digo_cust_asset_in10000 | 万元入金户数 | 万元入金户数_策略归因 | BIGINT |

**wd 视图的 6 个指标**

| metricName | metricDisplayName | businessCaliber | dataType |
|---|---|---|---|
| digo_strategy_cnt | 下挂策略数 | 下挂策略数 | BIGINT |
| digo_strategy_cnt_distr_1 | 下发策略数 | 下发策略数 | BIGINT |
| digo_distr_count_1 | 下发次数 | 下发次数 | BIGINT |
| digo_distr_user_cnt_a | 下发人数 | 下发人数 | BIGINT |
| digo_touch_cnt_1 | 触达人数 | 触达人数 | BIGINT |
| digo_touch_user_cnt_1 | 触达次数 | 触达次数 | BIGINT |

### 4.5 数据行

**`cljd_zcl_zb_view`（6 行，取自脚本 INSERT 原文）**

| metric_time | attribution_plan_id | attribution_strategy_id | platform_id | channel | 13 指标 |
|---|---|---|---|---|---|
| 2026-09-01 | PLAN-001 | STR-001 | SUB-001 | APP | 1250000.00, 128, 420000.00, 96, 860000.00, 74, 210000.00, 39, 186, 72, 154, 91, 43 |
| 2026-09-01 | PLAN-001 | STR-001 | SUB-001 | SMS | 980000.00, 101, 315000.00, 81, 640000.00, 57, 175000.00, 31, 143, 55, 119, 68, 32 |
| 2026-09-01 | PLAN-001 | STR-002 | SUB-002 | APP | 1480000.00, 152, 510000.00, 112, 930000.00, 88, 265000.00, 46, 211, 84, 179, 108, 51 |
| 2026-09-02 | PLAN-001 | STR-001 | SUB-001 | APP | 1320000.00, 136, 450000.00, 103, 905000.00, 79, 230000.00, 42, 194, 78, 163, 97, 46 |
| 2026-09-02 | PLAN-001 | STR-001 | SUB-001 | SMS | 1040000.00, 109, 338000.00, 87, 690000.00, 62, 188000.00, 34, 151, 60, 126, 73, 35 |
| 2026-09-02 | PLAN-001 | STR-002 | SUB-002 | APP | 1550000.00, 161, 536000.00, 121, 982000.00, 94, 279000.00, 49, 223, 90, 190, 115, 55 |

**`cljd_zcl_wd_view`（18 行 = 6 组 × 3 个 `metric_id`）**

派生规则（等价于脚本的 `CASE`）：

- `attribution_plan_name` = `策略解读示例计划`；`attribution_plan_um_account` = `plan_owner`；`create_by` = `data_owner`
- `STR-001` → `attribution_strategy_name`=`高净值客户触达策略`，`attribution_over_by`=`strategy_owner_a`，`digo_strategy_cnt`=`digo_strategy_cnt_distr_1`=2
- `STR-002` → `attribution_strategy_name`=`新客增长策略`，`attribution_over_by`=`strategy_owner_b`，两者=3
- `SUB-001` → `platform_name`=`场内公募非货子策略`；`SUB-002` → `platform_name`=`新客入金子策略`
- `APP` → 下发次数=180、下发人数=150、触达人数=120、触达次数=96
- `SMS` → 下发次数=130、下发人数=105、触达人数=88、触达次数=71

| 组 | metric_time | strategy | platform | channel | 6 指标 | × metric_id |
|---|---|---|---|---|---|---|
| G1 | 2026-09-01 | STR-001 | SUB-001 | APP | 2, 2, 180, 150, 120, 96 | t1/t2/t3 |
| G2 | 2026-09-01 | STR-001 | SUB-001 | SMS | 2, 2, 130, 105, 88, 71 | t1/t2/t3 |
| G3 | 2026-09-01 | STR-002 | SUB-002 | APP | 3, 3, 180, 150, 120, 96 | t1/t2/t3 |
| G4 | 2026-09-02 | STR-001 | SUB-001 | APP | 2, 2, 180, 150, 120, 96 | t1/t2/t3 |
| G5 | 2026-09-02 | STR-001 | SUB-001 | SMS | 2, 2, 130, 105, 88, 71 | t1/t2/t3 |
| G6 | 2026-09-02 | STR-002 | SUB-002 | APP | 3, 3, 180, 150, 120, 96 | t1/t2/t3 |

`metric_id` / `metric_name` 三元组：

| 代号 | metric_id | metric_name |
|---|---|---|
| t1 | trd_fund_amt_inout_cy_jjgr | 经纪个人客户场内公募非货当年净买入 |
| t2 | fund_trd_amt_a566_fh_kgdb_jj0 | 经纪个人场内公募非货交易量 |
| t3 | pub_fh_kgdb_trdamt_ppcadd_jjgr | 经纪个人场内公募非货加仓交易量 |

> 完整列式 JSON 见 §6.6。

---

## 5. 落地方案（后端本地 mock）

### 5.1 切面候选对比

| 方案 | 做法 | 覆盖度 | mock 报文格式 | 是否锻炼真实逻辑 | 结论 |
|---|---|---|---|---|---|
| A. 覆写 `AloudataApiClient.callWithParams` | `@Profile("local-mock") @Primary` 子类，按 `endpointName` 返回内置报文 | 全 6 端点一处收口 | **真实上游格式** | ✅ `flattenTree`/`listFields` 聚合/`rows` 容错全跑 | ★ **推荐** |
| B. 替换 `AloudataAnalysisViewService` 实现 | mock 直接返回 DTO | 需同时替换 Adapter + `AloudataService`，3 个类 | dataagent DTO 格式（非上游格式） | ❌ 绕过聚合逻辑 | 备选 |
| C. WireMock 模拟上游 | 数据源 host 指向 18081 | 需补 mapping，且缺 `analysisview/list` | 真实上游格式 | ✅ | 本次不采用（要起外部进程） |

采用 **方案 A**。理由：mock 报文就是真实 Aloudata 报文，能同时验证 `flattenTree` 的树解析、`listFields` 的三段聚合、`rows()` 的行式/列式容错；且新增视图只需加一份 JSON。

### 5.2 文件布局

```
mateclaw-dataagent/src/main/java/vip/mate/dataagent/aloudata/local/
├── LocalAloudataApiClient.java          # @Profile("local-mock") @Primary extends AloudataApiClient
└── LocalAloudataFixtures.java           # 按 endpointName 读取 classpath fixture 并做事后处理

mateclaw-dataagent/src/main/resources/mock/aloudata/
├── analysis_view_tree.json
├── analysis_view_list.json
├── analysis_view_query_by_name.json     # 两个视图的详情，按 viewName 索引
├── metric_batch_detail.json
├── dimension_list.json
├── analysis_view_query_data.json        # 按 viewName 索引结果集
└── metrics_query.json
```

### 5.3 代码骨架（已落地）

实际实现见 `aloudata/local/LocalAloudataApiClient.java` 与 `aloudata/local/LocalAloudataFixtures.java`，要点：

```java
@Primary
@Component
@Profile("local-mock")
public class LocalAloudataApiClient extends AloudataApiClient {

    private final LocalAloudataFixtures fixtures;

    public LocalAloudataApiClient(AloudataEndpointService endpointService,
                                 LocalAloudataFixtures fixtures) {
        super(endpointService);
        this.fixtures = fixtures;
        log.warn("★ local-mock 已启用：...");
    }

    @Override
    public ResponseEntity<Map> callWithParams(String endpointName, AloudataConfigDTO config,
                                              Map<String, Object> params) {
        return ResponseEntity.ok(fixtures.payload(endpointName, params, config.getAuthValue()));
    }
```

    /** 少数调用方走不带参数规范的 call(...)，同样短路，避免漏网打真上游。 */
    @Override
    public ResponseEntity<Map> call(String endpointName, AloudataConfigDTO config,
                                   Map<String, String> pathVariables, Object requestBody) {
        return ResponseEntity.ok(fixtures.payload(endpointName, Map.of()));
    }
}
```

`LocalAloudataFixtures#payload` 的职责：

1. 按 `endpointName` 选 fixture 文件；
2. 按 `params` 做 **参数感知过滤**：
   - `analysis_view_query_by_name` / `analysis_view_query_data`：按 `viewName` 选视图；未知 viewName → 返回 `{"code":"SM_02_0038","success":false,"message":"用户&资源没有权限"}`
   - `metric_batch_detail`：按 `metricNames` 过滤（`params` 里是重复 query 参数展开后的 `List`，或单值 `String`，两种都要兼容）
   - `analysis_view_list`：按 `keyword` 做包含匹配（`_` 视为全量）；`onlyMine` 不进 params，仅由 dataagent 侧过滤，mock 只管返回带 `basicAttributes.owner` 的全量
   - `analysis_view_query_data` / `metrics_query`：按 `pageSize` / `pageIndex` 做真分页，`rows` 超限时截断并置 `last=false`
3. 返回 `LinkedHashMap`（保序），键结构与真实报文一致。

> 实现时务必返回 **可变** `Map`（`LinkedHashMap`），不要把 fixture 直接反序列化成 `Map.of(...)`，避免下游 `putAll` 抛 `UnsupportedOperationException`。

### 5.4 开关与启动

**方式一（推荐）：用日常启动脚本，mock 默认开** —— `docs/策略解读/restart-dataagent-backend.sh` 已支持 `ALOUDATA_MOCK` 开关：

```bash
./docs/策略解读/restart-dataagent-backend.sh                      # 默认 pgsql,local-mock，上游全走 mock
ALOUDATA_MOCK=off ./docs/策略解读/restart-dataagent-backend.sh     # 切回真实 Aloudata 上游
```

**方式二：手动指定 profile**

```bash
# 数据源连接信息 + mock profile
export DB_HOST=... DB_PORT=... DB_NAME=... DB_USERNAME=... DB_PASSWORD=...
export SPRING_PROFILES_ACTIVE=pgsql,local-mock
export AI_DASHSCOPE_API_KEY=sk-placeholder

java -jar mateclaw-dataagent/target/mateclaw-dataagent-1.0.0-SNAPSHOT.jar
```

启动日志应出现醒目 WARN 与夹具装载日志，用于确认 profile 与资源都生效：

```
WARN  v.m.d.aloudata.local.LocalAloudataApiClient - ★ local-mock 已启用：Aloudata 上游调用全部返回内置 mock 报文，不会发起任何真实 HTTP 请求
INFO  v.m.d.aloudata.local.LocalAloudataFixtures  - [local-mock] 已装载 Aloudata 夹具 mock/aloudata/analysis_view_tree.json
```

每次调用还会打一行 `[local-mock] <endpointName> params=[...]`，便于观察走了哪条分支。

前端：

```bash
cd mateclaw-dataagent-ui && VITE_DATAAGENT_PROXY_TARGET=http://localhost:<实际端口> pnpm dev
```

**重新生成夹具**（改了 `generated_cljd_zcl.sql` 或加视图时）：

```bash
python3 dev-support/local-simulation/scripts/generate-aloudata-fixtures.py
```

### 5.5 前置数据准备

**不需要新造数据源。** `owner` 由夹具装载时按当前数据源的认证值填充（§6.3 的 `__OWNER__` 机制），
所以**任何已有的 Aloudata 数据源都能直接用**，只要：

1. `dataagent_datasource.source_type` 小写后含 `aloudata` / `anymetrics`；
2. 该数据源对当前工作区可见（`meta_shared=true` 或 `owner_id` = 当前用户）；
3. 前端 `DatasetSourcePicker` 能在 `/v1/datasources` 里找到它。

> 若确实想造一条专用 mock 数据源，注意 `host` / `port` / `connection_params` 在 local-mock 下**不参与网络请求**
> （`callWithParams` 已被短路），保留只为让 `parseConfig` 走通；真正有语义的只有
> `source_type`、`username`（tenantId）、`password`（auth-value）。

真实环境对照（仅本地探测用，mock 下不生效）：

| 变量 | 含义 |
|---|---|
| `ALOU_DATA_PRODUCT_BASE_URL` | anymetrics 基址 |
| `ALOU_DATA_SEMANTIC_BASE_URL` | semantic 基址 |
| `ALOU_DATA_TENANT_ID` | tenant-id |
| `ALOU_DATA_AUTH_VALUE` | auth-value（= 数据源 `password` 明文 = mock 的 owner） |

---

## 6. Mock 报文（逐端点）

报文结构以**真实 Aloudata 实测响应**为准。夹具文件位于
`mateclaw-dataagent/src/main/resources/mock/aloudata/`，由
`dev-support/local-simulation/scripts/generate-aloudata-fixtures.py` 从 `generated_cljd_zcl.sql` 生成；
本节各端点的写法与 §6.0 的实测形状一一对应。

### 6.0 实测真实报文（权威形状）

**统一包络**——键名是 `errorMsg` / `detailErrorMsg`（**不是** `message`），且 `code` 是**字符串** `"200"`：

```json
{ "data": { }, "success": true, "code": "200", "errorMsg": null, "detailErrorMsg": null, "traceId": "..." }
```

**`treeList`**：`data.analysisViewRoots[] = {categoryId, categoryName, analysisViewList[], subCategory[]}`。
视图条目只有 4 个键，且 `id` 是 **int**（不是视图名）：

```json
{ "id": 654, "viewName": "NewView_lily_1", "description": "", "displayName": "lily测试指标视图_1" }
```

**`queryByName`**：`metrics` / `dimensions` 是**字符串数组**，展示名单独放在 `displayNameMap`：

```json
{
  "data": {
    "id": 11,
    "guid": { "catalog": "tn_27436_aloudata_datasource", "schema": "default", "table": "Demo_view",
              "validTableGuid": true, "validSchemaGuid": true, "validCatalogGuid": true },
    "viewName": "Demo_view",
    "displayName": "Demo_view",
    "description": "",
    "categoryId": null,
    "metrics": ["AUM", "RJCYKSL1", "SNMCKYE1", "ytd_avg_aum", "thisyear_tob_aum"],
    "dimensions": ["metric_time__day", "Demo_finance_productinfo_product_name_lv1"],
    "timeConstraint": "(((dateTrunc(['metric_time'], \"DAY\")) >= (DATEADD(DateTrunc(TODAY(), \"DAY\"), -(364), \"DAY\")))) AND ...",
    "filters": [], "resultFilters": [], "orders": [],
    "createFrom": "WEBUI",
    "displayNameMap": { "RJCYKSL1": "人均持有卡数量", "AUM": "AUM", "SNMCKYE1": "上年末存款余额" },
    "basicAttributes": { "owner": "393451174725943296", "modifyTime": 1714109245360,
                         "viewName": "Demo_view", "createTime": 1703419638688, "viewDisplayName": "Demo_view" }
  },
  "success": true, "code": "200", "errorMsg": null, "detailErrorMsg": null, "traceId": "..."
}
```

**`analysisview/list`**：`data = {pageNumber, pageSize, totalPageSize, data[]}`，条目是**完整视图定义**
（含 `metrics` / `dimensions` / `displayNameMap` / `basicAttributes.owner`），不是精简摘要。

**`metrics/batchDetail`**：`data[]`，展示名与口径字段是 `metricDisplayName` / `businessCaliber`。

**`dimension/list`**：`data = {total, pageNumber, pageSize, hasNext, data[]}`；字段名 `dimName`、
展示名 `dimDisplayName`、描述 `dimDescription`（**实测可能为 null**）、类型 `originDataType`。
注意条目里还有一个 `name`（形如 `dm7de2b44d79e7161ea948a209cd4a2c` 的内部标识），**不要**当成字段名。

**`analysisView/query`**：列式 `data.table.columns = {列名: [{value, flag, count}]}`，并带 `data.metas[]`、`data.queryId`、`data.warning`。
（本仓早期一版夹具曾误写成 `data.analysisView.columns`，已于本轮修正为与官方文档一致的 `data.table.columns`。）

### 6.1 `analysis_view_tree`

`GET /anymetrics/api/v1/analysisview/treeList`

夹具：`analysis_view_tree.json`

```json
{
  "data": {
    "analysisViewRoots": [
      {
        "categoryId": "CAT_CLJD",
        "categoryName": "策略解读",
        "analysisViewList": [
          { "id": 9001, "viewName": "cljd_zcl_zb_view", "description": "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）", "displayName": "策略解读-子策略-指标" },
          { "id": 9002, "viewName": "cljd_zcl_wd_view", "description": "策略解读：子策略维度的下发/触达指标", "displayName": "策略解读-子策略-维度" }
        ],
        "subCategory": []
      }
    ]
  },
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-tree"
}
```

**期望产出**（前端 `DatasetSourceDialog` 下拉的 `data`，`id` 会被 `String.valueOf` 成字符串）：

```json
[
  { "id": "9001", "viewName": "cljd_zcl_zb_view", "displayName": "策略解读-子策略-指标", "categoryId": "CAT_CLJD", "categoryName": "策略解读" },
  { "id": "9002", "viewName": "cljd_zcl_wd_view", "displayName": "策略解读-子策略-维度", "categoryId": "CAT_CLJD", "categoryName": "策略解读" }
]
```

> ⚠️ 这里刻意让镜像 `id`（9001/9002）**不等于** `viewName`，与真实环境一致，用来暴露 §8-P0-2
> 「`DatasetSourceDialog` 用 `view.id \|\| view.viewName` 当 `analysisViewId`」的坑。
> 走 `DatasetSourceDialog` 时视图名应取 `viewName`；若沿用 `id`，`preview` 会查不到视图。

### 6.2 `analysis_view_list`

`GET /anymetrics/api/v1/analysisview/list?keyword=%E7%AD%96%E7%95%A5&pageNumber=1&pageSize=200`

```json
{
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-list-0001",
  "data": {
    "total": 2,
    "pageNumber": 1,
    "pageSize": 200,
    "data": [
      {
        "id": "cljd_zcl_zb_view",
        "viewName": "cljd_zcl_zb_view",
        "displayName": "策略解读-子策略-指标",
        "description": "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）",
        "basicAttributes": {
          "owner": "__OWNER__",
          "viewDisplayName": "策略解读-子策略-指标",
          "createTime": "2026-09-18 10:00:00",
          "updateTime": "2026-09-18 10:00:00"
        }
      },
      {
        "id": "cljd_zcl_wd_view",
        "viewName": "cljd_zcl_wd_view",
        "displayName": "策略解读-子策略-维度",
        "description": "策略解读：子策略维度的下发/触达指标",
        "basicAttributes": {
          "owner": "__OWNER__",
          "viewDisplayName": "策略解读-子策略-维度",
          "createTime": "2026-09-18 10:00:00",
          "updateTime": "2026-09-18 10:00:00"
        }
      }
    ]
  }
}
```

> `owner` 是占位符，装载时替换为**当前数据源的认证值**，因此 `onlyMine=true` 在本地恒命中（P0-3 已消除）。
> 期望产出 `mine=true`（两条）。

### 6.3 `analysis_view_query_by_name`

`GET /anymetrics/api/v1/analysisview/queryByName?viewName=cljd_zcl_zb_view`

夹具：`analysis_view_query_by_name.json`——以 `viewName` 为键的容器，值是完整真实格式响应。

```json
{
  "cljd_zcl_zb_view": {
    "data": {
      "id": 9001,
      "guid": { "catalog": "mock_tenant_aloudata_datasource", "schema": "default", "table": "cljd_zcl_zb",
                "validTableGuid": true, "validSchemaGuid": true, "validCatalogGuid": true },
      "viewName": "cljd_zcl_zb_view",
      "displayName": "策略解读-子策略-指标",
      "description": "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）",
      "categoryId": "CAT_CLJD",
      "metrics": [
        "digo_trd_fund_amt_inout_cy_jjgr",
        "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt",
        "digo_trd_fund_amt_inout_cy_jjgr_pb",
        "digo_pbcnt_kgdb_a566_fh_jjgr",
        "digo_fund_trd_amt_a566_fh_kgdb_jj0",
        "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt",
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr",
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt",
        "digo_cust_asset_in",
        "digo_new_cust_asset_in",
        "digo_cnt_cust_code_new_brok",
        "digo_cust_valid_new_yxh",
        "digo_cust_asset_in10000"
      ],
      "dimensions": ["metric_time", "attribution_plan_id", "attribution_strategy_id", "platform_id", "channel"],
      "timeConstraint": "(((dateTrunc(['metric_time'], \"DAY\")) >= (DATEADD(DateTrunc(TODAY(), \"DAY\"), -(364), \"DAY\")))) AND (((dateTrunc(['metric_time'], \"DAY\")) <= (DATEADD(DateTrunc(TODAY(), \"DAY\"), 0, \"DAY\"))))",
      "filters": [],
      "resultFilters": [],
      "orders": [],
      "createFrom": "WEBUI",
      "displayNameMap": {
        "digo_trd_fund_amt_inout_cy_jjgr": "经纪个人客户场内公募非货当年净买入",
        "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt": "经纪个人客户场内公募非货当年净买入-转化客户数",
        "digo_trd_fund_amt_inout_cy_jjgr_pb": "经纪个人场内公募非货破冰客户当年净买入",
        "digo_pbcnt_kgdb_a566_fh_jjgr": "经纪个人场内公募非货破冰客户数",
        "digo_fund_trd_amt_a566_fh_kgdb_jj0": "经纪个人场内公募非货交易量",
        "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt": "经纪个人场内公募非货交易量-转化客户数",
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr": "经纪个人场内公募非货加仓交易量",
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt": "经纪个人场内公募非货加仓交易量-转化客户数",
        "digo_cust_asset_in": "入金客户数",
        "digo_new_cust_asset_in": "新客入金数",
        "digo_cnt_cust_code_new_brok": "新增客户数",
        "digo_cust_valid_new_yxh": "新增有效户数",
        "digo_cust_asset_in10000": "万元入金户数",
        "metric_time": "指标日期",
        "attribution_plan_id": "计划id",
        "attribution_strategy_id": "策略id",
        "platform_id": "子策略id",
        "channel": "触达渠道"
      },
      "basicAttributes": { "owner": "__OWNER__", "viewName": "cljd_zcl_zb_view",
                           "viewDisplayName": "策略解读-子策略-指标",
                           "createTime": 1758100000000, "modifyTime": 1758100000000 },
      "businessAttributes": { "viewDescription": "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）", "viewCategoryId": "CAT_CLJD" },
      "technicalAttributes": {},
      "managementAttributes": {}
    },
    "success": true,
    "code": "200",
    "errorMsg": null,
    "detailErrorMsg": null,
    "traceId": "mock-trace-detail-cljd_zcl_zb_view"
  }
}
```

`cljd_zcl_wd_view` 同构，只是字段集合换成 §4.3 的 13 维度 + §4.4 的 6 指标。

> **格式要点**：`metrics` / `dimensions` 是**字符串数组**，展示名只在 `displayNameMap` 里——这是实测确认的真实形状（§6.0），
> 也是 `listFields` 的实现依据。后端 `getByName` 会把它规范化成对象数组（`{name, displayName}`）后再对外返回，
> 见 §8-P0-1。
>
> **`__OWNER__` 占位符**：夹具不写死 owner，装载时替换为当前数据源的认证值（`AloudataConfigDTO#getAuthValue()`），
> 因此「只看我的」在本地任何数据源下都等价于「全部 mock 视图」，无需为 mock 单独造数据源。

**未命中 viewName 时**（模拟真实的无权限），返回真实同构的 `SM_02_0038` 报文：

```json
{
  "data": null,
  "success": false,
  "code": "SM_02_0038",
  "errorMsg": "用户&资源没有权限",
  "detailErrorMsg": "用户&资源没有权限",
  "traceId": "mock-trace-detail-<viewName>"
}
```

dataagent 会把它映射为 403 `VIEW_ACCESS_DENIED`（`AloudataAnalysisViewServiceImpl#call`）。

### 6.4 `metric_batch_detail`

`GET /anymetrics/api/v1/metrics/batchDetail?metricNames=digo_cust_asset_in&metricNames=digo_new_cust_asset_in`

```json
{
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-batch-0001",
  "data": [
    { "metricName": "digo_trd_fund_amt_inout_cy_jjgr", "metricDisplayName": "经纪个人客户场内公募非货当年净买入", "businessCaliber": "经纪个人客户场内公募非货当年净买入-策略归因", "metricCategoryName": "策略归因", "dataType": "DECIMAL", "synonyms": [] },
    { "metricName": "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", "metricDisplayName": "经纪个人客户场内公募非货当年净买入-转化客户数", "businessCaliber": "经纪个人客户场内公募非货当年净买入-转化客户数-策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_trd_fund_amt_inout_cy_jjgr_pb", "metricDisplayName": "经纪个人场内公募非货破冰客户当年净买入", "businessCaliber": "经纪个人场内公募非货破冰客户当年净买入-策略归因", "metricCategoryName": "策略归因", "dataType": "DECIMAL", "synonyms": [] },
    { "metricName": "digo_pbcnt_kgdb_a566_fh_jjgr", "metricDisplayName": "经纪个人场内公募非货破冰客户数", "businessCaliber": "经纪个人场内公募非货破冰客户数-策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_fund_trd_amt_a566_fh_kgdb_jj0", "metricDisplayName": "经纪个人场内公募非货交易量", "businessCaliber": "经纪个人场内公募非货交易量-策略归因", "metricCategoryName": "策略归因", "dataType": "DECIMAL", "synonyms": [] },
    { "metricName": "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", "metricDisplayName": "经纪个人场内公募非货交易量-转化客户数", "businessCaliber": "经纪个人场内公募非货交易量-转化客户数-策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", "metricDisplayName": "经纪个人场内公募非货加仓交易量", "businessCaliber": "经纪个人场内公募非货加仓交易量-策略归因", "metricCategoryName": "策略归因", "dataType": "DECIMAL", "synonyms": [] },
    { "metricName": "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", "metricDisplayName": "经纪个人场内公募非货加仓交易量-转化客户数", "businessCaliber": "经纪个人场内公募非货加仓交易量-转化客户数-策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_cust_asset_in", "metricDisplayName": "入金客户数", "businessCaliber": "入金客户数_策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_new_cust_asset_in", "metricDisplayName": "新客入金数", "businessCaliber": "新客入金数_策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_cnt_cust_code_new_brok", "metricDisplayName": "新增客户数", "businessCaliber": "新增客户数_策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_cust_valid_new_yxh", "metricDisplayName": "新增有效户数", "businessCaliber": "新增有效户数_策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_cust_asset_in10000", "metricDisplayName": "万元入金户数", "businessCaliber": "万元入金户数_策略归因", "metricCategoryName": "策略归因", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_strategy_cnt", "metricDisplayName": "下挂策略数", "businessCaliber": "下挂策略数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_strategy_cnt_distr_1", "metricDisplayName": "下发策略数", "businessCaliber": "下发策略数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_distr_count_1", "metricDisplayName": "下发次数", "businessCaliber": "下发次数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_distr_user_cnt_a", "metricDisplayName": "下发人数", "businessCaliber": "下发人数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_touch_cnt_1", "metricDisplayName": "触达人数", "businessCaliber": "触达人数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] },
    { "metricName": "digo_touch_user_cnt_1", "metricDisplayName": "触达次数", "businessCaliber": "触达次数", "metricCategoryName": "策略解读", "dataType": "BIGINT", "synonyms": [] }
  ]
}
```

> 夹具里放**全量 19 个指标**，由 `LocalAloudataFixtures` 按请求的 `metricNames` 过滤返回，省得为每个视图各写一份。

### 6.5 `dimension_list`

`POST /anymetrics/api/v1/dimension/list`
Body：`{"pager":{"pageNumber":1,"pageSize":1000}}`

```json
{
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-dim-0001",
  "data": {
    "total": 13,
    "hasNext": false,
    "data": [
      { "dimName": "metric_time", "dimCode": "metric_time", "dimDisplayName": "指标日期", "dimDescription": "指标日期", "dimCategoryId": "dim_time", "originDataType": "DATE", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_plan_id", "dimCode": "attribution_plan_id", "dimDisplayName": "计划id", "dimDescription": "计划id", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_plan_name", "dimCode": "attribution_plan_name", "dimDisplayName": "计划名称", "dimDescription": "计划名称", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_plan_um_account", "dimCode": "attribution_plan_um_account", "dimDisplayName": "计划负责人", "dimDescription": "计划负责人", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_strategy_id", "dimCode": "attribution_strategy_id", "dimDisplayName": "策略id", "dimDescription": "策略id", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_strategy_name", "dimCode": "attribution_strategy_name", "dimDisplayName": "策略名称", "dimDescription": "策略名称", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "attribution_over_by", "dimCode": "attribution_over_by", "dimDisplayName": "策略负责人", "dimDescription": "策略负责人", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "platform_id", "dimCode": "platform_id", "dimDisplayName": "子策略id", "dimDescription": "子策略id", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "platform_name", "dimCode": "platform_name", "dimDisplayName": "子策略名称", "dimDescription": "子策略名称", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "create_by", "dimCode": "create_by", "dimDisplayName": "子策略创建人", "dimDescription": "子策略创建人", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "channel", "dimCode": "channel", "dimDisplayName": "触达渠道", "dimDescription": "触达渠道", "dimCategoryId": "dim_channel", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "metric_id", "dimCode": "metric_id", "dimDisplayName": "转化指标id", "dimDescription": "转化指标id；对应指标表中 digo_<metric_id>_trans_user_cnt 的中间部分", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" },
      { "dimName": "metric_name", "dimCode": "metric_name", "dimDisplayName": "转化指标名称", "dimDescription": "转化指标名称", "dimCategoryId": "dim_strategy", "originDataType": "STRING", "displayStatus": "PUBLISHED", "datasetName": "cljd_zcl" }
    ]
  }
}
```

> `dimDescription` 是「维度描述」列的数据源；缺失时会回退成 `dimDisplayName`。

### 6.6 `analysis_view_query_data`

`GET /semantic/api/v1.1/analysisView/query?viewName=cljd_zcl_zb_view&pageSize=20&pageIndex=0&queryResultType=DATA`

真实结构为**列式**：`data.table.columns = { 列名: [ {value, flag, count}, ... ] }`，并携带 `data.metas[]`、`data.queryId`、`data.warning`、`data.total`（顶层，等于行数）。

> **端口配置（已就绪，无需改代码）**：该接口在 `AloudataEndpointService.getDefaultEndpoints()` 中已注册为端点键 `analysis_view_query_data`（`AloudataEndpointService.java` 第 120 行；`semantic` 服务、GET、`/semantic/api/v1.1/analysisView/query`），本仓自带，无需新增配置。
>
> ⚠️ **不支持按维度值筛选**：`analysisView/query` 是**无筛选的全量结果查询**；带筛选条件时本仓改走 `metrics_query`（见 §6.7）。本仓早期一版夹具曾误写成 `data.analysisView.columns`，已于本轮修正为与官方「指标视图结果查询」一致的 `data.table.columns`。

```json
{
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-data-zb",
  "data": {
    "total": 6,
    "queryId": "mock-query-cljd_zcl_zb_view",
    "warning": null,
    "table": {
      "total": 6,
      "columns": {
        "metric_time": [{"value":"2026-09-01","flag":0,"count":1},{"value":"2026-09-01","flag":0,"count":1},{"value":"2026-09-01","flag":0,"count":1},{"value":"2026-09-02","flag":0,"count":1},{"value":"2026-09-02","flag":0,"count":1},{"value":"2026-09-02","flag":0,"count":1}],
        "attribution_plan_id": [{"value":"PLAN-001","flag":0,"count":1},{"value":"PLAN-001","flag":0,"count":1},{"value":"PLAN-001","flag":0,"count":1},{"value":"PLAN-001","flag":0,"count":1},{"value":"PLAN-001","flag":0,"count":1},{"value":"PLAN-001","flag":0,"count":1}],
        "attribution_strategy_id": [{"value":"STR-001","flag":0,"count":1},{"value":"STR-001","flag":0,"count":1},{"value":"STR-002","flag":0,"count":1},{"value":"STR-001","flag":0,"count":1},{"value":"STR-001","flag":0,"count":1},{"value":"STR-002","flag":0,"count":1}],
        "platform_id": [{"value":"SUB-001","flag":0,"count":1},{"value":"SUB-001","flag":0,"count":1},{"value":"SUB-002","flag":0,"count":1},{"value":"SUB-001","flag":0,"count":1},{"value":"SUB-001","flag":0,"count":1},{"value":"SUB-002","flag":0,"count":1}],
        "channel": [{"value":"APP","flag":0,"count":1},{"value":"SMS","flag":0,"count":1},{"value":"APP","flag":0,"count":1},{"value":"APP","flag":0,"count":1},{"value":"SMS","flag":0,"count":1},{"value":"APP","flag":0,"count":1}],
        "digo_trd_fund_amt_inout_cy_jjgr": [{"value":1250000.00,"flag":0,"count":1},{"value":980000.00,"flag":0,"count":1},{"value":1480000.00,"flag":0,"count":1},{"value":1320000.00,"flag":0,"count":1},{"value":1040000.00,"flag":0,"count":1},{"value":1550000.00,"flag":0,"count":1}],
        "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt": [{"value":128,"flag":0,"count":1},{"value":101,"flag":0,"count":1},{"value":152,"flag":0,"count":1},{"value":136,"flag":0,"count":1},{"value":109,"flag":0,"count":1},{"value":161,"flag":0,"count":1}],
        "digo_trd_fund_amt_inout_cy_jjgr_pb": [{"value":420000.00,"flag":0,"count":1},{"value":315000.00,"flag":0,"count":1},{"value":510000.00,"flag":0,"count":1},{"value":450000.00,"flag":0,"count":1},{"value":338000.00,"flag":0,"count":1},{"value":536000.00,"flag":0,"count":1}],
        "digo_pbcnt_kgdb_a566_fh_jjgr": [{"value":96,"flag":0,"count":1},{"value":81,"flag":0,"count":1},{"value":112,"flag":0,"count":1},{"value":103,"flag":0,"count":1},{"value":87,"flag":0,"count":1},{"value":121,"flag":0,"count":1}],
        "digo_fund_trd_amt_a566_fh_kgdb_jj0": [{"value":860000.00,"flag":0,"count":1},{"value":640000.00,"flag":0,"count":1},{"value":930000.00,"flag":0,"count":1},{"value":905000.00,"flag":0,"count":1},{"value":690000.00,"flag":0,"count":1},{"value":982000.00,"flag":0,"count":1}],
        "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt": [{"value":74,"flag":0,"count":1},{"value":57,"flag":0,"count":1},{"value":88,"flag":0,"count":1},{"value":79,"flag":0,"count":1},{"value":62,"flag":0,"count":1},{"value":94,"flag":0,"count":1}],
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr": [{"value":210000.00,"flag":0,"count":1},{"value":175000.00,"flag":0,"count":1},{"value":265000.00,"flag":0,"count":1},{"value":230000.00,"flag":0,"count":1},{"value":188000.00,"flag":0,"count":1},{"value":279000.00,"flag":0,"count":1}],
        "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt": [{"value":39,"flag":0,"count":1},{"value":31,"flag":0,"count":1},{"value":46,"flag":0,"count":1},{"value":42,"flag":0,"count":1},{"value":34,"flag":0,"count":1},{"value":49,"flag":0,"count":1}],
        "digo_cust_asset_in": [{"value":186,"flag":0,"count":1},{"value":143,"flag":0,"count":1},{"value":211,"flag":0,"count":1},{"value":194,"flag":0,"count":1},{"value":151,"flag":0,"count":1},{"value":223,"flag":0,"count":1}],
        "digo_new_cust_asset_in": [{"value":72,"flag":0,"count":1},{"value":55,"flag":0,"count":1},{"value":84,"flag":0,"count":1},{"value":78,"flag":0,"count":1},{"value":60,"flag":0,"count":1},{"value":90,"flag":0,"count":1}],
        "digo_cnt_cust_code_new_brok": [{"value":154,"flag":0,"count":1},{"value":119,"flag":0,"count":1},{"value":179,"flag":0,"count":1},{"value":163,"flag":0,"count":1},{"value":126,"flag":0,"count":1},{"value":190,"flag":0,"count":1}],
        "digo_cust_valid_new_yxh": [{"value":91,"flag":0,"count":1},{"value":68,"flag":0,"count":1},{"value":108,"flag":0,"count":1},{"value":97,"flag":0,"count":1},{"value":73,"flag":0,"count":1},{"value":115,"flag":0,"count":1}],
        "digo_cust_asset_in10000": [{"value":43,"flag":0,"count":1},{"value":32,"flag":0,"count":1},{"value":51,"flag":0,"count":1},{"value":46,"flag":0,"count":1},{"value":35,"flag":0,"count":1},{"value":55,"flag":0,"count":1}]
      },
      "metas": [
        {"name":"metric_time","dataType":null,"dataTypeName":"TIMESTAMP","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"attribution_plan_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"attribution_strategy_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"platform_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"channel","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_trd_fund_amt_inout_cy_jjgr","dataType":null,"dataTypeName":"DECIMAL","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_trd_fund_amt_inout_cy_jjgr_pb","dataType":null,"dataTypeName":"DECIMAL","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_pbcnt_kgdb_a566_fh_jjgr","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_fund_trd_amt_a566_fh_kgdb_jj0","dataType":null,"dataTypeName":"DECIMAL","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr","dataType":null,"dataTypeName":"DECIMAL","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_cust_asset_in","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_new_cust_asset_in","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_cnt_cust_code_new_brok","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_cust_valid_new_yxh","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"},
        {"name":"digo_cust_asset_in10000","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_zb"}
      ]
    }
  }
}
```

`viewName=cljd_zcl_wd_view` 的列式数据（19 列 × 18 行，行序 = G1t1,G1t2,G1t3,G2t1,…,G6t3）：

```json
{
  "code": "200",
  "success": true,
  "data": {
    "total": 18,
    "queryId": "mock-query-cljd_zcl_wd_view",
    "warning": null,
    "table": {
      "total": 18,
      "columns": {
        "metric_time": [{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"},{"value":"2026-09-02"}],
        "attribution_plan_id": ["PLAN-001" × 18 → 全为 PLAN-001"],
        "attribution_plan_name": ["策略解读示例计划" × 18],
        "attribution_plan_um_account": ["plan_owner" × 18],
        "attribution_strategy_id": ["STR-001","STR-001","STR-001","STR-001","STR-001","STR-001","STR-002","STR-002","STR-002","STR-001","STR-001","STR-001","STR-001","STR-001","STR-001","STR-002","STR-002","STR-002"],
        "attribution_strategy_name": ["高净值客户触达策略" × 6, "新客增长策略" × 3, "高净值客户触达策略" × 6, "新客增长策略" × 3],
        "attribution_over_by": ["strategy_owner_a" × 6, "strategy_owner_b" × 3, "strategy_owner_a" × 6, "strategy_owner_b" × 3],
        "platform_id": ["SUB-001" × 6, "SUB-002" × 3, "SUB-001" × 6, "SUB-002" × 3],
        "platform_name": ["场内公募非货子策略" × 6, "新客入金子策略" × 3, "场内公募非货子策略" × 6, "新客入金子策略" × 3],
        "create_by": ["data_owner" × 18],
        "channel": ["APP","APP","APP","SMS","SMS","SMS","APP","APP","APP","APP","APP","APP","SMS","SMS","SMS","APP","APP","APP"],
        "metric_id": ["trd_fund_amt_inout_cy_jjgr","fund_trd_amt_a566_fh_kgdb_jj0","pub_fh_kgdb_trdamt_ppcadd_jjgr"] × 6,
        "metric_name": ["经纪个人客户场内公募非货当年净买入","经纪个人场内公募非货交易量","经纪个人场内公募非货加仓交易量"] × 6,
        "digo_strategy_cnt": [2,2,2, 2,2,2, 3,3,3, 2,2,2, 2,2,2, 3,3,3],
        "digo_strategy_cnt_distr_1": [2,2,2, 2,2,2, 3,3,3, 2,2,2, 2,2,2, 3,3,3],
        "digo_distr_count_1": [180,180,180, 130,130,130, 180,180,180, 180,180,180, 130,130,130, 180,180,180],
        "digo_distr_user_cnt_a": [150,150,150, 105,105,105, 150,150,150, 150,150,150, 105,105,105, 150,150,150],
        "digo_touch_cnt_1": [120,120,120, 88,88,88, 120,120,120, 120,120,120, 88,88,88, 120,120,120],
        "digo_touch_user_cnt_1": [96,96,96, 71,71,71, 96,96,96, 96,96,96, 71,71,71, 96,96,96]
      },
      "metas": [
        {"name":"metric_time","dataType":null,"dataTypeName":"TIMESTAMP","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_plan_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_plan_name","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_plan_um_account","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_strategy_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_strategy_name","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"attribution_over_by","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"platform_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"platform_name","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"create_by","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"channel","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"metric_id","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"metric_name","dataType":null,"dataTypeName":"VARCHAR","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_strategy_cnt","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_strategy_cnt_distr_1","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_distr_count_1","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_distr_user_cnt_a","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_touch_cnt_1","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"},
        {"name":"digo_touch_user_cnt_1","dataType":null,"dataTypeName":"BIGINT","displaySize":null,"schemaName":"default","scale":null,"precision":null,"tableName":"cljd_zcl_wd"}
      ]
    }
  }
}
```

> 上表为**紧凑写法**（`× N` 表示重复、数组为裸值），落地时需展开为 `{"value":...,"flag":0,"count":1}` 的完整形式 —— 展开规则与 §6.6 第一段完全一致。为避免 18×19 的 JSON 把文档撑爆，这里只给展开语义；实现时建议直接用脚本从这张表生成 JSON（见 §9 校验脚本思路）。

**行式等价写法（调试用，`rows()` 同样支持）**：

```json
{
  "code": "200",
  "success": true,
  "data": {
    "table": {
      "rows": [
        { "metric_time": "2026-09-01", "attribution_plan_id": "PLAN-001", "channel": "APP", "digo_cust_asset_in": 186 },
        { "metric_time": "2026-09-01", "attribution_plan_id": "PLAN-001", "channel": "SMS", "digo_cust_asset_in": 143 }
      ]
    }
  }
}
```

`rows()` → `extractRows()` 的抽取优先级：`data.analysisView` / `data.table` 下钻 → `rows`/`rowData`/`records`/`list`… → `columns` 列式 → 整个 `data` 下任意首个 `List<Map>`。

### 6.7 `metrics_query`

`POST /semantic/api/v1.1/metrics/query`（带筛选时走此路径；`AloudataAnalysisViewQueryCompiler` 生成的 body）

请求示例：

```json
{
  "metrics": ["digo_cust_asset_in", "digo_new_cust_asset_in"],
  "dimensions": ["metric_time", "attribution_plan_id", "attribution_strategy_id", "platform_id", "channel"],
  "filters": [{ "field": "channel", "operator": "eq", "value": "APP" }],
  "timeConstraint": "(metric_time >= '2026-09-01' AND metric_time <= '2026-09-30')",
  "limit": 20,
  "offset": 0,
  "queryResultType": "DATA"
}
```

响应（真实结构 `data.table.columns`）：

```json
{
  "success": true,
  "code": "200",
  "errorMsg": null,
  "detailErrorMsg": null,
  "traceId": "mock-trace-metrics-query",
  "data": {
    "table": {
      "total": 3,
      "columns": {
        "metric_time": [{"value":"2026-09-01"},{"value":"2026-09-01"},{"value":"2026-09-02"}],
        "attribution_plan_id": [{"value":"PLAN-001"},{"value":"PLAN-001"},{"value":"PLAN-001"}],
        "attribution_strategy_id": [{"value":"STR-001"},{"value":"STR-002"},{"value":"STR-001"}],
        "platform_id": [{"value":"SUB-001"},{"value":"SUB-002"},{"value":"SUB-001"}],
        "channel": [{"value":"APP"},{"value":"APP"},{"value":"APP"}],
        "digo_cust_asset_in": [{"value":186},{"value":211},{"value":194}],
        "digo_new_cust_asset_in": [{"value":72},{"value":84},{"value":78}]
      }
    }
  }
}
```

> mock 侧建议实现**最简筛选语义**：只处理 `operator=eq` 且 `field` 属维度的条件（对 `analysis_view_query_data` 的数据行做 JVM 内过滤），其余条件忽略并在响应 `message` 里保持 `null`。目标是让前端「预览」不报错、数据量随筛选变化可感知，**不需要**实现完整 SQL 语义。

---

## 7. dataagent 对外响应（前端可见，逐接口期望值）

### 7.1 `GET /v1/datasources/9001/analysis-views`

```json
{ "code": 200, "msg": "操作成功", "data": [
  { "id": "cljd_zcl_zb_view", "viewName": "cljd_zcl_zb_view", "displayName": "策略解读-子策略-指标", "categoryId": "CAT_CLJD", "categoryName": "策略解读" },
  { "id": "cljd_zcl_wd_view", "viewName": "cljd_zcl_wd_view", "displayName": "策略解读-子策略-维度", "categoryId": "CAT_CLJD", "categoryName": "策略解读" }
]}
```

### 7.2 `GET /v1/datasources/9001/analysis-views/list?onlyMine=true`

```json
{ "code": 200, "msg": "操作成功", "data": [
  { "id": "cljd_zcl_zb_view", "viewName": "cljd_zcl_zb_view", "displayName": "策略解读-子策略-指标", "description": "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）", "owner": "<数据源认证值>", "mine": true },
  { "id": "cljd_zcl_wd_view", "viewName": "cljd_zcl_wd_view", "displayName": "策略解读-子策略-维度", "description": "策略解读：子策略维度的下发/触达指标", "owner": "<数据源认证值>", "mine": true }
]}
```

### 7.3 ★ `GET /v1/datasources/9001/analysis-views/cljd_zcl_zb_view/fields`

**先指标后维度**，共 18 项：

```json
{ "code": 200, "msg": "操作成功", "data": [
  { "name": "digo_trd_fund_amt_inout_cy_jjgr", "displayName": "经纪个人客户场内公募非货当年净买入", "description": "经纪个人客户场内公募非货当年净买入-策略归因", "role": "measure" },
  { "name": "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", "displayName": "经纪个人客户场内公募非货当年净买入-转化客户数", "description": "经纪个人客户场内公募非货当年净买入-转化客户数-策略归因", "role": "measure" },
  { "name": "digo_trd_fund_amt_inout_cy_jjgr_pb", "displayName": "经纪个人场内公募非货破冰客户当年净买入", "description": "经纪个人场内公募非货破冰客户当年净买入-策略归因", "role": "measure" },
  { "name": "digo_pbcnt_kgdb_a566_fh_jjgr", "displayName": "经纪个人场内公募非货破冰客户数", "description": "经纪个人场内公募非货破冰客户数-策略归因", "role": "measure" },
  { "name": "digo_fund_trd_amt_a566_fh_kgdb_jj0", "displayName": "经纪个人场内公募非货交易量", "description": "经纪个人场内公募非货交易量-策略归因", "role": "measure" },
  { "name": "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", "displayName": "经纪个人场内公募非货交易量-转化客户数", "description": "经纪个人场内公募非货交易量-转化客户数-策略归因", "role": "measure" },
  { "name": "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", "displayName": "经纪个人场内公募非货加仓交易量", "description": "经纪个人场内公募非货加仓交易量-策略归因", "role": "measure" },
  { "name": "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", "displayName": "经纪个人场内公募非货加仓交易量-转化客户数", "description": "经纪个人场内公募非货加仓交易量-转化客户数-策略归因", "role": "measure" },
  { "name": "digo_cust_asset_in", "displayName": "入金客户数", "description": "入金客户数_策略归因", "role": "measure" },
  { "name": "digo_new_cust_asset_in", "displayName": "新客入金数", "description": "新客入金数_策略归因", "role": "measure" },
  { "name": "digo_cnt_cust_code_new_brok", "displayName": "新增客户数", "description": "新增客户数_策略归因", "role": "measure" },
  { "name": "digo_cust_valid_new_yxh", "displayName": "新增有效户数", "description": "新增有效户数_策略归因", "role": "measure" },
  { "name": "digo_cust_asset_in10000", "displayName": "万元入金户数", "description": "万元入金户数_策略归因", "role": "measure" },
  { "name": "metric_time", "displayName": "指标日期", "description": "指标日期", "role": "dimension" },
  { "name": "attribution_plan_id", "displayName": "计划id", "description": "计划id", "role": "dimension" },
  { "name": "attribution_strategy_id", "displayName": "策略id", "description": "策略id", "role": "dimension" },
  { "name": "platform_id", "displayName": "子策略id", "description": "子策略id", "role": "dimension" },
  { "name": "channel", "displayName": "触达渠道", "description": "触达渠道", "role": "dimension" }
]}
```

前端 `FieldMappingDialog` 三列渲染结果（节选）：

| 字段名 | 字段描述 | 目标名称（默认值） |
|---|---|---|
| digo_trd_fund_amt_inout_cy_jjgr | 经纪个人客户场内公募非货当年净买入-策略归因 | 经纪个人客户场内公募非货当年净买入 |
| digo_cust_asset_in | 入金客户数_策略归因 | 入金客户数 |
| metric_time | 指标日期 | 指标日期 |
| channel | 触达渠道 | 触达渠道 |

### 7.4 `POST /v1/dataset-composer/drafts/preview`

请求 `{"sourceType":"ALOUDATA_ANALYSIS_VIEW","datasourceId":"9001","sourceConfig":{"analysisViewId":"cljd_zcl_zb_view"},"filters":[],"limit":20}`

```json
{ "code": 200, "msg": "操作成功", "data": {
  "rows": [
    { "metric_time": "2026-09-01", "attribution_plan_id": "PLAN-001", "attribution_strategy_id": "STR-001", "platform_id": "SUB-001", "channel": "APP", "digo_trd_fund_amt_inout_cy_jjgr": 1250000.00, "digo_cust_asset_in": 186, "digo_cust_asset_in10000": 43 },
    { "metric_time": "2026-09-01", "attribution_plan_id": "PLAN-001", "attribution_strategy_id": "STR-001", "platform_id": "SUB-001", "channel": "SMS", "digo_trd_fund_amt_inout_cy_jjgr": 980000.00, "digo_cust_asset_in": 143, "digo_cust_asset_in10000": 32 }
  ],
  "schema": ["metric_time", "attribution_plan_id", "attribution_strategy_id", "platform_id", "channel", "digo_trd_fund_amt_inout_cy_jjgr", "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", "digo_trd_fund_amt_inout_cy_jjgr_pb", "digo_pbcnt_kgdb_a566_fh_jjgr", "digo_fund_trd_amt_a566_fh_kgdb_jj0", "digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", "digo_cust_asset_in", "digo_new_cust_asset_in", "digo_cnt_cust_code_new_brok", "digo_cust_valid_new_yxh", "digo_cust_asset_in10000"],
  "rowCount": 6,
  "pushdownReport": { "pushedFilters": [], "residualFilters": [] },
  "executionId": "draft-<uuid>"
}}
```

> 上面 `rows` 为节选（真实应返回 6 行全 18 列）。`schema` 取自首行 keySet，**顺序由列式 `columns` 的插入顺序决定**，因此 mock 的 `columns` 要保持 dims→metrics 的稳定顺序。

### 7.5 `POST /v1/dataset-composer/drafts/confirm`

```json
{ "code": 200, "msg": "操作成功", "data": {
  "datasetId": "12345",
  "descriptor": {
    "datasetId": 12345,
    "inputName": "策略解读-子策略-指标",
    "sourceType": "ALOUDATA_ANALYSIS_VIEW",
    "schema": [
      { "name": "metric_time", "title": "指标日期", "dataType": "DATE", "nullable": true, "semanticRole": "dimension" },
      { "name": "channel", "title": "触达渠道", "dataType": "STRING", "nullable": true, "semanticRole": "dimension" },
      { "name": "digo_cust_asset_in", "title": "入金客户数", "dataType": "BIGINT", "nullable": true, "semanticRole": "measure" }
    ],
    "rowCount": 6,
    "dataRef": null
  }
}}
```

> `descriptor.schema` 由 `AloudataAnalysisViewAdapter#describe` 生成，**维度在前、指标在后**；`title` 取视图详情里的 `displayName`，`dataType` 取 `dataType`（缺省 `STRING`）。因此 §6.3 mock 里的 `dataType` 必须写全，否则会退化成 `STRING`。

---

## 8. P0 前置问题（必须先处理，否则 mock 跑不通）

### P0-1 `getByName` 按对象数组解析 `metrics`/`dimensions`，而真实是字符串数组（**已定论 + 已修复**）

- **实测定论（2026-09-18）**：直连 demo 环境抓 `queryByName` 原文，`data.metrics[0]` 是**字符串**：

  ```bash
  curl -s 'https://demo.can.aloudata.com/anymetrics/api/v1/analysisview/queryByName?viewName=Demo_view' \
    -H 'tenant-id: tn_27436' -H 'auth-type: UID' -H 'auth-value: <UID>'
  ```

  ```json
  "metrics": ["AUM", "RJCYKSL1", "SNMCKYE1", "ytd_avg_aum", "thisyear_tob_aum"],
  "dimensions": ["metric_time__day", "Demo_finance_productinfo_product_name_lv1"],
  "displayNameMap": { "RJCYKSL1": "人均持有卡数量", "AUM": "AUM", "SNMCKYE1": "上年末存款余额" }
  ```

- **修复前的两个读取方**：

  | 读取方 | 代码 | 真实形状下的结果 |
  |---|---|---|
  | `getByName` | `mapList(data.get("metrics"))` | ❌ `objectMapper.convertValue(List<String>, List<Map>)` 抛 `IllegalArgumentException` → 详情接口 **500** |
  | `listFields` | `stringList(data.get("metrics"))` | ✅ 正确（**上一轮判断是对的**） |

  受影响的下游（`getByName` 是共用入口）：`GET /analysis-views/{viewName}`、`AloudataAnalysisViewAdapter#describe`（数据集字段 / `descriptor`）、带筛选的 `previewDraft`（走 `viewDetail` → `QueryCompiler`）。

- **已应用的修复**（`AloudataAnalysisViewServiceImpl`）：

  1. 新增 `definitionList(value, displayNameMap)`：字符串元素归一为 `{name, displayName(取自 displayNameMap)}`，对象元素原样保留；
     供 `getByName` 构造 `AloudataAnalysisViewDetail` 使用。
  2. `stringList(value)` 反向兼容对象元素（从 `name`/`metricName`/`dimName`/`code` 取首个非空），
     避免将来遇到对象数组环境时产出 `{name=...}` 脏串。

  **双向容错**，两种真实形态都能工作；顺带让 `Adapter#column()` 的 `title` 拿到真实展示名（此前只能回落到字段名）。

- **配套单测**：`AloudataAnalysisViewServiceTest` 新增 4 例——字符串数组下的 `listFields`（含「先指标后维度」、描述取 `businessCaliber`/`dimDescription`、无脏串断言）、字符串数组下的 `getByName`（含 `displayNameMap` 归一）、以及两条对象数组回归。原有 2 例未改。
  此前 `listFields` **完全没有单测**，所以这条路径的缺陷一直没暴露。

- **遗留（不阻塞）**：真实 `queryByName` 不返回字段 `dataType`，因此 `DatasetColumn.dataType` 会回落到 `STRING`；
  真实类型在 `analysisView/query` 的 `data.metas[].dataTypeName` 里。夹具已写入 `metas`（且 `generate-...py` 按指标名推断 DECIMAL/BIGINT），若后续要精确类型，从 `metas` 接入即可。

### P0-2 `analysisViewId` 的语义不统一（id 还是 viewName）—— **已核实：不影响本链路，无需改前端**

- **事实**：
  - `DatasetSourceDialog.vue:12` 下拉 `value = view.id || view.viewName` → 会把 id 传给后端，
    而后端 `DatasetComposerController#previewAloudataView` / `AloudataAnalysisViewAdapter#viewName` 都把
    `analysisViewId` **当 viewName** 用。
- **但它不在活跃链路上**（2026-09-18 复核）：
  - `DatasetSourceDialog` 的唯一引用方是 `DatasetInputPanel.vue`；
  - `DatasetInputPanel.vue` 全仓**无任何路由/父组件引用**（只有它自己的单测引用）→ **死代码**；
  - 活跃链路「卡片属性 → 添加数据集 → 指标视图」走的是 `AloudataDialog.vue:195`，
    选项 `:value="v.viewName"` —— **本来就是对的**。
- **结论**：mock 不需要为此做任何事，前端也不需要改。`DatasetSourceDialog` 若将来复活再修。
- **mock 夹具的选择**：刻意让 `id`（9001/9002）≠ `viewName`，与真实环境一致（实测 `id=824` / `viewName=cs202609161430`），
  这样任何复活死代码路径的人都会立刻撞上这个问题，而不是被 mock 掩盖。

### P0-3 `onlyMine` 依赖 `basicAttributes.owner` 与数据源 `auth-value` 严格相等

- **现象**：`AloudataDialog` 默认 `onlyMine=true`；`mine` 由 `configHelper.parseConfig(datasource).getAuthValue()`（= `dataagent_datasource.password` 明文）与 `basicAttributes.owner` 比较得出。
- **已在 mock 层消除**：夹具不写死 owner，装载时把 `__OWNER__` 替换为**当前数据源的认证值**，
  于是「只看我的」在任何数据源下都等价于「全部 mock 视图」，无需为此专造数据源。
- **自检**：`GET .../analysis-views/list?onlyMine=true` 应返回 2 条；若为 `[]`，
  说明走的不是 mock 数据源（或 profile 没生效）。

### P0-4（提示，非阻塞）前端 TS 类型缺 `ALOUDATA_METRICS`

`types/index.ts` 的 `DatasetSourceType` 漏了 `ALOUDATA_METRICS`（后端枚举有 6 个值）。本次只走 `ALOUDATA_ANALYSIS_VIEW`，不受影响，但改动 `DatasetSourcePicker` 时会踩到。

### P0-5（提示，非阻塞）前端调用但后端无映射

`datasetApi.getComposerExecution` 请求 `GET /v1/dataset-composer/executions/{id}`，而 `DatasetComposerController` **没有该映射** → 404。当前「添加数据集」主流程不依赖它，若某处触发需一并处理。

---

## 9. 验收清单

### 9.1 后端自检（curl，需先登录拿 token）

```bash
BASE=http://localhost:<port>/dataagent/api
H=(-H "Authorization: Bearer $TOKEN" -H "X-Workspace-Id: $WS")

# 1) 目录树 → 2 条，带 categoryName=策略解读
curl -s "${H[@]}" "$BASE/v1/datasources/9001/analysis-views" | jq '.code, (.data|length), .data[0]'

# 2) 平铺列表（只看我的）→ 2 条，mine=true
curl -s "${H[@]}" "$BASE/v1/datasources/9001/analysis-views/list?onlyMine=true" | jq '.data[] | {viewName, mine}'

# 3) ★ 字段清单 → 18 项，前 13 项 role=measure，后 5 项 role=dimension，displayName 非空
curl -s "${H[@]}" "$BASE/v1/datasources/9001/analysis-views/cljd_zcl_zb_view/fields" \
  | jq '.data | length, (map(.role)|group_by(.)|map({(.[0]):length})|add), (map(select(.displayName==null))|length)'
# 期望：18 / {measure:13, dimension:5} / 0

# 4) 视图详情 → metrics 13、dimensions 5
curl -s "${H[@]}" "$BASE/v1/datasources/9001/analysis-views/cljd_zcl_zb_view" | jq '.data.metrics|length, (.data.dimensions|length)'

# 5) 草稿预览 → rowCount=6，schema 18 列
curl -s "${H[@]}" -H 'Content-Type: application/json' -X POST "$BASE/v1/dataset-composer/drafts/preview" \
  -d '{"sourceType":"ALOUDATA_ANALYSIS_VIEW","datasourceId":"9001","sourceConfig":{"analysisViewId":"cljd_zcl_zb_view"},"filters":[],"limit":20}' \
  | jq '.data.rowCount, (.data.schema|length), (.data.rows[0]|keys|length)'
# 期望：6 / 18 / 18
```

### 9.2 前端人工验收（需硬刷新，避免缓存）

| # | 操作 | 期望 |
|---|---|---|
| 1 | 洞察 → 仪表盘 → 组件 → 卡片 → 属性 → 添加数据集 → 选择 Aloudata 数据源 → 指标视图 | 下拉出现「策略解读-子策略-指标」「策略解读-子策略-维度」 |
| 2 | 选中「策略解读-子策略-指标」，点「预览」 | 表格显示 6 行 × 18 列，中文列名正确 |
| 3 | 点「确定」 | 数据集创建成功，卡片绑定该数据集 |
| 4 | 打开「字段名称」弹窗 | 18 行；字段名/字段描述/目标名称三列**均有值且无 `{name=...}` 脏串**；目标名称默认 = 展示名 |
| 5 | 手工改一行「目标名称」后关闭再打开 | 改过的行保留人工值，未改的行使用展示名 |
| 6 | 卡片属性侧「指标视图」下拉（默认只看我的） | 2 条，非空 |

### 9.3 回归

```bash
# 单测：新增 listFields 用例 + 既有用例全绿
mvn -f mateclaw-dataagent/pom.xml -Dtest='AloudataAnalysisView*Test' test

# 前端类型与单测
cd mateclaw-dataagent-ui && pnpm vue-tsc --noEmit && pnpm vitest run src/utils/__tests__/field-mapping.spec.ts
```

---

## 10. 附录

### 10.1 文件索引（本方案涉及）

**已新增（已落地）**

| 路径 | 说明 |
|---|---|
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/aloudata/local/LocalAloudataApiClient.java` | mock 切面（`@Primary @Profile("local-mock")`） |
| `mateclaw-dataagent/src/main/java/vip/mate/dataagent/aloudata/local/LocalAloudataFixtures.java` | fixture 装载 + 参数感知过滤 / 分页 / 列投影 |
| `mateclaw-dataagent/src/main/resources/mock/aloudata/*.json` | 7 个端点夹具（§6） |
| `dev-support/local-simulation/scripts/generate-aloudata-fixtures.py` | 夹具生成脚本（从 `generated_cljd_zcl.sql`） |
| `src/test/java/.../aloudata/local/LocalAloudataFixturesTest.java` | 夹具契约单测（10 例，不需 Spring 上下文） |

**已修改（P0-1）**

| 路径 | 改动 |
|---|---|
| `service/impl/AloudataAnalysisViewServiceImpl.java` | 新增 `definitionList()`；`getByName` 改用它；`stringList()` 增加对象元素兼容 |
| `src/test/java/.../service/AloudataAnalysisViewServiceTest.java` | 新增 4 例（字符串数组 / 对象数组 × listFields / getByName） |

**关键只读参考**

| 路径 | 关注点 |
|---|---|
| `controller/DataAgentDatasourceController.java:463-511` | 四个指标视图接口定义 |
| `controller/DatasetComposerController.java:57-155, 223-251` | 草稿预览/确认、`previewAloudataView` |
| `dataset/AloudataAnalysisViewAdapter.java:226-311` | `rows()` / `extractRows()` / `convertColumnar()` 抽取规则 |
| `aloudata/AloudataEndpointService.java:114-161` | 7 个上游端点默认定义 |
| `aloudata/AloudataApiClient.java:102-183` | `callWithParams` 参数分发与校验 |
| `aloudata/AloudataConfigHelper.java:31-78` | `username`→tenantId、`password`→authValue |
| `mateclaw-dataagent-ui/src/utils/field-mapping.ts` | 字段默认值算法 |
| `mateclaw-dataagent-ui/src/views/insight/components/card-attribute/useInsight.ts:582-613` | `/fields` 优先调用与降级路径（其 `viewDisplayNameMap` 也期望对象数组，修复后一并可用） |

### 10.2 P0-1 的形态选择（已定论）

| mock 采用 | `/fields`（字段名称默认值） | `/{viewName}` 详情 + 数据集 `describe` |
|---|---|---|
| 对象数组 | ❌ 字段名/描述/目标名全变 `{name=...}` 脏串 | ✅ 正常 |
| **字符串数组（真实形态，已采用）** | ✅ 正常 | ❌ 修复前 500；**修复后正常**（`definitionList` 归一） |

**结论**：真实形态是字符串数组，所以 `getByName` 的 `mapList` 是缺陷方，已按 `definitionList` 归一修复；
同时给 `stringList` 加了对象兼容，避免反向踩坑。**教训：字段形状这类契约歧义，抓一次真实响应原文比读文档推断可靠得多** ——
本仓 javadoc 与调研记录当时都写对了，是代码走偏了。

### 10.3 与既有 `dev-support` WireMock 方案的关系

`dev-support/local-simulation/wiremock/mappings/` 已有一套 Aloudata 夹具，但：

| 问题 | 说明 |
|---|---|
| 缺 `analysisview/list` 映射 | `AloudataDialog` 的「指标视图」平铺下拉无数据 |
| `aloudata-tree-list.json` 用 `children` 而非 `analysisViewRoots` | `flattenTree` 兜底分支要求节点含 `subCategory`/`analysisViewList`/`analysisViewRoots`，该夹具三者皆无 → **目录解析不出任何视图** |
| 视图名/字段与「策略解读」无关 | 是 `local_sales_view` 销售样例 |

本方案（方案 A）不复用这套夹具，但 §6 的报文结构与它保持一致风格，并且**修正了上面两个结构性问题**。若将来切回 WireMock，§6 的 JSON 可直接改写为 mapping 的 `jsonBody`。

---

## 11. 一页速查

| 事项 | 值 |
|---|---|
| Mock 切点 | `AloudataApiClient.callWithParams`（`@Profile("local-mock")` + `@Primary`） |
| 开关 | `SPRING_PROFILES_ACTIVE=pgsql,local-mock` |
| 上游端点 | **7 个**：tree / list / queryByName / batchDetail / dimension/list / analysisView/query / metrics_query |
| 视图 | `cljd_zcl_zb_view`（6 行 / 5 维 13 指标）、`cljd_zcl_wd_view`（18 行 / 13 维 6 指标） |
| 镜像 `id` | `9001` / `9002`（int，**故意不等于 viewName**，与真实一致） |
| 字段形状 | `metrics`/`dimensions` = **字符串数组**；展示名在 `displayNameMap` |
| owner | 装载时替换为数据源认证值 → 「只看我的」恒命中（P0-3 已消除） |
| 字段接口返回顺序 | 先 measure（13）后 dimension（5） |
| 字段默认值来源 | `/fields` 的 `displayName`，缺省回落 `name` |
| 未命中视图 | 返回 `SM_02_0038` → dataagent 映射为 403 `VIEW_ACCESS_DENIED` |
| 硬前提 | P0-1（`getByName` 字符串数组解析，**已修**）；P0-2（`analysisViewId` 语义）**已核实只在死代码里，活跃链路无需改前端** |
| 单测 | `LocalAloudataFixturesTest` 10 例 + `AloudataAnalysisViewServiceTest` 6 例，全绿 |
