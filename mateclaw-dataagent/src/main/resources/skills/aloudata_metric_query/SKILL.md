---
name: aloudata_metric_query
version: "2.0.0"
description: "通过 Aloudata 指标平台查询指标数据。支持语义检索指标和维度、构造指标查询请求、获取指标列表和维度列表。"
dependencies:
  tools:
    - aloudata_search_semantic
    - aloudata_metrics_list
    - aloudata_dimensions_list
    - aloudata_metric_available_dimensions
    - aloudata_metrics_query
---

# Aloudata 指标查询技能

## 适用场景

用户需要查询指标数据、了解指标口径、查看维度信息，或需要基于指标平台进行数据分析。

## 工作流程

### 第一步：语义检索指标和维度

调用 `aloudata_search_semantic(datasourceId, keyword)` 检索相关的指标和维度。

**这一步是关键**：直接返回精确的 metricName 和 dimName，无需猜测指标名。

返回格式包含：
- **指标命中**：metricName(展示名) [类型] - 口径, 同义词: ..., 可用维度: ...
- **维度命中**：dimName(展示名) [数据类型] - 描述, 同义词: ..., 示例值: ...

**检索策略**：
- 关键词检索：匹配 metricName、metricDisplayName、businessCaliber、synonyms
- 向量语义检索：理解自然语言含义（如"营收"匹配到"销售额"指标）
- 混合检索：关键词 + 向量 + RRF 融合，确保准确性和召回率

### 第二步：确认可用维度

根据第一步返回的指标，检查其 `availableDimensions` 字段：
- 如果用户需要的维度在列表中 → 直接使用 dimName
- 如果需要确认或查看更多维度 → 调用 `aloudata_metric_available_dimensions(metricNames)`

**常见维度类型**：
- 时间维度：`metric_time__day`、`metric_time__month`、`metric_time__year` — 用于时间范围和时间对比
- 业务维度：如 `region`(区域)、`channel`(渠道)、`category`(类目) — 用于数据拆解
- 衍生维度：如 `metric_time__week`(周)、`metric_time__quarter`(季度) — 按需使用

### 第三步：构造查询请求

使用第一步得到的 metricName/dimName 构造 `aloudata_metrics_query` 请求。

**请求参数说明**：
- `metrics`：指标英文名列表（如 `["sales_amount"]`），**必须使用第一步返回的 metricName**
- `dimensions`：维度英文名列表（如 `["region", "metric_time__month"]`），**必须使用第一步返回的 dimName**
- `filters`：筛选条件数组，格式 `[{"dimName": "region", "operator": "IN", "values": ["华东", "华南"]}]`
- `timeConstraint`：时间约束（如 `"2024-01-01/2024-01-31"`），时间维度名通常是 `metric_time__day` 或 `metric_time__month`
- `metricDefinitions`：快速计算定义，用于环比、同比、占比等衍生指标
- `orderBy`：排序字段，格式 `[{"fieldName": "sales_amount", "direction": "DESC"}]`
- `limit` / `offset`：分页参数

**快速计算(metricDefinitions)示例**：
- 环比增长率：`{"refMetric": "sales_amount", "specifyDimension": "metric_time__month"}`
- 同比增长率：`{"refMetric": "sales_amount", "specifyDimension": "metric_time__year"}`
- 占比：`{"refMetric": "sales_amount", "specifyDimension": "region"}`

### 第四步：输出自检

在展示结果前，进行自检：
1. **指标名验证**：确认 metrics 参数使用的是 metricName（英文名），不是展示名
2. **维度名验证**：确认 dimensions 参数使用的是 dimName（英文名），不是展示名
3. **时间范围**：确认 timeConstraint 格式正确，时间维度名正确
4. **维度可用性**：确认使用的维度在指标的 availableDimensions 列表中

### 第五步：解读结果

根据查询结果向用户解读：
- 数据趋势和变化
- 异常值和关注点
- 维度拆解后的差异分析

如果数据适合可视化，结果中会包含 echarts 图表配置。

## 重要提示

1. **必须先检索再查询**：不要直接构造 metrics_query 请求，必须先通过 aloudata_search_semantic 获取精确的 metricName 和 dimName
2. **使用英文名构造请求**：metrics 和 dimensions 参数必须使用英文名(metricName/dimName)，不要使用中文展示名
3. **维度需确认可用**：使用维度前，先检查指标的 availableDimensions 列表
4. **时间维度特殊处理**：时间维度名通常为 `metric_time__*` 格式，在 dimensions 中指定时间粒度，在 timeConstraint 中指定时间范围
5. **同义词匹配**：用户可能使用非标准名称（如"营收"而非"销售额"），语义检索会通过同义词自动匹配
