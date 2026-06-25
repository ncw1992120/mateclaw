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
| `dimensions` | 选填 | 查询维度，支持已定义维度，日期维度支持粒度切换 | Array[String]       |
| `filters` | 选填 | 全局筛选，对全部指标进行维度过滤 | Array[String]       |
| `specialMvConfig` | 选填 | 物化表加速配置，控制是否启用指定物化表加速及未命中处理 | Map                 |
| `resultFilters` | 选填 | 结果筛选，对查询结果进行二次过滤（按指标值或维度值） | Array[String]       |
| `timeConstraint` | 选填 | 指标日期范围 | String              |
| `orders` | 选填 | 排序，排序字段需包含在 metrics 或 dimensions 中 | Array[Map]          |
| `limit` | 选填 | 返回条数，默认100 | int                 |
| `offset` | 选填 | 偏移量，默认1（如 offset=100 时从第100条开始返回） | int                 |
| `queryResultType` | 选填 | 返回内容类型：`SQL_AND_DATA`（默认，同时返回数据和SQL）/ `SQL`（仅SQL）/ `DATA`（仅数据） | String              |
| `source` | 选填 | 查询来源标识（自定义参数） | String              |
| `isQueryTotalCount` | 选填 | 是否返回数据总条数 | Boolean             |

---

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

timeConstraint 用于指定指标日期范围，使用表达式语法。

### 常用表达式速查

| 场景 | 表达式 |
|------|--------|
| 当月 | `DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\")` |
| 上月 | `DateTrunc([metric_time],\"MONTH\")=DateAdd(DateTrunc(Today(),\"MONTH\"),-1,\"MONTH\")` |
| 近7天 | `[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")` |
| 近30天 | `[metric_time__day]>=DateAdd(Today(),-30,\"DAY\")` |
| 今年 | `DateTrunc([metric_time],\"YEAR\")=DateTrunc(Today(),\"YEAR\")` |
| 指定日期范围 | `[metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\"` |
| 指定月份 | `[metric_time__month]=\"2024-03\"` |
| 今天 | `[metric_time__day]=Today()` |

### 表达式函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `Today()` | 当前日期 | `Today()` |
| `DateAdd(date, N, unit)` | 日期偏移 | `DateAdd(Today(),-7,"DAY")` |
| `DateTrunc(date, unit)` | 日期截断到指定粒度 | `DateTrunc(Today(),"MONTH")` |

unit 取值：`DAY` / `MONTH` / `YEAR`

### 维度引用

在表达式中使用方括号引用时间维度：

- `[metric_time]` — 基础时间维度
- `[metric_time__day]` — 日粒度
- `[metric_time__month]` — 月粒度
- `[metric_time__year]` — 年粒度

### 与 dimensions 的配合

- 在 dimensions 中指定时间粒度（如 `metric_time__month`）
- 在 timeConstraint 中指定时间范围
- 两者配合使用，不要在 timeConstraint 中重复指定粒度

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

排序字段，格式为对象。

```json
[
  {
    "fieldName": "direction"
  }
]
```

- `fieldName`：排序字段名，可以是指标名或维度名
- `direction`：`asc`（升序）或 `desc`（降序）

对快速计算产生的衍生指标同样适用，如 `"fieldName": "sales_amount__sameperiod__yoy__growth"`。

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
