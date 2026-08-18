---
name: aloudata_metric_query
version: "1.0.0"
description: "Aloudata 指标平台查询的唯一入口。凡涉及指标数值、趋势、对比、排名、占比、按维度拆解的问数请求，必须先 load_skill('aloudata_metric_query') 加载完整工作流，再按流程执行，禁止跳过流程直接调用 aloudata_metrics_query。硬约束：禁止凭猜测构造 metricName/dimName，必须先 aloudata_search_semantic 检索获得英文名；timeConstraint 仅支持 metric_time 维度且必须用 () 包裹；同环比须 metric_time 单值锚点。"
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

> **注意**：本文档（SKILL.md）是运行时唯一生效的流程规范，通过 load_skill / readSkillFile 加载，查询流程细节以本文档为准。仓库根目录 `prompts/数据分析助手.md` 是人工维护的同一套流程参考文档（侧重角色设定、系统不变量、图表与可视化规范），当两处存在差异时，**以本文档（SKILL.md）为准**。
>
> **流程总览**：解析意图 → ①追问判定(多轮优先) → ②意图确认(非追问才走) → ③术语标准化 → ④语义检索 → ⑤消歧(检索后) → ⑥确认维度 → ⑦构造查询 → ⑧查询前自检 → ⑨执行&结果自检 → ⑩解读&可视化

### 第一步：解析用户意图

分析用户问题，提取查询要素：指标（想看什么）、维度（按什么角度拆解）、时间范围、筛选条件、计算需求（同环比/占比/排名）、展示偏好。

### 第一点二五步：追问判定与增量复用（多轮场景，优先判断）

**解析意图后，立即判断本轮是否为"追问"**。若会话历史中存在上一轮**已成功执行**的 `aloudata_metrics_query`，且本轮仅在其基础上调整局部要素（省略指标主体、用指代词，或只改时间/计算/维度/筛选/图表），则判定为追问。

追问时**以上一轮成功请求为基座**，只修改变化的参数，未变化的 metricName/dimName **直接沿用、不重新检索**；仅当本轮引入基座中不存在的新指标/新维度时，才对该新实体执行第二、三步检索。复用平台已接受过的英文名不属于"猜测"。

**追问判定优先于意图确认**：判定为追问的轮次，要素从基座继承，自然明确，**跳过第一点五步意图完整性确认**，直接进入增量修改或对新实体的检索。首轮、历史无可提取的成功请求、或本轮更换了主指标时 → 退回下方完整流程（走第一点五步意图确认）。

#### 追问的判定信号

以下用户表述模式通常意味着追问，应优先从历史中提取基座请求：

| 追问类型 | 用户表述示例 | 基座修改方式 |
|---------|------------|------------|
| 追加同环比 | "相比上月增长多少""和去年比呢""环比呢" | metrics 中追加 `{指标名}__sameperiod__{偏移粒度}__{方法}` |
| 调整时间 | "3月呢""换成上月""看看Q1" | 修改 timeConstraint 表达式 |
| 追加维度拆解 | "按区域拆开""分渠道看看" | dimensions 中追加维度名 |
| 追加筛选 | "只看华东""排除线上" | filters 中追加筛选条件 |
| 追加排名/占比 | "排名呢""占比多少" | metrics 中追加 `{指标名}__rank` 或 `{指标名}__proportion__{维度}` |
| 调整粒度 | "按天看""换成月度" | dimensions 中替换日期维度粒度 |

#### 追问时的操作规则

1. **提取基座**：从对话历史中找到上一轮 `aloudata_metrics_query` 的**完整请求参数**（metrics、dimensions、timeConstraint、filters 等），作为本轮的起点
2. **增量修改**：仅修改用户追问涉及的参数，其余参数原样保留。例如用户追问"相比3月增长多少"：
   - metrics：保留原指标，追加 `{原指标名}__sameperiod__mom__growthvalue` 和 `{原指标名}__sameperiod__mom__growth`
   - timeConstraint：调整为3月范围
   - dimensions、filters：原样保留
3. **不重新检索已有实体**：基座中已验证的 metricName/dimName 直接沿用，**禁止对它们重新调用 aloudata_search_semantic**——这既浪费工具调用，又可能引入不一致的检索结果
4. **仅检索新增实体**：如果追问引入了基座中不存在的新指标或新维度，仅对该新实体执行第二、三步检索
5. **保持参数格式一致**：timeConstraint、filters 等表达式的语法格式必须与基座保持一致，不要切换到另一种写法

### 第一点五步：意图完整性确认（非追问的全新查询，检索前判断）

