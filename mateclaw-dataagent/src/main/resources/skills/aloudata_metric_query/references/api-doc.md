# Aloudata 指标查询 API 参数参考

## 接口信息

- **URL**: `POST http://{语义层服务地址}/semantic/api/v1.1/metrics/query`
- **语义层服务地址获取**：指标平台产品界面中，「指标应用」->「API集成」中，环境信息即为语义层服务地址

---

## Header 参数

| 参数 | 必填 | 描述 | 类型 | 最大长度 |
|------|------|------|------|---------|
| `tenant-id` | 必填 | 租户ID，指标平台为多租户架构，查询时需要指明资产所在的租户 | String | 32 |
| `auth-type` | 必填 | 认证方式，支持 `UID`、`TOKEN`、`ACCOUNT`、`APIKEY` | String | 32 |
| `auth-value` | 必填 | 与 auth-type 对应的认证值 | String | 32 |
| `query-user-account` | 选填 | 鉴权用户，使用该用户进行鉴权处理，若为空则使用 auth-value 对应的用户进行鉴权 | String | 32 |

**获取 tenant-id、UID**：指标平台产品界面中，「指标应用」->「API集成」中，tenant-id 即为当前账号的租户ID，user-id 即为当前账号的 UID

**获取 TOKEN**：参考帮助手册「获取访问凭证」

**获取 query-user-account**：用户的用户名（登录名），可在个人中心或管理设置中查看

---

## Body 参数总览

