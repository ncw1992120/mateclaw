---
name: aloudata_metric_query
version: "1.0.0"
description: "通过 Aloudata 指标平台查询指标数据，支持语义检索、同环比/占比/排名快速计算、时间限定、结果筛选等。当用户需要查询指标数据、查看维度信息、进行指标分析时调用。"
dependencies:
  tools:
    - search_business_term
    - aloudata_search_semantic
    - aloudata_metric_list
    - aloudata_dimension_list
    - aloudata_metric_available_dimensions
    - aloudata_dimension_values
    - aloudata_metrics_query
references:
  - references/api-doc.md
  - references/best-practices.md
scripts:
  - scripts/format_query_result.py
  - scripts/extract_data.py
  - scripts/validate_query_request.py
templates:
  - templates/query-request-template.json
  - templates/report-template.md
---

# Aloudata 指标查询技能

## 适用场景

用户需要查询指标数据、了解指标口径、查看维度信息，或需要基于指标平台进行数据分析。

**指标平台 vs 数据库的选择**：
- 指标平台（aloudata_* 工具）：查询已建模的指标数据，指标口径已统一定义
- 数据库（data_query 工具）：查询原始业务数据，或指标平台未覆盖的数据

## 工作流程

### 第一步：理解业务术语（可选但推荐）

当用户提问涉及业务术语、缩写或别名时，先通过 `search_business_term(keyword)` 查询术语的标准名称、定义和同义词。检索跨所有业务域进行，无需指定租户。

**为什么需要这一步**：
- 用户可能说"营收"，但指标平台的标准指标名是"销售额"
- 同义词映射帮助后续语义检索更精准

### 第二步：语义检索指标和维度

调用 `aloudata_search_semantic(datasourceId, keyword)` 检索相关的指标和维度。

**这一步是关键**：直接返回精确的 metricName 和 dimName，无需猜测。如果第一步查询了业务术语，可将术语名和同义词作为关键词传入以提高召回率。

### 第三步：确认可用维度

根据第二步返回的指标，检查其 `availableDimensions` 字段。如需确认更多维度，调用 `aloudata_metric_available_dimensions(metricNames)`。

### 第四步：构造查询请求

使用第二步得到的 metricName/dimName 构造 `aloudata_metrics_query` 请求。可参考 [templates/query-request-template.json](templates/query-request-template.json) 中的请求模板。

**基本参数**：
- `metrics`（必填）：指标英文名列表，如 `["sales_amount"]`。支持快速计算语法（同环比、占比、排名、时间限定）
- `dimensions`（选填）：维度英文名列表，如 `["region", "metric_time__month"]`。日期维度支持粒度切换（`metric_time__day`/`metric_time__month`/`metric_time__year`）
- `timeConstraint`（选填）：指标日期范围，表达式语法。**必须用 `()` 包裹整个表达式**。如 `"(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"`
- `filters`（选填）：全局筛选，对全部指标生效，如 `["[region] IN (\"华东\",\"华南\")"]`
- `resultFilters`（选填）：结果筛选，对查询结果进行二次过滤
- `metricDefinitions`（选填）：临时指标定义，用于 specifyDimension 等复杂衍生
- `orders`（选填）：排序，格式 `[{"fieldName": "direction"}]`。fieldName：字段名称，direction：asc或者desc
- `limit`（选填）：返回条数，默认100
- `offset`（选填）：偏移量，默认1
- `queryResultType`（选填）：返回内容类型，`SQL_AND_DATA`（默认）/`SQL`/`DATA`
- `source`（选填）：查询来源标识
- `isQueryTotalCount`（选填）：是否返回数据总条数
- `specialMvConfig`（选填）：物化表加速配置

> 详细参数说明请参考 [references/api-doc.md](references/api-doc.md)
> 查询示例请参考 [references/best-practices.md](references/best-practices.md) 中的案例

### 第五步：输出自检

提交查询前，逐项确认以下检查项。也可使用 [scripts/validate_query_request.py](scripts/validate_query_request.py) 进行自动化校验：

1. 确认 metrics 使用 metricName（英文名），不是展示名
2. 确认 dimensions 使用 dimName（英文名），不是展示名
3. 确认 timeConstraint 格式正确（整个表达式用 `()` 包裹，使用方括号引用维度、双引号转义）
4. 确认使用的维度在指标的 availableDimensions 列表中
5. 确认同环比偏移粒度不小于日期粒度
6. 确认占比/排名的范围维度在 dimensions 中声明
7. 确认 filters 中的维度引用使用方括号，字符串值使用双引号
8. 确认 orders 中的 fieldName 包含在 metrics 或 dimensions 中

### 第六步：解读结果

根据查询结果向用户解读数据趋势、异常值和维度拆解差异。

**结果处理工具**：
- 使用 [scripts/format_query_result.py](scripts/format_query_result.py) 将原始 JSON 响应格式化为 Markdown 表格，便于阅读
- 使用 [scripts/extract_data.py](scripts/extract_data.py) 将原始 JSON 响应提取为结构化数据，便于传入 `python_analysis` 工具进行深度分析
- 使用 [templates/report-template.md](templates/report-template.md) 生成标准化的指标分析报告

如需更复杂分析，将提取后的数据传给 `python_analysis` 工具。

## 快速计算语法速查

### 同环比

语法：`{指标名}__sameperiod__{偏移粒度}__{同环比方法}`

如需日期标识：`{指标名}__sameperiod__{偏移粒度}__{日期标识}__{同环比方法}`

**偏移粒度**：`dod`(日) / `wow`(周) / `mom`(月) / `qoq`(季) / `yoy`(年)，支持 `{N}_` 前缀指定偏移量，如 `-2_dod`、`-52_wow`

**同环比方法**：`value`(对比值) / `growthvalue`(增长值) / `growth`(增长率) / `decrease`(下降值) / `decreaserate`(下降率)

**约束**：
- metric_time 必须在 dimensions 或 timeConstraint 中
- timeConstraint 中须为单值筛选
- 偏移粒度不可小于日期粒度

### 占比

语法：`{指标名}__proportion__{占比范围维度}`

省略占比维度则为全局占比。占比维度必须在 dimensions 中声明。

### 排名

语法：`{指标名}__rank__{排名范围维度}`

省略排名维度则为全局排名。排名维度必须在 dimensions 中声明。

### 时间限定

语法：`{指标名}__timefilter__{时间范围表达式}`

为指标添加时间限定，如：`sales_amount__timefilter__[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")`

## 重要提示

1. **理解业务术语再检索**：当用户使用业务术语、缩写或别名时，先通过 search_business_term 确认含义
2. **必须先检索再查询**：不要直接构造 metrics_query 请求，必须先通过 aloudata_search_semantic 获取精确的 metricName 和 dimName
3. **使用英文名构造请求**：metrics 和 dimensions 必须使用英文名，不要使用中文展示名
4. **同环比使用约束**：metric_time 必须在 dimensions 或 timeConstraint 中；timeConstraint 中须为单值筛选；偏移粒度不可小于日期粒度
5. **占比/排名维度约束**：使用的占比/排名范围维度必须在 dimensions 中声明
6. **与 Python 分析协作**：如需复杂处理，将数据传入 python_analysis 工具
7. **使用脚本辅助**：利用 scripts/ 下的校验、格式化、提取脚本提高效率和准确性