**未判定为追问的全新查询，进入术语标准化前，先判断用户问题本身的要素是否齐全**。问题不明确时，宁可先确认再检索，也不要带着模糊意图跑完术语标准化和语义检索后才追问——那会浪费工具调用，且追问依赖检索候选而非用户原始意图。

#### 必须确认的场景

1. **指标缺失或歧义**：用户未说清要看什么数据
   - "看看销售情况" → 金额？单量？客户数？请用户选择
   - "各区域表现" → 看哪个指标的表现？请用户明确
2. **时间范围缺失**：用户未指定时间，且无法从上下文/追问基座推断
   - "最近""近期""这段时间" → 请用户给出具体时间段或确认默认（如当月）
3. **维度拆解不清**：用户说"按区域""分维度"但未指明具体维度或层级
   - "按区域" → region/province/city 哪一层？请用户选择
4. **业务限定不清**：用户未指明业务范围，导致查询目标不唯一
   - "客户分析" → 新客/活跃/流失？请用户明确
   - "销售对比" → 对比的维度（渠道/区域/产品）未指明
5. **计算需求模糊**：用户表达了对比/计算意图但未指明具体方式
   - "做个对比" → 同比还是环比？和什么时期比？请用户明确
   - "增长" → 增长值（绝对值）还是增长率（百分比）？请用户明确

#### 不需要确认的场景

1. 指标明确且唯一（用户明确说了具体指标名，如"销售额""订单量"）
2. 时间范围明确（"上月""2025年1月""近7天"）或可从追问基座继承
3. 维度明确（"按区域"且上下文已明确层级）或无维度拆解需求（"累计/总计"）
4. 追问场景（第一点二五步已判定为追问，要素从基座继承，本步跳过）

#### 与第三点五步的区分

- 本步判断**问题本身没说清楚**（基于用户原始表述）
- 第三点五步判断**说清楚了但平台有多个候选**（基于检索结果）
- 两步不重复：本步通过后仍可能触发第三点五步（如指标说清楚了但检索出多个口径）

#### 追问原则

- **给出具体选项**，陈述句直接呈现，不要用疑问句质问（差："您想看什么？" → 中："您想看销售额还是营业收入？" → 好："可查看销售额或营业收入，请选择"）
- **列出候选项的展示名+口径描述**，让用户做知情选择
- **不要替用户做决定**，即使你"觉得"某个选项更可能，也必须确认
- **一次追问只问一个维度的问题**，不要同时问指标+时间+维度

### 第二步：理解业务术语（必执行）

当用户提问涉及业务术语、缩写或别名时，先通过 `search_business_term(keyword)` 查询术语的标准名称、定义和同义词。检索跨所有业务域进行，无需指定租户。

**为什么需要这一步**：
- 用户可能说"营收"，但指标平台的标准指标名是"销售额"
- 同义词映射帮助后续语义检索更精准
- 语义检索工具已内置术语扩展，但显式调用可获取更完整的术语定义

### 第三步：语义检索指标和维度

调用 `aloudata_search_semantic(datasourceId, keyword)` 检索相关的指标和维度。

**这一步是关键**：直接返回精确的 metricName 和 dimName，无需猜测。如果第二步查询了业务术语，可将术语名和同义词作为关键词传入以提高召回率。

**注意**：语义检索工具已内置关键词同义词扩展（将检索词的同义词作为 should 子句提升召回），这与第二步的 `search_business_term` 职责不同——前者是关键词级扩展（如"销售额"→还搜"销售收入"），后者是业务术语标准化（如"营收"→标准名"销售额"）。两者互补，不可互相替代：当用户使用业务术语/缩写/别名时，仍必须先调 `search_business_term` 获取标准名和定义，再用标准名作为关键词传入 `aloudata_search_semantic`。

### 第三点五步：消歧判断（必执行）

语义检索返回结果后，判断是否需要向用户确认。**宁可多问一句，不可错答一次**。

**本步仅处理检索结果的多义性**（指标命中多个、维度命中多个层级、检索返回消歧提示）。问题本身的要素不明确已在第一点二五步处理，本步不再重复判断。

#### 必须追问的场景

1. **检索结果包含消歧提示**：当 `aloudata_search_semantic` 返回中包含「⚠️ 消歧提示」时，**必须按提示向用户确认**，禁止自行选择
2. **指标多义**：检索到 ≥2 个指标，且无法从用户问题中确定唯一一个
3. **时间模糊**：用户未明确时间范围（如"最近""近期"），且无法从上下文推断
4. **维度多义**：用户说"按区域"但存在多个层级维度（如 region/province/city）
5. **计算需求模糊**：用户说"对比"但未明确是同比还是环比

#### 不需要追问的场景

1. 检索结果只有1个指标，或第1名分数远超第2名（差距 > 0.15）
2. 用户明确指定了时间范围（如"上月""2025年1月""近7天"）
3. 维度名称唯一匹配
4. 计算需求可从上下文唯一确定（如"去年和今年比"→同比）

