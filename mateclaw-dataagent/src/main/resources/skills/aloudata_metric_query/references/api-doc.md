# Aloudata 指标查询 API 参数参考

## 请求参数总览

| 参数 | 必填 | 说明 |
|------|------|------|
| `metrics` | 是 | 指标英文名列表，如 `["sales_amount"]`。支持直接引用和快速计算语法 |
| `dimensions` | 否 | 维度英文名列表，如 `["region", "metric_time__month"]`。日期维度支持粒度切换 |
| `filters` | 否 | 全局筛选条件，对全部指标生效 |
| `resultFilters` | 否 | 结果筛选，对查询结果进行二次过滤（按指标值或维度值） |
| `timeConstraint` | 否 | 指标日期范围 |
| `metricDefinitions` | 否 | 临时指标定义，用于同环比、占比、排名等衍生计算 |
| `orders` | 否 | 排序，格式 `{"fieldName": "sales_amount", "direction": "DESC"}` |
| `limit` | 否 | 返回条数，默认 100 |
| `offset` | 否 | 偏移量，默认 1 |

---

## metrics 快速计算语法

在 metrics 中可以使用语法糖直接定义衍生指标，无需通过 metricDefinitions。

### 同环比

语法：`{指标名}__sameperiod__{偏移粒度}__{同环比方法}`

如需指定日期标识：`{指标名}__sameperiod__{偏移粒度}__{日期标识}__{同环比方法}`

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
| `{N}_{日历名}_{粒度}` | 自定义日历 | `-2_FY_mom`（2个财年月前） |

#### 日期标识

可选，用于区分工作日、交易日等。插入在偏移粒度和同环比方法之间。

示例：`order_count__sameperiod__-2_dod__workdays__growth`

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
```

### 占比

语法：`{指标名}__proportion__{占比范围维度}`

占比维度必须在 dimensions 中声明。省略占比维度则为全局占比。

```
"sales_amount__proportion__region"   // 区域内占比
"sales_amount__proportion"           // 全局占比
```

### 排名

语法：`{指标名}__rank__{排名范围维度}`

排名维度必须在 dimensions 中声明。省略排名维度则为全局排名。

```
"sales_amount__rank__region"   // 区域内排名
"sales_amount__rank"           // 全局排名
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
    "sales_amount_yoy": {
        "refMetric": "sales_amount",
        "specifyDimension": {
            "type": "INCLUDE",
            "dimensions": "metric_time__day,region"
        }
    }
}
```

---

## orders 详解

排序字段，格式为对象。

```json
{
    "fieldName": "sales_amount",
    "direction": "DESC"
}
```

- `fieldName`：排序字段名，可以是指标名或维度名
- `direction`：`ASC`（升序）或 `DESC`（降序）

对快速计算产生的衍生指标同样适用，如 `"fieldName": "sales_amount__sameperiod__yoy__growth"`。
