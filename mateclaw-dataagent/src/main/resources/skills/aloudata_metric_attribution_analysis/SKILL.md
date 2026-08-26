---
name: aloudata_metric_attribution_analysis
version: "1.0.0"
description: "指标归因分析入口。当用户问'为什么下降/上升/变化''什么原因导致''归因'时，必须先 load_skill('aloudata_metric_attribution_analysis') 加载归因工作流。流程：检索指标英文名 → 归因校验 → LLM 自动选维度跑多维归因 → 对主因维度下钻 → 解读贡献率。硬约束：metric/dimensions 必须用英文名（先走 aloudata_search_semantic）；先 attribution_check 校验能否归因，再调归因查询；comparisonType 与时间粒度匹配；对比时间涉及相对表述（上月/去年/上周几等）必须先调 DateTimeTool.getCurrentDate 获取当前日期，禁止主观推算。"
dependencies:
  tools:
    - search_business_term
    - aloudata_search_semantic
    - aloudata_metric_available_dimensions
    - attribution_check
    - attribution_multi_dim
    - attribution_drilldown
    - attribution_tree
references:
  - ../aloudata_metric_query/references/api-doc.md
templates:
  - templates/attribution-report.md
---

# 指标归因分析技能

## 适用场景

用户想理解**指标为什么变化**——"销售额为什么下降""订单量上升的主要原因""和上月比哪个区域拉低的"。归因分析返回各维度对指标变化的**贡献率**，定位主因。

**归因 vs 查询的区别**：
- 指标查询（`aloudata_metric_query`）：查"指标是多少"
- 归因分析（本技能）：查"指标为什么变"——需要两个时间点的对比 + 维度贡献率分解

## 工作流程

> **流程总览**：解析归因意图 → 检索指标英文名 → 归因校验 → LLM 自动选维度 → 多维归因 → 下钻主因 → 解读贡献率

### 第一步：解析归因意图

判断用户问题是否为归因（而非纯查询）。归因信号：

- "为什么下降/上升/变化""什么原因导致""主要因素""归因""贡献""拉动/拖累"
- 隐含时间对比："相比上月""和去年比""环比下降的原因"

提取要素：
- **指标**：要归因的指标
- **对比基准**：和什么比（环比/同比/自定义区间）
- **时间粒度**：DAY/WEEK/MONTH/QUARTER/YEAR
- **候选维度**：用户指定的维度（若有）；若未指定，LLM 自动选（见第四步）

**与查询的边界**：若用户只问"上月销售额多少"（查数值）→ 走 `aloudata_metric_query`；若问"上月销售额为什么下降"（查原因）→ 走本技能。

**时间锚定（先取真实日期，禁止主观推算）**：对比基准涉及相对表述（"上月""去年""上周""近N天"等）时，必须**先调用内置工具 getCurrentDate（DateTimeTool.getCurrentDate）获取服务器当前日期与星期**，再以其返回值为唯一锚点换算 currentFilter/comparison 的时间表达式与 CUSTOM 区间；严禁凭主观印象推算今天的日期与星期。

### 第二步：检索指标英文名（必执行）

调用 `aloudata_search_semantic(datasourceId, keyword)` 获取指标的 metricName（英文名）。归因接口的 `metric` 参数必须是英文名，禁止用中文展示名。

若用户使用业务术语，先调 `search_business_term` 标准化。多口径指标（如"销售总金额（华北）"）按族级消歧确认唯一 metricName。

> 复用 `aloudata_metric_query` 的检索流程；若本会话已加载该 skill 且已检索过同一指标，直接复用 metricName，不重复检索。

### 第三步：归因校验（必执行）

调用 `attribution_check` 校验指标是否支持归因：

```
analysisRange:
  current: {type: EXPR, expr: "<当前时间表达式>"}
  comparison: {type: EXPR, expr: "<对比时间表达式>"}
metric: "<metricName>"
dimensions: ["<候选维度英文名列表>"]
```

- 返回 `true` → 可归因，继续
- 返回 `false` → 该指标/维度组合不支持归因，告知用户并终止（或换维度重试）

### 第四步：LLM 自动选维度

**用户未指定维度时，LLM 自动选择归因维度**。选维策略：

1. 调 `aloudata_metric_available_dimensions(metricNames)` 获取指标的可用维度
2. 从可用维度中选 **3-5 个业务相关维度**，优先级：
   - 业务主维度（区域/产品/渠道/客户类型等离散维度）
   - 排除时间维度（`metric_time*`，归因本身是时间对比）
   - 排除高基数维度（如订单号、客户ID，维度值过多无归因意义）