#### 追问原则

- **给出具体选项**，不要问开放式问题，且选项以陈述句直接呈现，不要用疑问句质问用户（差："您想看什么？" → 中："您想看销售额还是营业收入？" → 好："可查看销售额或营业收入，请选择"）
- **列出候选项的展示名+口径描述**，让用户做知情选择
- **不要替用户做决定**，即使你"觉得"某个选项更可能，也必须确认
- **一次追问只问一个维度的问题**，不要同时问指标+时间+维度

### 第四步：确认可用维度

根据第三步返回的指标，检查其 `availableDimensions` 字段。如需确认更多维度，调用 `aloudata_metric_available_dimensions(metricNames)`。

**指标-维度配对（硬约束）**：
- 每个指标的 `availableDimensions` 是该指标**唯一合法**的维度来源。构造 `dimensions` 时只能从所选指标的可用维度中选取，**禁止混入其他指标的维度**或凭印象选维度。
- 查询多个指标时，`dimensions` 中每个维度必须**同时属于所有被查指标的可用维度集**（取交集），否则查询报错。
- 向用户展示候选维度时，必须标注每个维度属于哪个指标（如「销售额可用维度：区域/省份/城市」），**禁止脱离指标单独列维度让用户选**——这会导致用户选了不属于该指标的维度。
- 构造查询前自检：对 `dimensions` 中每个 dimName，确认它出现在所选指标的 `availableDimensions` 中。

### 第五步：构造查询请求

使用第三步得到的 metricName/dimName 构造 `aloudata_metrics_query` 请求。可参考 [templates/query-request-template.json](templates/query-request-template.json) 中的请求模板。

**基本参数**：
- `metrics`（必填）：指标英文名列表，如 `["sales_amount"]`。支持快速计算语法（同环比、占比、排名、时间限定）
- `dimensions`（选填）：维度英文名列表。**默认按指标日期查询**，即 `metric_time` 的某个粒度（支持 year/quarter/month/week/day/hour/minute，如 `metric_time__month`）；用户指定其他维度另说。详见 [references/api-doc.md](references/api-doc.md) 的 dimensions 详解
- `timeConstraint`（选填）：指标日期范围，**仅支持 `metric_time` 维度**（业务日期维度用 filters）。表达式语法，**必须用 `()` 包裹**，不支持 BETWEEN。如 `"(DateTrunc([metric_time],\"MONTH\")=DateTrunc(Today(),\"MONTH\"))"`。详见 [references/api-doc.md](references/api-doc.md) 的 timeConstraint 详解
- `filters`（选填）：全局筛选（含业务日期维度如 `dim_order_date`），对全部指标生效，如 `["[region] IN (\"华东\",\"华南\")"]`
- `resultFilters`（选填）：结果筛选，对查询结果进行二次过滤
- `metricDefinitions`（选填）：临时指标定义，用于 specifyDimension 等复杂衍生
- `orders`（选填）：排序，格式 `[{"字段名": "asc或desc"}]`。键为维度名或指标名，值为 asc 或 desc。如 `[{"sales_amount": "desc"}, {"region": "asc"}]`
  - **orders 常见错误（务必避免）**：必须传**对象数组** `[{"metric_time__day": "asc"}]`；禁止传字符串数组 `["{\"metric_time__day\": \"asc\"}"]` 或整体 JSON 字符串 `"[{...}]"`——这类写法会触发参数类型异常。即使工具已能自动归一化字符串写法，正确写法可避免额外重试。
- `limit`（选填）：返回条数，默认100
- `offset`（选填）：偏移量，默认1
- `queryResultType`（选填）：查询引擎默认 `SQL_AND_DATA`（同时返回数据和 SQL）；**本工具已强制覆盖为 `DATA`（仅返回数据），因为含 SQL 的返回数据量极大，会导致上下文溢出和文件截断。调试时如需查看 SQL，可显式传入 `SQL_AND_DATA`，但务必谨慎**
- `source`（选填）：查询来源标识
- `isQueryTotalCount`（选填）：是否返回数据总条数
- `specialMvConfig`（选填）：物化表加速配置

> 详细参数说明请参考 [references/api-doc.md](references/api-doc.md)
> 查询示例请参考 [references/best-practices.md](references/best-practices.md) 中的案例

### 第六步：输出自检

提交查询前，逐项确认以下检查项。也可使用 [scripts/validate_query_request.py](scripts/validate_query_request.py) 进行自动化校验：

