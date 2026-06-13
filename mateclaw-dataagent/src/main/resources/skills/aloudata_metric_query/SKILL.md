---
name: aloudata_metric_query
version: "2.0.0"
description: "当用户需要查询指标平台的指标数据或维度信息时，使用 Aloudata 指标平台工具。适用于基于已定义的语义化指标和维度进行业务分析。"
dependencies:
  tools:
    - aloudata_metrics_list
    - aloudata_metrics_query
    - aloudata_dimensions_list
    - aloudata_search_semantic
---

# Aloudata 指标平台查询技能

当用户需要查询指标平台的指标数据时，按照以下工作流程操作。

> **注意**：Aloudata API 端点以动态 Tool 方式注册，Tool 名称格式为 `aloudata_{端点名}`。
> 完整可用 Tool 列表由 `aloudata.api.endpoints` 配置决定，以下为常用端点。

## 前提条件

使用此技能前，需要确保已配置 Aloudata 类型的数据源。如果用户未指定数据源，先询问用户使用哪个数据源。

> **关于 datasourceId**：所有 Aloudata Tool 都需要 `datasourceId` 参数，这是**本地系统中配置的指标平台数据源 ID**，用于定位连接配置和认证信息。它**不会**传递给远程 Aloudata API，Aloudata 平台不识别此参数。

## 工作流程

### 第一步：发现可用指标
调用 `aloudata_metrics_list(datasourceId=<id>)` 查看数据源下的所有指标。
- 每个指标包含 metricName、metricDisplayName、type、businessCaliber 等信息
- 根据用户问题推断最相关的指标；不确定时询问用户
- 注意指标的 businessCaliber（业务口径）描述，理解指标的含义
- 可选参数：keyword（搜索关键词）、statusFilters（状态过滤）、pageNumber/pageSize（分页）

### 第二步：了解可用维度
如果用户需要按维度分析：
- 调用 `aloudata_dimensions_list(datasourceId=<id>)` 查看所有维度
- 或调用 `aloudata_metric_available_dimensions(datasourceId=<id>, metricNames=[...])` 查看指定指标的可用维度
- 维度是指标的分组/切片条件，如时间、地区、产品类别等
- 根据用户需求选择合适的维度

### 第三步：查询指标数据
调用 `aloudata_metrics_query(datasourceId=<id>, metrics=[...], dimensions=[...])` 查询指标数据。
- metrics 为必填参数，传入指标名称列表
- dimensions 为可选参数，传入维度名称列表用于分组
- filters 为可选参数，传入过滤条件
- timeConstraint 为可选参数，传入时间范围约束
- limit 和 offset 用于分页控制
- queryResultType 控制返回类型：SQL_AND_DATA（默认）/ SQL / DATA

### 第四步：解读结果
- 用自然语言总结查询结果的要点
- 如果结果为空，分析可能的原因（如指标名称错误、过滤条件过严等）
- 如果查询结果包含数值列，系统会自动生成 ECharts 图表，无需手动生成图表代码
- **重要：不要使用 write_file 工具生成 HTML 图表文件，系统已内置图表渲染能力**

## 其他可用工具

- `aloudata_metric_detail` — 查询单个指标详情（需要 metricName）
- `aloudata_metric_batch_detail` — 批量查询指标详情（需要 metricNames）
- `aloudata_metric_tree` — 获取树状结构指标列表
- `aloudata_dimension_detail` — 查询维度详情（需要 dimName）
- `aloudata_dimension_values` — 预览维度取值（需要 dimName）
- `aloudata_attribution_tree` — 指标归因分析（需要 metricName）
- `aloudata_attribution_multi_dim` — 多维归因分析（需要 metricName + dimensions）
- `aloudata_attribution_validate` — 归因分析校验（需要 metricName）
- `aloudata_attribution_drilldown` — 归因下钻查询（需要 metricName + dimensions）
- `aloudata_search_semantic` — 搜索本地语义模型（需要 datasourceId + keyword）

## 使用场景

指标平台查询适用于以下场景：
- 用户提到"指标"、"度量"、"KPI"等关键词
- 用户需要基于已定义的业务指标进行分析
- 用户需要查看指标的维度拆解
- 用户需要了解指标的业务口径和定义

## 与数据集查询和 SQL 查询的区别

- **指标平台查询**：基于已定义的语义化指标和维度，业务语义明确，适合业务分析
- **数据集查询**：基于已治理的数据集，字段已分类，适合结构化数据探索
- **SQL 查询**：直接查询原始数据源表，灵活但需要了解表结构

如果用户的问题更适合直接查询原始表或数据集，建议使用对应的技能。

## 错误处理

如果查询失败：
1. 仔细阅读错误信息
2. 常见原因：数据源 ID 无效、数据源类型不是 aloudata、指标名称错误、认证失败
3. 如果提示"数据源类型不是 aloudata"，建议用户检查数据源配置
4. 如果认证失败，建议用户检查数据源的连接配置和认证信息