3. 选定的维度用于 `attribution_multi_dim`

**用户指定维度时**：直接用用户指定的维度，但必须确认在可用维度集内。

### 第五步：多维归因

调用 `attribution_multi_dim`：

```
attributionRange:
  granularity: "<DAY/MONTH/...>"
  comparisonType: "<MOM/YOY/CUSTOM/...>"
  currentFilter: {type: EXPR, expr: "<当前时间表达式>"}
  startDateTime/endDateTime: "<CUSTOM 时指定>"
metric: "<metricName>"
dimensions: ["<维度1>","<维度2>",...]
```

返回各维度的 `contributionRate`（贡献率）。

**comparisonType 与 granularity 匹配约束**：
- granularity=DAY → DOD/WOW 可用
- granularity=MONTH → MOM/QOQ 可用，DOD 不可用
- granularity=YEAR → YOY 可用
- 偏移粒度不可小于时间粒度（同 aloudata_metrics_query 同环比约束）

### 第六步：下钻主因维度（按需）

从多维归因结果中找出**贡献率绝对值最大的维度**（主因维度），调 `attribution_drilldown` 下钻：

```
attributionRange: <同多维归因>
metric: "<metricName>"
dimension: "<主因维度单数>"   // 注意是单数 dimension，不是 dimensions
```

下钻返回该维度各维度值的贡献率，定位"具体是哪个维度值（如华东区域）拉低了指标"。

### 第七步：解读贡献率

贡献率解读规则：

- `contributionRate > 0`：该维度值推动指标同向变化（指标涨，该维度值也涨）
- `contributionRate < 0`：该维度值反向变化（指标涨但该维度值跌，或反之）
- `|contributionRate|` 越大，贡献越大
- `all.growth` 是整体变化，各维度 contributionRate 之和应接近整体

**输出要求**：
- 按贡献率绝对值排序，列出 Top 3 主因
- 区分"正向拉动"和"反向拖累"
- 给出可读结论（如"销售额下降主要因华东区域贡献率 -0.6，下拉 60%"）

## 指标树归因（可选路径）

当用户问"总销售额 = 销售额 + 退货额，各因子贡献多少"这类**因子拆解**归因时，调用 `attribution_tree`，直接传入指标拆解树：

**attribution_tree 请求结构**（官方文档）：
- `metricTree`：指标树层级关系，key 为节点名（如 `RootNode`/`MetricA`），value 含 `expr`（拆解表达式，根节点为子节点相加公式，叶子节点为空）和 `refId`（引用 metrics 中的指标 ID）
- `metrics`：指标定义详情，key 为指标 ID（与 metricTree 的 refId 对应），value 含 `id` 和 `code`（指标英文名）
- `attributionRange`：归因范围，结构同多维归因（granularity/comparisonType/currentFilter）
- `filters`：筛选器

> `metricTree` 和 `metrics` 结构需要由调用方构造（基于指标的拆解关系）。官方文档未提供独立的"指标拆解查询"接口，若平台已配置指标树，可从指标元数据获取拆解关系。

指标树归因用于复合指标的因子分解，与多维归因（维度分解）是互补的两条路径。详见 [api-doc.md 的 attribution_tree 章节](../aloudata_metric_query/references/api-doc.md#attribution_tree--指标树归因)。

## 重要提示

1. **必须先检索再归因**：metric/dimensions 必须用英文名，先走 `aloudata_search_semantic`
2. **必须先校验再归因**：`attribution_check` 返回 true 才继续，避免无效查询
3. **comparisonType 与粒度匹配**：偏移粒度不可小于时间粒度
4. **下钻用 dimension 单数**：`attribution_drilldown` 的参数是 `dimension`（单数），不是 `dimensions`
5. **贡献率正负含义**：正值同向推动，负值反向拖累，绝对值大小代表贡献程度
6. **与查询 skill 协作**：归因前若需查看指标当前值，可先走 `aloudata_metric_query`；归因 skill 聚焦"为什么变"
7. **相对时间先取真实日期（硬性）**：归因的当前/对比时间表达式涉及相对表述（"上月/去年/上周几"等）时，必须先调用内置工具 getCurrentDate 获取服务器当前日期与星期，以其为唯一锚点换算；禁止主观推算当前日期（见第一步"时间锚定"）

详细参数说明参考 [aloudata_metric_query/references/api-doc.md](../aloudata_metric_query/references/api-doc.md) 的"指标归因分析 API"章节。
