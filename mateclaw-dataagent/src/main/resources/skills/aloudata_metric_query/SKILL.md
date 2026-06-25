---
name: aloudata_metric_query
version: "3.0.0"
description: "通过 Aloudata 指标平台查询指标数据。支持业务术语理解、语义检索指标和维度、构造指标查询请求、获取指标列表和维度列表。"
dependencies:
  tools:
    - search_business_term
    - aloudata_search_semantic
    - aloudata_metrics_list
    - aloudata_dimensions_list
    - aloudata_metric_available_dimensions
    - aloudata_metrics_query
references:
  - references/api-doc.md
  - references/best-practices.md
---

# Aloudata 指标查询技能

## 适用场景

用户需要查询指标数据、了解指标口径、查看维度信息，或需要基于指标平台进行数据分析。

**指标平台 vs 数据库的选择**：
- 指标平台（aloudata_* 工具）：查询已建模的指标数据，指标口径已统一定义
- 数据库（data_query 工具）：查询原始业务数据，或指标平台未覆盖的数据

## 工作流程

### 第一步：理解业务术语（可选但推荐）

当用户提问涉及业务术语、缩写或别名时，先通过 `search_business_term(tenantCode, keyword)` 查询术语的标准名称、定义和同义词。

**为什么需要这一步**：
- 用户可能说"营收"，但指标平台的标准指标名是"销售额"
- 同义词映射帮助后续语义检索更精准

### 第二步：语义检索指标和维度

调用 `aloudata_search_semantic(datasourceId, keyword)` 检索相关的指标和维度。

**这一步是关键**：直接返回精确的 metricName 和 dimName，无需猜测。如果第一步查询了业务术语，可将术语名和同义词作为关键词传入以提高召回率。

### 第三步：确认可用维度

根据第二步返回的指标，检查其 `availableDimensions` 字段。如需确认更多维度，调用 `aloudata_metric_available_dimensions(metricNames)`。

### 第四步：构造查询请求

使用第二步得到的 metricName/dimName 构造 `aloudata_metrics_query` 请求。

**基本参数**：
- `metrics`（必填）：指标英文名列表，如 `["sales_amount"]`。支持快速计算语法（同环比、占比、排名等）
- `dimensions`（选填）：维度英文名列表，如 `["region", "metric_time__month"]`
- `timeConstraint`（选填）：指标日期范围，如 `"DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\")"`
- `filters`（选填）：全局筛选，如 `["[region] IN (\"华东\",\"华南\")"]`
- `orders`（选填）：排序
- `limit`（选填）：返回条数，默认100

> 详细参数说明请参考 [references/api-doc.md](references/api-doc.md)
> 查询示例请参考 [references/best-practices.md](references/best-practices.md) 中的案例

### 第五步：输出自检

1. 确认 metrics 使用 metricName（英文名），不是展示名
2. 确认 dimensions 使用 dimName（英文名），不是展示名
3. 确认 timeConstraint 格式正确
4. 确认使用的维度在指标的 availableDimensions 列表中

### 第六步：解读结果

根据查询结果向用户解读数据趋势、异常值和维度拆解差异。如需更复杂分析，将数据传给 `python_analysis` 工具。

## 重要提示

1. **理解业务术语再检索**：当用户使用业务术语、缩写或别名时，先通过 search_business_term 确认含义
2. **必须先检索再查询**：不要直接构造 metrics_query 请求，必须先通过 aloudata_search_semantic 获取精确的 metricName 和 dimName
3. **使用英文名构造请求**：metrics 和 dimensions 必须使用英文名，不要使用中文展示名
4. **同环比使用约束**：metric_time 必须在 dimensions 或 timeConstraint 中；timeConstraint 中须为单值筛选；偏移粒度不可小于日期粒度
5. **占比/排名维度约束**：使用的占比/排名范围维度必须在 dimensions 中声明
6. **与 Python 分析协作**：如需复杂处理，将数据传入 python_analysis 工具