1. 确认 metrics 使用 metricName（英文名），不是展示名
2. 确认 dimensions 使用 dimName（英文名），不是展示名
3. 确认 timeConstraint 格式正确（整个表达式用 `()` 包裹，使用方括号引用维度、双引号转义）
4. 确认使用的维度在指标的 availableDimensions 列表中
5. 确认同环比偏移粒度不小于日期粒度
6. 确认占比/排名的范围维度在 dimensions 中声明
7. 确认 filters 中的维度引用使用方括号，字符串值使用双引号
8. 确认 orders 中每个键（字段名）都包含在 metrics 或 dimensions 中
9. 确认 orders 每个元素是**对象**（`{"字段名": "asc|desc"}`），不是字符串或 JSON 字符串

### 第七步：解读结果

根据查询结果向用户解读数据趋势、异常值和维度拆解差异。

**结果处理工具**：
- 使用 [scripts/format_query_result.py](scripts/format_query_result.py) 将原始 JSON 响应格式化为 Markdown 表格，便于阅读
- 使用 [scripts/extract_data.py](scripts/extract_data.py) 将原始 JSON 响应提取为结构化数据，便于传入 `python_analysis` 工具进行深度分析
- 使用 [templates/report-template.md](templates/report-template.md) 生成标准化的指标分析报告

如需更复杂分析，将提取后的数据传给 `python_analysis` 工具。

### 查询失败处理与止损（硬性约束）

1. **相同错误连续出现 ≥2 次时，禁止继续变换参数重试**（如"去掉 orders 试试""换个时间范围试试"）。逐一试错会浪费大量工具调用且大概率无效；应停止查询，检查数据源查询通道（指标平台「指标应用 → API集成」中的服务地址、tenant-id、认证值）或查询引擎状态，无法确认时如实向用户反馈并等待指示。
2. **错误信息为 `API: ... 返回错误: null`**：说明远程 API 返回失败但未携带错误明细，参数格式问题可能性低，优先排查数据源通道/认证配置，不要盲目重试。
3. **收到「⚠️ 消歧提示」后禁止继续构造查询**：必须先向用户确认目标指标/维度，确认后才能继续。未确认前任何查询尝试都视为违规。
4. **追问基座必须来自上一轮"成功执行"的请求**：上一轮查询失败（含参数校验失败）时，其 metricName/dimName 不得作为基座沿用，须重新走第二、三步检索。

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

语法：`{指标名}__proportion__{占比范围维度}`，支持多维度逗号分隔如 `__proportion__region,province`

省略占比维度则为全局占比。占比维度必须在 dimensions 中声明。

### 排名

语法：`{指标名}__rank__{排名范围维度}`，支持多维度逗号分隔如 `__rank__region,province`

省略排名维度则为全局排名。排名维度必须在 dimensions 中声明。

### 时间限定

语法：`{指标名}__timefilter__{时间范围表达式}`

为指标添加时间限定，如：`sales_amount__timefilter__[metric_time__day]>=DateAdd(Today(),-7,\"DAY\")`

## 重要提示

1. **必须理解业务术语再检索**：当用户使用业务术语、缩写或别名时，必须先通过 search_business_term 确认含义
2. **必须先检索再查询（全新查询）**：全新查询不要直接构造 metrics_query 请求，必须先通过 aloudata_search_semantic 获取精确的 metricName 和 dimName。**追问例外**：本轮仅在上一轮成功请求基础上调整局部要素时，复用已验证的英文名，仅对新出现的实体检索（见第一点二五步）
3. **使用英文名构造请求**：metrics 和 dimensions 必须使用英文名，不要使用中文展示名（系统会自动校验并拦截中文展示名）
4. **同环比使用约束**：metric_time 必须在 dimensions 或 timeConstraint 中；timeConstraint 中须为单值筛选；偏移粒度不可小于日期粒度
5. **占比/排名维度约束**：使用的占比/排名范围维度必须在 dimensions 中声明
6. **意图不明确先确认再检索**：当用户问题在指标、维度、时间范围、业务限定上不明确时，必须先向用户确认（见第一点二五步），不要带着模糊意图直接检索
7. **消歧追问优先**：当检索结果包含「⚠️ 消歧提示」时，必须向用户确认后再构造查询，禁止自行选择（见第三点五步）
8. **与 Python 分析协作**：如需复杂处理，将数据传入 python_analysis 工具
9. **系统自动保障**：queryResultType 已强制为 DATA；timeConstraint 会自动规范化（补括号、BETWEEN转AND、补方括号）；查询请求会自动校验（中文展示名、同环比约束、维度可用性等）；orders 会自动归一化（字符串数组/JSON字符串自动转对象数组）
10. **失败止损**：相同错误连续出现 2 次以上即停止变换参数重试，优先排查数据源查询通道与认证配置（见"查询失败处理与止损"）