| 参数 | 必填 | 描述 | 类型                  |
|------|------|------|---------------------|
| `metrics` | 必填 | 查询指标，支持已定义指标、metricDefinitions 临时指标、快速计算语法 | Array[String]       |
| `metricDefinitions` | 选填 | 临时指标定义，基于已定义指标临时生成 | Map<String, Object> |
| `dimensions` | 选填 | 查询维度，支持已定义维度；日期维度（`metric_time`）支持粒度切换（year/quarter/month/week/day/hour/minute）。默认按指标日期查询，用户指定其他维度另说。详见 [dimensions 详解](#dimensions-详解) | Array[String]       |
| `filters` | 选填 | 全局筛选，对全部指标进行维度过滤（支持文本/数值/日期/JSON 维度，含业务日期维度如 `dim_order_date`） | Array[String]       |
| `specialMvConfig` | 选填 | 物化表加速配置，控制是否启用指定物化表加速及未命中处理 | Map                 |
| `resultFilters` | 选填 | 结果筛选，对查询结果进行二次过滤（按指标值或维度值） | Array[String]       |
| `timeConstraint` | 选填 | 指标日期范围，**仅支持使用 `metric_time` 维度**（业务日期维度筛选请用 `filters`）。详见 [timeConstraint 详解](#timeconstraint-详解) | String              |
| `orders` | 选填 | 排序，排序字段需包含在 metrics 或 dimensions 中 | Array[Map]          |
| `limit` | 选填 | 返回条数，默认100 | int                 |
| `offset` | 选填 | 偏移量，默认1（如 offset=100 时从第100条开始返回） | int                 |
| `queryResultType` | 选填 | 返回内容类型：`SQL_AND_DATA`（默认，同时返回数据和SQL）/ `SQL`（仅SQL）/ `DATA`（仅数据） | String              |
| `source` | 选填 | 查询来源标识（自定义参数） | String              |
| `isQueryTotalCount` | 选填 | 是否返回数据总条数 | Boolean             |

---

## dimensions 详解

查询维度，使用维度的英文名（或编码，取决于指标平台配置）。多个维度之间用逗号分隔。

```json
"dimensions": ["metric_time__day", "province", "city"]
```

### 默认行为：按指标日期查询

一般情况下，dimensions 都根据**指标日期**（`metric_time`）进行查询。`metric_time` 是指标平台内建的时间维度，几乎所有查询都应包含它来限定数据的时间粒度。用户若未指定其他业务维度（如区域、产品、渠道），dimensions 只放 `metric_time` 的某个粒度即可。

### metric_time 粒度切换

`metric_time` 支持以下粒度的快速切换（在维度名后加 `__粒度`）：

| 粒度 | 维度名 | 含义 |
|------|--------|------|
| year | `metric_time__year` | 年 |
| quarter | `metric_time__quarter` | 季 |
| month | `metric_time__month` | 月 |
| week | `metric_time__week` | 周 |
| day | `metric_time__day` | 日 |
| hour | `metric_time__hour` | 小时 |
| minute | `metric_time__minute` | 分钟 |

### N 倍粒度（仅 hour/minute）

`metric_time` 额外支持 `{N}hour` 和 `{N}minute` 粒度，用于按 N 小时/N 分钟聚合：

```json
"dimensions": ["metric_time__2hour"]   // 以 2 小时为粒度
```

### 自定义日历粒度

若指标平台配置了自定义日历（如财年），可用 `metric_time__{自定义日历名称}_粒度`：

```json
"dimensions": ["metric_time__FY_MONTH"]   // 财年月
```

自定义日历名称在「管理设置」->「自定义日历」中维护。

### 与 timeConstraint 的粒度独立原则

dimensions 决定**显示粒度**，timeConstraint 决定**筛选范围**，两者可以使用不同的时间粒度。例如 dimensions 用 `metric_time__day` 展示每日数据，timeConstraint 用 `([metric_time__month]="2024-03")` 限定只查 3 月。详见 [timeConstraint 详解](#timeconstraint-详解)。

## metrics 快速计算语法

在 metrics 中可以使用语法糖直接定义衍生指标，无需通过 metricDefinitions。

### 同环比

语法：`{指标名}__sameperiod__{偏移粒度}__{同环比方法}`

如需日期标识：`{指标名}__sameperiod__{偏移粒度}__{日期标识}__{同环比方法}`

#### 偏移粒度

| 偏移粒度 | 含义 | 示例 |
|---------|------|------|
| `dod` / `{N}_dod` | 日偏移 | `dod`（昨日），`-2_dod`（前天），`-14_dod`（14天前） |
| `wow` / `{N}_wow` | 周偏移 | `wow`（上周），`-2_wow`（两周前） |
| `mom` / `{N}_mom` | 月偏移 | `mom`（上月），`-2_mom`（两月前） |
| `qoq` / `{N}_qoq` | 季偏移 | `qoq`（上季），`-2_qoq`（两季前） |
| `yoy` / `{N}_yoy` | 年偏移 | `yoy`（去年），`-2_yoy`（前年） |
| `woeow` / `{N}_woeow` | 上周末偏移 | 上个周末 |
| `moeom` / `{N}_moeom` | 上月末偏移 | 上个月末 |
| `qoeoq` / `{N}_qoeoq` | 上季末偏移 | 上个季度末 |
| `yoeoy` / `{N}_yoeoy` | 上年末偏移 | 上个年末 |
| `wosow` / `{N}_wosow` | 上周初偏移 | 上个周初 |
| `mosom` / `{N}_mosom` | 上月初偏移 | 上个月初 |
| `qosoq` / `{N}_qosoq` | 上季初偏移 | 上个季初 |
| `yosoy` / `{N}_yosoy` | 上年初偏移 | 上个年初 |
| `{N}_{日历名}_{粒度}` | 自定义日历偏移 | `-2_FY_mom`（2个财年月前） |

**自定义日历获取**：在「管理设置」->「自定义日历」模块中查看维护的自定义日历名称

#### 日期标识

可选，用于区分工作日、交易日等。插入在偏移粒度和同环比方法之间。

示例：`order_count__sameperiod__-2_dod__workdays__growth`

**日期标识获取**：在「管理设置」->「时间限定」模块中查看维护的日期标识名称

#### 同环比方法

| 方法 | 含义 | 公式 |
|------|------|------|
| `value` | 对比值（原始值） | 对比期的值 |
| `growthvalue` | 增长值 | 当前值 - 对比值 |
| `growth` | 增长率 | (当前值 - 对比值) / 对比值 |
| `decrease` | 下降值 | 对比值 - 当前值 |
| `decreaserate` | 下降率 | (对比值 - 当前值) / 对比值 |

#### 同环比使用约束

1. 使用同环比时，metric_time 维度必须在 dimensions 或 timeConstraint 中被使用
2. 在 timeConstraint 中使用时，必须是**单值筛选**
3. 同环比的偏移粒度不可小于 metric_time 的日期粒度（如月粒度下不可用日环比）

#### 同环比示例

```
"sales_amount__sameperiod__yoy__value"               // 去年同期值
"sales_amount__sameperiod__yoy__growth"               // 同比增长率
"sales_amount__sameperiod__mom__growthvalue"          // 环比增长值
"sales_amount__sameperiod__dod__growth"               // 日环比增长率
"order_count__sameperiod__-2_dod__workdays__growth"   // 两个工作日前环比增长率
"sales_amount__sameperiod__-52_wow__value"            // 52周前同比值
"sales_amount__sameperiod__-6_mom__value"             // 半年前同比值
"sales_amount__sameperiod__yoeoy__growthvalue"        // 同比去年年底增长值
"sales_amount__sameperiod__0_yosoy__growthvalue"      // 同比本年年初增长值
"sales_amount__sameperiod__-2_FY_mom__value"          // 两个财年月前同比值
```

### 占比

语法：`{指标名}__proportion__{占比范围维度1,占比范围维度2}`

占比维度必须在 dimensions 中声明。省略占比维度则为全局占比。

```
"sales_amount__proportion__province"   // 省内占比
"sales_amount__proportion"             // 全局占比
```

### 排名

语法：`{指标名}__rank__{排名范围维度1,排名范围维度2}`

排名维度必须在 dimensions 中声明。省略排名维度则为全局排名。

```
"sales_amount__rank__province"   // 省内排名
"sales_amount__rank"             // 全局排名
```

### 时间限定

语法：`{指标名}__timefilter__{时间范围表达式}`

为指标添加时间限定，如查询近7日数据：

```
"sales_amount__timefilter__[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")"
```

---

## timeConstraint 详解

timeConstraint 用于指定**指标日期范围**，使用表达式语法。

### 核心约束

> ⚠️ **timeConstraint 仅支持使用 `metric_time` 维度**。业务日期维度（如 `dim_order_date`）的日期筛选请用 `filters`，不能放在 timeConstraint 中。

**格式要求**：

1. 整个表达式**必须用 `()` 包裹**
2. **不支持 BETWEEN 语法**，日期区间用 `AND` 连接两个边界条件
3. 日期字符串值必须用双引号包裹（JSON 中转义为 `\"`）
4. 维度引用用方括号 `[metric_time]` 或 `['metric_time']`（两种写法均可）

### 两种引用模式

| 模式 | 语法 | 适用场景 | 示例 |
|------|------|---------|------|
| **DateTrunc 截断比较** | `DateTrunc([metric_time],\"粒度\")=DateTrunc(Today(),\"粒度\")` | 按月/年汇总时，截断到同一粒度比较 | 当月、上月、今年 |
| **直接维度比较** | `[metric_time__粒度] 运算符 值` | 精确到天/月/年粒度的范围筛选 | 近7天、指定日期范围 |

### 常用表达式速查

| 场景 | 表达式 |
|------|--------|
| 当月 | `(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))` |
| 上月 | `(DateTrunc([metric_time],\"MONTH\")=DateAdd(DateTrunc(Today(),\"MONTH\"),-1,\"MONTH\"))` |
| 近7天 | `([metric_time__day]>=DateAdd(Today(),-7,\"DAY\"))` |
| 近30天 | `([metric_time__day]>=DateAdd(Today(),-30,\"DAY\"))` |
| 今年 | `(DateTrunc([metric_time],\"YEAR\")=DateTrunc(Today(),\"YEAR\"))` |
| 去年同期 | `(DateTrunc([metric_time],\"YEAR\")=DateTrunc(DateAdd(Today(),-1,\"YEAR\"),\"YEAR\"))` |
| 今天 | `([metric_time__day]=Today())` |
| 指定某一天 | `([metric_time__day]=\"2025-01-15\")` |
| 指定日期范围 | `([metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\")` |
| 指定月份 | `([metric_time__month]=\"2024-03\")` |
| 指定年份 | `([metric_time__year]=\"2025\")` |

### 表达式函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `Today()` / `Now()` | 当前日期/时间（两者均可，函数名大小写不敏感） | `Today()`、`Now()` |
| `DateAdd(date, N, unit)` | 日期偏移，N 为整数（负数=向前） | `DateAdd(Today(),-7,\"DAY\")` |
| `DateTrunc(date, unit)` | 日期截断到指定粒度 | `DateTrunc(Today(),\"MONTH\")` |
| `date(string)` | 字符串转日期 | `date(\"2024-03-01\")` |

unit 取值：`"DAY"` / `"WEEK"` / `"MONTH"` / `"QUARTER"` / `"YEAR"`（注意 JSON 中需转义为 `\"DAY\"`）。自定义日历还支持 `"FY_YEAR"` / `"FY_MONTH"` 等。

### 维度引用

在表达式中用方括号引用 `metric_time` 维度，支持基础维度和各粒度：

| 引用 | 用途 |
|------|------|
| `[metric_time]` | 基础时间维度，**配合 DateTrunc 函数**进行截断比较 |
| `[metric_time__day]` | 日粒度，用于直接比较或范围筛选 |
| `[metric_time__week]` | 周粒度 |
| `[metric_time__month]` | 月粒度，用于直接比较 |
| `[metric_time__quarter]` | 季粒度 |
| `[metric_time__year]` | 年粒度 |
| `[metric_time__{自定义日历}_粒度]` | 自定义日历粒度（如 `[metric_time__FY_MONTH]`） |

### 自定义日历（财年等）

若指标平台配置了自定义日历，timeConstraint 可使用自定义日历粒度：

```json
// 本财年
"timeConstraint": "DateTrunc(['metric_time'], \"FY_YEAR\") = DateTrunc(Now(), \"FY_YEAR\")"

// 指定 2025 财年
"timeConstraint": "['metric_time__FY_YEAR'] = \"FY2025\""
```

### 与 dimensions 的配合

timeConstraint 和 dimensions 协同工作，遵循以下规则：

1. **dimensions 决定显示粒度**：在 dimensions 中指定 `metric_time__day`/`metric_time__month`/`metric_time__year` 等粒度
2. **timeConstraint 决定筛选范围**：在 timeConstraint 中指定日期条件
3. **粒度独立原则**：dimensions 和 timeConstraint 可以使用不同的时间粒度。例如 dimensions 用 `metric_time__day` 展示每日数据，timeConstraint 用 `([metric_time__month]="2024-03")` 限定只查 3 月

### 同环比场景的单值筛选约束

当 metrics 中使用同环比快速计算（`__sameperiod__`）时，timeConstraint 有额外硬约束：

1. **metric_time 必须被使用**：metric_time 维度必须在 dimensions 或 timeConstraint 中被使用
2. **若在 timeConstraint 中，必须是单值筛选**：引擎需要确定**唯一的时间锚点**来偏移。范围筛选（如 `([metric_time__day]>="2024-01-01" AND ...)`）不满足单值要求
3. **偏移粒度不可小于日期粒度**：如同环比偏移为 `yoy`（年），dimensions 中的 metric_time 粒度不可大于年；月粒度数据下不可用日环比 `dod`

**单值筛选示例**（✓）：`([metric_time__month]="2025-04")`——唯一月份锚点
**非单值示例**（✗）：`([metric_time__day]>="2025-04-01" AND [metric_time__day]<="2025-04-30")`——范围，无法确定唯一锚点

> 同环比若需单值锚点又想展示多日数据，可把单值筛选放 timeConstraint，多日粒度放 dimensions（粒度独立原则）。

### 正确 vs 错误对照

| 正确 | 错误 | 说明 |
|------|------|------|
| `(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))` | `DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\")` | 缺少外层括号 |
| `([metric_time__day]>=DateAdd(Today(),-7,\"DAY\"))` | `[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")` | 缺少外层括号 |
| `([metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\")` | `([metric_time__day] between \"2024-01-01\" and \"2024-01-31\")` | 不支持 BETWEEN，用 AND 连接 |
| `([metric_time__month]=\"2024-03\")` | `([some_other_date]=\"2024-03\")` | timeConstraint 仅支持 metric_time，业务日期用 filters |

---

## filters 详解

filters 对全部指标进行维度过滤，格式为字符串数组。

### 文本维度

```
"filters": ["[region] IN (\"华东\",\"华南\")"]
```

### 数值维度

```
"filters": ["[product_id] IN (13,18)"]
```

### 日期维度

```
"filters": ["[metric_time__day]>=\"2024-01-01\""]
```

### 组合过滤

```
"filters": [
    "[region] IN (\"华东\",\"华南\")",
    "[product_id] IN (13,18)"
]
```

### 运算符

| 运算符 | 适用类型 | 示例 |
|--------|---------|------|
| `IN` | 文本、数值 | `[region] IN ("华东","华南")` |
| `NOT IN` | 文本、数值 | `[region] NOT IN ("西北")` |
| `=` | 文本、数值、日期 | `[metric_time__month]="2024-03"` |
| `!=` | 文本、数值 | `[status]!="已取消"` |
| `>` / `>=` | 数值、日期 | `[amount]>=1000` |
| `<` / `<=` | 数值、日期 | `[amount]<=50000` |
| `LIKE` | 文本 | `[name] LIKE "%手机%"` |

---

## resultFilters 详解

resultFilters 对查询结果进行二次过滤，可以筛选指标值或维度值。格式与 filters 类似，但作用于查询结果而非查询过程。

---

## metricDefinitions 详解

用于定义临时指标，与快速计算语法糖等价但更灵活。当快速计算语法无法满足需求时使用。

### 结构

```json
{
    "临时指标名": {
        "refMetric": "基准指标名",
        "specifyDimension": {
            "type": "INCLUDE 或 EXCLUDE",
            "dimensions": "维度列表（逗号分隔）"
        }
    }
}
```

### 字段说明

| 字段 | 说明 |
|------|------|
| `refMetric` | 引用的基准指标英文名 |
| `specifyDimension.type` | `INCLUDE`（限定维度范围）或 `EXCLUDE`（排除维度） |
| `specifyDimension.dimensions` | 维度列表，逗号分隔 |

### 示例

```json
{
    "orderCount_total": {
        "refMetric": "orderCount",
        "specifyDimension": {
            "type": "INCLUDE",
            "dimensions": "metric_time__day,province"
        }
    }
}
```

---

## orders 详解

排序字段，数组中的每个元素是一个键值对，键为维度名或指标名，值为排序方向。

```json
[
  {"sales_amount": "desc"},
  {"region": "asc"}
]
```

- 键：排序字段名，可以是指标名或维度名
- 值：`asc`（升序）或 `desc`（降序）

对快速计算产生的衍生指标同样适用，如 `"sales_amount__sameperiod__yoy__growth"`。

---

## specialMvConfig 详解

用于配置查询时是否启用指定物化表加速，以及如何处理物化表未命中的情况。

---

## 响应参数

| 参数 | 必填 | 描述 | 类型 |
|------|------|------|------|
| `data` | 必填 | 查询返回的数据内容 | Object |
| `success` | 必填 | 查询状态，true=成功，false=失败 | Boolean |
| `code` | 必填 | 接口响应码 | String |
| `message` | 选填 | 报错信息 | String |
| `traceId` | 必填 | 本次请求的追踪ID | String |

### data 结构

| 字段 | 描述 |
|------|------|
| `queryId` | 查询ID |
| `sql` | 查询SQL（当 queryResultType 包含 SQL 时返回） |
| `table.columns` | 查询结果数据，key 为列名，value 为值数组 |
| `metas` | 列元信息，包含 name、dataTypeName 等 |

### 响应示例

```json
{
    "data": {
        "queryId": "f8d14bba-918a-410b-b01c-87233fc70940",
        "sql": "SELECT ...",
        "table": {
            "columns": {
                "metric_time__day": [
                    {"value": "2022-09-09 00:00:00", "flag": null, "count": 1}
                ],
                "flOrderCount": [
                    {"value": 100, "flag": null, "count": 1}
                ]
            }
        },
        "metas": [
            {"name": "metric_time__day", "dataTypeName": "DATETIME"},
            {"name": "flOrderCount", "dataTypeName": "BIGINT"}
        ]
    },
    "success": true,
    "code": null,
    "message": null,
    "traceId": "add22b18f1524199941fc83ce26dda33.576.16940932763920003"
}
```

---

## 指标归因分析 API

归因分析用于解释**指标为什么变化**——在两个时间点之间，各维度值对指标变化的贡献率是多少。共 5 个 endpoint，构成"校验 → 多维归因 → 下钻归因"的流程，外加指标树归因（指标拆解关系）。

### 归因流程

```
attribution_check（校验指标能否归因）
  → attribution_multi_dim（多维归因，看各维度贡献率）
  → attribution_drilldown（对主因维度下钻，看具体维度值贡献）
```

指标树归因是独立路径：直接调 `attribution_tree`（传入 `metricTree` + `metrics` + `attributionRange`），按指标的拆解树归因各因子。

### attribution_check — 归因校验

**用途**：校验指标是否支持归因分析（先决条件，避免直接调归因查询报错）。

**请求参数**（BODY）：

| 参数 | 必填 | 描述 |
|------|------|------|
| `analysisRange` | 是 | 归因分析范围，结构见下方 |
| `metric` | 是 | 指标英文名 |
| `dimensions` | 是 | 分析维度英文名列表 |
| `timeConstraint` | 否 | 时间筛选器表达式 |
| `filters` | 否 | 筛选器 |
| `displayFilters` | 否 | 结果筛选器（having 语义） |
| `limit` | 否 | 返回行数，默认 200 |

**analysisRange 结构**：

```json
{
  "current": "2024-05-11",
  "comparison": "2024-05-10"
}
```

- `current`：当前分析日期（字符串，如 `"2024-05-11"`）
- `comparison`：对比分析日期（同格式）

**响应**：`data` 为布尔数组，`true` 表示对应指标+维度组合可归因，`false` 表示不可归因。

### attribution_multi_dim — 多维归因

**用途**：对指标在多个维度上做归因，返回各维度的贡献率（哪个维度拉动了指标变化）。

**请求参数**（BODY）：

| 参数 | 必填 | 描述 |
|------|------|------|
| `attributionRange` | 是 | 归因范围，结构见下方 |
| `metric` | 是 | 指标英文名 |
| `dimensions` | 是 | 分析维度英文名列表（多维度同时归因） |
| `filters` | 否 | 筛选器 |
| `orders` | 否 | 排序，默认按 contributionRate 倒排。格式 `[{"column":"overallContributionRate","type":"DESC"}]`，可排序字段：currentValue/comparisonValue/growth/growthRate/contributionRate |
| `limit` | 否 | 返回行数，默认 200 |

**attributionRange 结构**：

```json
{
  "granularity": "DAY",
  "comparisonType": "MOM",
  "currentFilter": {"type": "EXPR", "expr": "DateTrunc([metric_time],\"DAY\")=\"2025-07-07\""},
  "startDateTime": "2025-07-01",
  "endDateTime": "2025-07-06"
}
```

- `granularity`：时间粒度，`DAY`/`WEEK`/`MONTH`/`QUARTER`/`YEAR`
- `comparisonType`：对比类型，`CUSTOM`/`DOD`/`YOY`/`MOM`/`QOQ`/`WOW`
- `currentFilter`：当前时间筛选对象（type=EXPR, expr=时间表达式）
- `startDateTime`/`endDateTime`：当 comparisonType=CUSTOM 时指定对比区间

**响应 data 结构**：

```json
{
  "metric": "sales_amount",
  "all": {
    "currentValue": 10000, "comparisonValue": 8000,
    "growth": 2000, "growthRate": 0.25,
    "overallContributionRate": 1.0, "relativeContributionRate": 1.0
  },
  "dimensions": {
    "region": {
      "dimensionValue": ["华东","华南","华北"],
      "currentValue": [5000,3000,2000],
      "comparisonValue": [4000,2500,1500],
      "growth": [1000,500,500],
      "growthRate": [0.25,0.2,0.33],
      "contributionRate": [0.5,0.25,0.25],
      "overallContributionRate": [0.5,0.25,0.25],
      "relativeContributionRate": [0.5,0.25,0.25]
    }
  }
}
```

- `all`：整体变化概要（当前值/对比值/增长值/增长率/贡献率）
- `dimensions`：各维度归因详情，key 为维度名，value 中所有列表**按维度值对齐**（dimensionValue[i] 对应 currentValue[i]、contributionRate[i] 等）
- `contributionRate`：贡献率，正值表示该维度值推动指标同向变化，负值表示反向

### attribution_drilldown — 下钻归因

**用途**：对**单一维度**细查变动原因（通常在 multi_dim 找出主因维度后，对其下钻）。

**请求参数**与 multi_dim 的差异：

| 参数 | 描述 |
|------|------|
| `dimension`（单数） | 下钻维度英文名，**单个**（multi_dim 是 `dimensions` 复数） |
| `filters` | 可追加 `drillFilters`（下钻维度值筛选） |

其余参数（attributionRange、metric、orders、limit）与 multi_dim 一致。响应结构同 multi_dim。

### attribution_tree — 指标树归因

**用途**：按指标拆解树做归因（如总销售额 = 销售额 + 退货额，分别归因各因子的贡献）。

**请求参数**（BODY）：

| 参数 | 必填 | 描述 |
|------|------|------|
| `metricTree` | 是 | 指标树层级关系，key 为节点名，value 含 expr 和 refId |
| `metrics` | 是 | 指标定义详情，key 为指标 ID，value 含 id 和 code |
| `attributionRange` | 是 | 归因范围，结构同 [attribution_multi_dim 的 attributionRange](#attribution_multi_dim--多维归因) |
| `filters` | 否 | 筛选器 |

**请求示例**：

```json
{
  "metricTree": {
    "RootNode": {"expr": "[MetricA]+[MetricB]", "refId": "METRIC_001"},
    "MetricA": {"expr": "", "refId": "METRIC_002"},
    "MetricB": {"expr": "", "refId": "METRIC_003"}
  },
  "metrics": {
    "METRIC_001": {"id": "METRIC_001", "code": "TotalSales"},
    "METRIC_002": {"id": "METRIC_002", "code": "Sales"},
    "METRIC_003": {"id": "METRIC_003", "code": "Returns"}
  },
  "filters": ["IN(['dim'],\"a\")"],
  "attributionRange": {
    "granularity": "DAY",
    "comparisonType": "DOD",
    "currentFilter": {"type": "EXPR", "expr": "DateTrunc([metric_time], 'DAY') = '2024-11-30'"}
  }
}
```

**响应 data**：含 `metricTree`（树结构）、`metrics`（各节点指标值）、`all`（整体变化）、`dimensions`（各节点归因）。
