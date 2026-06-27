# Aloudata 指标查询最佳实践

## 常见错误与规避

### 1. 使用展示名而非英文名

**错误**：
```json
{
    "metrics": ["销售额"],
    "dimensions": ["区域"]
}
```

**正确**：
```json
{
    "metrics": ["sales_amount"],
    "dimensions": ["region", "metric_time__month"]
}
```

> 必须先通过 aloudata_search_semantic 获取 metricName/dimName（英文名），再构造请求。

### 2. 同环比缺少时间维度

**错误**：使用同环比但 dimensions 中没有 metric_time
```json
{
    "metrics": ["sales_amount", "sales_amount__sameperiod__yoy__growth"],
    "dimensions": ["region"]
}
```

**正确**：添加 metric_time 维度
```json
{
    "metrics": ["sales_amount", "sales_amount__sameperiod__yoy__growth"],
    "dimensions": ["region", "metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
}
```

### 3. 占比/排名维度未在 dimensions 中声明

**错误**：占比维度 region 未在 dimensions 中
```json
{
    "metrics": ["sales_amount__proportion__region"],
    "dimensions": ["metric_time__month"]
}
```

**正确**：region 必须出现在 dimensions 中
```json
{
    "metrics": ["sales_amount__proportion__region"],
    "dimensions": ["region", "metric_time__month"]
}
```

### 4. timeConstraint 格式错误

**错误**：直接用日期字符串
```json
{
    "timeConstraint": "2024-01-01/2024-01-31"
}
```

**正确**：使用表达式语法
```json
{
    "timeConstraint": "([metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\")"
}
```

### 5. 同环比偏移粒度小于日期粒度

**错误**：月粒度下使用日环比
```json
{
    "metrics": ["sales_amount__sameperiod__dod__growth"],
    "dimensions": ["metric_time__month"]
}
```

**正确**：月粒度用月环比
```json
{
    "metrics": ["sales_amount__sameperiod__mom__growth"],
    "dimensions": ["metric_time__month"]
}
```

---

## 典型查询模式

### 模式1：当月汇总

查询当月某指标的总值。

```json
{
    "metrics": ["sales_amount"],
    "dimensions": ["metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
}
```

### 模式2：按维度拆解

查询当月各区域的指标值。

```json
{
    "metrics": ["sales_amount"],
    "dimensions": ["region", "metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
}
```

### 模式3：同环比对比

查询当月指标值及同比、环比增长率。

```json
{
    "metrics": [
        "sales_amount",
        "sales_amount__sameperiod__yoy__growth",
        "sales_amount__sameperiod__mom__growth"
    ],
    "dimensions": ["metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
}
```

### 模式4：维度拆解 + 同环比 + 排名

查询各城市销售额及同比变化，并按排名排序。

```json
{
    "metrics": [
        "sales_amount",
        "sales_amount__sameperiod__yoy__growth",
        "sales_amount__rank"
    ],
    "dimensions": ["province", "city", "metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))",
    "orders": [{"sales_amount__rank": "asc"}]
}
```

### 模式5：指定时间范围 + 筛选

查询指定日期范围内、特定区域的指标趋势。

```json
{
    "metrics": ["sales_amount"],
    "dimensions": ["metric_time__day", "region"],
    "filters": ["[region] IN (\"华东\",\"华南\")"],
    "timeConstraint": "([metric_time__day]>=\"2024-01-01\" AND [metric_time__day]<=\"2024-01-31\")",
    "orders": [{"metric_time__day": "asc"}]
}
```

### 模式6：日环比 + 占比

查询今日订单量的日环比，以及各渠道占比。

```json
{
    "metrics": [
        "order_count",
        "order_count__sameperiod__dod__growth",
        "order_count__proportion__channel"
    ],
    "dimensions": ["channel", "metric_time__day"],
    "timeConstraint": "([metric_time__day]=Today())"
}
```

### 模式7：时间限定衍生指标

对比当月累计值和近7日值。

```json
{
    "metrics": [
        "sales_amount",
        "sales_amount__timefilter__[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")"
    ],
    "dimensions": ["metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"
}
```

### 模式8：使用 metricDefinitions 定义临时指标

查询订单量及其在指定维度范围内的值。

```json
{
    "metrics": ["orderCount", "orderCount_total"],
    "metricDefinitions": {
        "orderCount_total": {
            "refMetric": "orderCount",
            "specifyDimension": {
                "type": "INCLUDE",
                "dimensions": "metric_time__day,province"
            }
        }
    },
    "dimensions": ["metric_time__day", "province", "city"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))",
    "limit": 30
}
```

### 模式9：指定日期标识的同环比

查询工作日环比。

```json
{
    "metrics": [
        "order_count",
        "order_count__sameperiod__dod__workdays__growth"
    ],
    "dimensions": ["metric_time__day"],
    "timeConstraint": "([metric_time__day]=Today())"
}
```

### 模式10：使用 resultFilters 结果筛选

查询销售额排名前10的城市。

```json
{
    "metrics": [
        "sales_amount",
        "sales_amount__rank"
    ],
    "dimensions": ["city", "metric_time__month"],
    "timeConstraint": "(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))",
    "resultFilters": ["[sales_amount__rank]<=10"],
    "orders": [{"sales_amount__rank": "asc"}]
}
```

---

## 跨工具协作模式

### 与 search_business_term 协作

当用户使用业务术语（非标准指标名）时：

1. `search_business_term(keyword)` → 获取术语定义和同义词（跨所有业务域检索）
2. 用术语名和同义词作为 aloudata_search_semantic 的关键词
3. 提高指标检索的召回率和准确性

**示例**：用户说"营收"
- search_business_term 返回：`营收（同义词: 收入, 营业收入）`
- aloudata_search_semantic(keyword="营收") → 匹配到 `sales_amount(销售额)` 指标

### 与 python_analysis 协作

当查询结果需要复杂分析（统计、机器学习、可视化）时：

1. `aloudata_metrics_query` → 获取指标数据
2. 将查询结果作为 input 传入 `python_analysis(execute_python)`
3. Python 执行复杂分析并输出结果

### 与 data_query 协作

当需要联合指标平台和数据库数据时：

1. `aloudata_metrics_query` → 获取指标平台数据
2. `data_query(execute_sql)` → 获取数据库原始数据
3. `python_analysis(execute_python)` → 两份数据对比分析

---

## 构造请求的检查清单

在提交 aloudata_metrics_query 前，逐项确认：

- [ ] metrics 中的指标名是否为英文名（metricName）？是否来自 aloudata_search_semantic 返回？
- [ ] dimensions 中的维度名是否为英文名（dimName）？是否在 availableDimensions 中？
- [ ] timeConstraint 表达式是否使用了正确的语法（整个表达式用 `()` 包裹、方括号引用维度、双引号转义）？
- [ ] 使用同环比时，metric_time 是否在 dimensions 或 timeConstraint 中？
- [ ] 同环比的偏移粒度是否大于等于日期粒度？
- [ ] 占比/排名的范围维度是否在 dimensions 中声明？
- [ ] filters 中的维度引用是否使用方括号？字符串值是否用双引号？
- [ ] orders 中的 fieldName 是否包含在 metrics 或 dimensions 中？
